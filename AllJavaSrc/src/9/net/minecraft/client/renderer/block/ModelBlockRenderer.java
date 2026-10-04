package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import java.util.List;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

public class ModelBlockRenderer {
   private static final Direction[] DIRECTIONS = Direction.values();
   private final BlockColors blockColors;
   private static final int CACHE_SIZE = 100;
   private static final ThreadLocal<ModelBlockRenderer.Cache> CACHE = ThreadLocal.withInitial(ModelBlockRenderer.Cache::new);

   public ModelBlockRenderer(final BlockColors blockColors) {
      this.blockColors = blockColors;
   }

   public void tesselateBlock(
      final BlockAndTintGetter level,
      final List<BlockModelPart> parts,
      final BlockState blockState,
      final BlockPos pos,
      final PoseStack poseStack,
      final VertexConsumer builder,
      final boolean cull,
      final int overlayCoords
   ) {
      if (!parts.isEmpty()) {
         boolean useAO = Minecraft.useAmbientOcclusion() && blockState.getLightEmission() == 0 && parts.getFirst().useAmbientOcclusion();
         poseStack.translate(blockState.getOffset(pos));

         try {
            if (useAO) {
               this.tesselateWithAO(level, parts, blockState, pos, poseStack, builder, cull, overlayCoords);
            } else {
               this.tesselateWithoutAO(level, parts, blockState, pos, poseStack, builder, cull, overlayCoords);
            }
         } catch (Throwable t) {
            CrashReport report = CrashReport.forThrowable(t, "Tesselating block model");
            CrashReportCategory category = report.addCategory("Block model being tesselated");
            CrashReportCategory.populateBlockDetails(category, level, pos, blockState);
            category.setDetail("Using AO", useAO);
            throw new ReportedException(report);
         }
      }
   }

   private static boolean shouldRenderFace(
      final BlockAndTintGetter level, final BlockState state, final boolean cullEnabled, final Direction direction, final BlockPos neighborPos
   ) {
      if (!cullEnabled) {
         return true;
      }

      BlockState neighborState = level.getBlockState(neighborPos);
      return Block.shouldRenderFace(state, neighborState, direction);
   }

   public void tesselateWithAO(
      final BlockAndTintGetter level,
      final List<BlockModelPart> parts,
      final BlockState state,
      final BlockPos pos,
      final PoseStack poseStack,
      final VertexConsumer builder,
      final boolean cull,
      final int overlayCoords
   ) {
      ModelBlockRenderer.AmbientOcclusionRenderStorage scratch = new ModelBlockRenderer.AmbientOcclusionRenderStorage();
      int cacheValid = 0;
      int shouldRenderFaceCache = 0;

      for (BlockModelPart part : parts) {
         for (Direction direction : DIRECTIONS) {
            int cacheMask = 1 << direction.ordinal();
            boolean validCacheForDirection = (cacheValid & cacheMask) == 1;
            boolean shouldRenderFace = (shouldRenderFaceCache & cacheMask) == 1;
            if (!validCacheForDirection || shouldRenderFace) {
               List<BakedQuad> culledQuads = part.getQuads(direction);
               if (!culledQuads.isEmpty()) {
                  if (!validCacheForDirection) {
                     shouldRenderFace = shouldRenderFace(level, state, cull, direction, scratch.scratchPos.setWithOffset(pos, direction));
                     cacheValid |= cacheMask;
                     if (shouldRenderFace) {
                        shouldRenderFaceCache |= cacheMask;
                     }
                  }

                  if (shouldRenderFace) {
                     this.renderModelFaceAO(level, state, pos, poseStack, builder, culledQuads, scratch, overlayCoords);
                  }
               }
            }
         }

         List<BakedQuad> unculledQuads = part.getQuads(null);
         if (!unculledQuads.isEmpty()) {
            this.renderModelFaceAO(level, state, pos, poseStack, builder, unculledQuads, scratch, overlayCoords);
         }
      }
   }

   public void tesselateWithoutAO(
      final BlockAndTintGetter level,
      final List<BlockModelPart> parts,
      final BlockState state,
      final BlockPos pos,
      final PoseStack poseStack,
      final VertexConsumer builder,
      final boolean cull,
      final int overlayCoords
   ) {
      ModelBlockRenderer.CommonRenderStorage scratch = new ModelBlockRenderer.CommonRenderStorage();
      int cacheValid = 0;
      int shouldRenderFaceCache = 0;

      for (BlockModelPart part : parts) {
         for (Direction direction : DIRECTIONS) {
            int cacheMask = 1 << direction.ordinal();
            boolean validCacheForDirection = (cacheValid & cacheMask) == 1;
            boolean shouldRenderFace = (shouldRenderFaceCache & cacheMask) == 1;
            if (!validCacheForDirection || shouldRenderFace) {
               List<BakedQuad> culledQuads = part.getQuads(direction);
               if (!culledQuads.isEmpty()) {
                  BlockPos relativePos = scratch.scratchPos.setWithOffset(pos, direction);
                  if (!validCacheForDirection) {
                     shouldRenderFace = shouldRenderFace(level, state, cull, direction, relativePos);
                     cacheValid |= cacheMask;
                     if (shouldRenderFace) {
                        shouldRenderFaceCache |= cacheMask;
                     }
                  }

                  if (shouldRenderFace) {
                     int lightColor = scratch.cache.getLightColor(state, level, relativePos);
                     this.renderModelFaceFlat(level, state, pos, lightColor, overlayCoords, false, poseStack, builder, culledQuads, scratch);
                  }
               }
            }
         }

         List<BakedQuad> unculledQuads = part.getQuads(null);
         if (!unculledQuads.isEmpty()) {
            this.renderModelFaceFlat(level, state, pos, -1, overlayCoords, true, poseStack, builder, unculledQuads, scratch);
         }
      }
   }

   private void renderModelFaceAO(
      final BlockAndTintGetter level,
      final BlockState state,
      final BlockPos pos,
      final PoseStack poseStack,
      final VertexConsumer builder,
      final List<BakedQuad> quads,
      final ModelBlockRenderer.AmbientOcclusionRenderStorage storage,
      final int overlayCoords
   ) {
      for (BakedQuad quad : quads) {
         calculateShape(level, state, pos, quad, storage);
         storage.calculate(level, state, pos, quad.direction(), quad.shade());
         this.putQuadData(level, state, pos, builder, poseStack.last(), quad, storage, overlayCoords);
      }
   }

   private void putQuadData(
      final BlockAndTintGetter level,
      final BlockState state,
      final BlockPos pos,
      final VertexConsumer builder,
      final Pose pose,
      final BakedQuad quad,
      final ModelBlockRenderer.CommonRenderStorage renderStorage,
      final int overlayCoords
   ) {
      int tintIndex = quad.tintIndex();
      float r;
      float g;
      float b;
      if (tintIndex != -1) {
         int tintColor;
         if (renderStorage.tintCacheIndex == tintIndex) {
            tintColor = renderStorage.tintCacheValue;
         } else {
            tintColor = this.blockColors.getColor(state, level, pos, tintIndex);
            renderStorage.tintCacheIndex = tintIndex;
            renderStorage.tintCacheValue = tintColor;
         }

         r = ARGB.redFloat(tintColor);
         g = ARGB.greenFloat(tintColor);
         b = ARGB.blueFloat(tintColor);
      } else {
         r = 1.0F;
         g = 1.0F;
         b = 1.0F;
      }

      builder.putBulkData(pose, quad, renderStorage.brightness, r, g, b, 1.0F, renderStorage.lightmap, overlayCoords);
   }

   private static void calculateShape(
      final BlockAndTintGetter level, final BlockState state, final BlockPos pos, final BakedQuad quad, final ModelBlockRenderer.CommonRenderStorage storage
   ) {
      float minX = 32.0F;
      float minY = 32.0F;
      float minZ = 32.0F;
      float maxX = -32.0F;
      float maxY = -32.0F;
      float maxZ = -32.0F;

      for (int i = 0; i < 4; i++) {
         Vector3fc position = quad.position(i);
         float x = position.x();
         float y = position.y();
         float z = position.z();
         minX = Math.min(minX, x);
         minY = Math.min(minY, y);
         minZ = Math.min(minZ, z);
         maxX = Math.max(maxX, x);
         maxY = Math.max(maxY, y);
         maxZ = Math.max(maxZ, z);
      }

      if (storage instanceof ModelBlockRenderer.AmbientOcclusionRenderStorage aoStorage) {
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.WEST.index] = minX;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.EAST.index] = maxX;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.DOWN.index] = minY;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.UP.index] = maxY;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.NORTH.index] = minZ;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.SOUTH.index] = maxZ;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_WEST.index] = 1.0F - minX;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_EAST.index] = 1.0F - maxX;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_DOWN.index] = 1.0F - minY;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_UP.index] = 1.0F - maxY;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_NORTH.index] = 1.0F - minZ;
         aoStorage.faceShape[ModelBlockRenderer.SizeInfo.FLIP_SOUTH.index] = 1.0F - maxZ;
      }

      float minEpsilon = 1.0E-4F;
      float maxEpsilon = 0.9999F;

      storage.facePartial = switch (quad.direction()) {
         case DOWN, UP -> minX >= 1.0E-4F || minZ >= 1.0E-4F || maxX <= 0.9999F || maxZ <= 0.9999F;
         case NORTH, SOUTH -> minX >= 1.0E-4F || minY >= 1.0E-4F || maxX <= 0.9999F || maxY <= 0.9999F;
         case WEST, EAST -> minY >= 1.0E-4F || minZ >= 1.0E-4F || maxY <= 0.9999F || maxZ <= 0.9999F;
      };

      storage.faceCubic = switch (quad.direction()) {
         case DOWN -> minY == maxY && (minY < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos));
         case UP -> minY == maxY && (maxY > 0.9999F || state.isCollisionShapeFullBlock(level, pos));
         case NORTH -> minZ == maxZ && (minZ < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos));
         case SOUTH -> minZ == maxZ && (maxZ > 0.9999F || state.isCollisionShapeFullBlock(level, pos));
         case WEST -> minX == maxX && (minX < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos));
         case EAST -> minX == maxX && (maxX > 0.9999F || state.isCollisionShapeFullBlock(level, pos));
      };
   }

   private void renderModelFaceFlat(
      final BlockAndTintGetter level,
      final BlockState state,
      final BlockPos pos,
      int lightColor,
      final int overlayCoords,
      final boolean checkLight,
      final PoseStack poseStack,
      final VertexConsumer builder,
      final List<BakedQuad> quads,
      final ModelBlockRenderer.CommonRenderStorage shapeState
   ) {
      for (BakedQuad quad : quads) {
         if (checkLight) {
            calculateShape(level, state, pos, quad, shapeState);
            BlockPos lightPos = shapeState.faceCubic ? shapeState.scratchPos.setWithOffset(pos, quad.direction()) : pos;
            lightColor = shapeState.cache.getLightColor(state, level, lightPos);
         }

         float directionalBrightness = level.getShade(quad.direction(), quad.shade());
         shapeState.brightness[0] = directionalBrightness;
         shapeState.brightness[1] = directionalBrightness;
         shapeState.brightness[2] = directionalBrightness;
         shapeState.brightness[3] = directionalBrightness;
         shapeState.lightmap[0] = lightColor;
         shapeState.lightmap[1] = lightColor;
         shapeState.lightmap[2] = lightColor;
         shapeState.lightmap[3] = lightColor;
         this.putQuadData(level, state, pos, builder, poseStack.last(), quad, shapeState, overlayCoords);
      }
   }

   public static void renderModel(
      final Pose pose,
      final VertexConsumer builder,
      final BlockStateModel model,
      final float r,
      final float g,
      final float b,
      final int lightCoords,
      final int overlayCoords
   ) {
      for (BlockModelPart part : model.collectParts(RandomSource.create(42L))) {
         for (Direction direction : DIRECTIONS) {
            renderQuadList(pose, builder, r, g, b, part.getQuads(direction), lightCoords, overlayCoords);
         }

         renderQuadList(pose, builder, r, g, b, part.getQuads(null), lightCoords, overlayCoords);
      }
   }

   private static void renderQuadList(
      final Pose pose,
      final VertexConsumer builder,
      final float r,
      final float g,
      final float b,
      final List<BakedQuad> quads,
      final int lightCoords,
      final int overlayCoords
   ) {
      for (BakedQuad quad : quads) {
         float red;
         float green;
         float blue;
         if (quad.isTinted()) {
            red = Mth.clamp(r, 0.0F, 1.0F);
            green = Mth.clamp(g, 0.0F, 1.0F);
            blue = Mth.clamp(b, 0.0F, 1.0F);
         } else {
            red = 1.0F;
            green = 1.0F;
            blue = 1.0F;
         }

         builder.putBulkData(pose, quad, red, green, blue, 1.0F, lightCoords, overlayCoords);
      }
   }

   public static void enableCaching() {
      CACHE.get().enable();
   }

   public static void clearCache() {
      CACHE.get().disable();
   }

   protected enum AdjacencyInfo {
      DOWN(
         new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},
         0.5F,
         true,
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.SOUTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.NORTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.NORTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.SOUTH
         }
      ),
      UP(
         new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH},
         1.0F,
         true,
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.SOUTH,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.SOUTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.NORTH,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.NORTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.NORTH,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.NORTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.SOUTH,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.SOUTH
         }
      ),
      NORTH(
         new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST},
         0.8F,
         true,
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.FLIP_WEST
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.FLIP_EAST
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_EAST
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_WEST
         }
      ),
      SOUTH(
         new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP},
         0.8F,
         true,
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.WEST
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_WEST,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.WEST,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.WEST
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.EAST
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.FLIP_EAST,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.EAST,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.EAST
         }
      ),
      WEST(
         new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH},
         0.6F,
         true,
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.SOUTH,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.SOUTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.NORTH,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.NORTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.NORTH,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.NORTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.SOUTH,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.SOUTH
         }
      ),
      EAST(
         new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH},
         0.6F,
         true,
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.SOUTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.DOWN,
            ModelBlockRenderer.SizeInfo.NORTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.NORTH,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.FLIP_NORTH,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.NORTH
         },
         new ModelBlockRenderer.SizeInfo[]{
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.SOUTH,
            ModelBlockRenderer.SizeInfo.FLIP_UP,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
            ModelBlockRenderer.SizeInfo.UP,
            ModelBlockRenderer.SizeInfo.SOUTH
         }
      );

      private final Direction[] corners;
      private final boolean doNonCubicWeight;
      private final ModelBlockRenderer.SizeInfo[] vert0Weights;
      private final ModelBlockRenderer.SizeInfo[] vert1Weights;
      private final ModelBlockRenderer.SizeInfo[] vert2Weights;
      private final ModelBlockRenderer.SizeInfo[] vert3Weights;
      private static final ModelBlockRenderer.AdjacencyInfo[] BY_FACING = Util.make(new ModelBlockRenderer.AdjacencyInfo[6], map -> {
         map[Direction.DOWN.get3DDataValue()] = DOWN;
         map[Direction.UP.get3DDataValue()] = UP;
         map[Direction.NORTH.get3DDataValue()] = NORTH;
         map[Direction.SOUTH.get3DDataValue()] = SOUTH;
         map[Direction.WEST.get3DDataValue()] = WEST;
         map[Direction.EAST.get3DDataValue()] = EAST;
      });

      AdjacencyInfo(
         final Direction[] corners,
         final float shadeWeight,
         final boolean doNonCubicWeight,
         final ModelBlockRenderer.SizeInfo[] vert0Weights,
         final ModelBlockRenderer.SizeInfo[] vert1Weights,
         final ModelBlockRenderer.SizeInfo[] vert2Weights,
         final ModelBlockRenderer.SizeInfo[] vert3Weights
      ) {
         this.corners = corners;
         this.doNonCubicWeight = doNonCubicWeight;
         this.vert0Weights = vert0Weights;
         this.vert1Weights = vert1Weights;
         this.vert2Weights = vert2Weights;
         this.vert3Weights = vert3Weights;
      }

      public static ModelBlockRenderer.AdjacencyInfo fromFacing(final Direction direction) {
         return BY_FACING[direction.get3DDataValue()];
      }
   }

   private static class AmbientOcclusionRenderStorage extends ModelBlockRenderer.CommonRenderStorage {
      private final float[] faceShape = new float[ModelBlockRenderer.SizeInfo.COUNT];

      public AmbientOcclusionRenderStorage() {
      }

      public void calculate(
         final BlockAndTintGetter level, final BlockState state, final BlockPos centerPosition, final Direction direction, final boolean shade
      ) {
         BlockPos basePosition = this.faceCubic ? centerPosition.relative(direction) : centerPosition;
         ModelBlockRenderer.AdjacencyInfo info = ModelBlockRenderer.AdjacencyInfo.fromFacing(direction);
         BlockPos.MutableBlockPos pos = this.scratchPos;
         pos.setWithOffset(basePosition, info.corners[0]);
         BlockState state0 = level.getBlockState(pos);
         int light0 = this.cache.getLightColor(state0, level, pos);
         float shade0 = this.cache.getShadeBrightness(state0, level, pos);
         pos.setWithOffset(basePosition, info.corners[1]);
         BlockState state1 = level.getBlockState(pos);
         int light1 = this.cache.getLightColor(state1, level, pos);
         float shade1 = this.cache.getShadeBrightness(state1, level, pos);
         pos.setWithOffset(basePosition, info.corners[2]);
         BlockState state2 = level.getBlockState(pos);
         int light2 = this.cache.getLightColor(state2, level, pos);
         float shade2 = this.cache.getShadeBrightness(state2, level, pos);
         pos.setWithOffset(basePosition, info.corners[3]);
         BlockState state3 = level.getBlockState(pos);
         int light3 = this.cache.getLightColor(state3, level, pos);
         float shade3 = this.cache.getShadeBrightness(state3, level, pos);
         BlockState corner0 = level.getBlockState(pos.setWithOffset(basePosition, info.corners[0]).move(direction));
         boolean translucent0 = !corner0.isViewBlocking(level, pos) || corner0.getLightBlock() == 0;
         BlockState corner1 = level.getBlockState(pos.setWithOffset(basePosition, info.corners[1]).move(direction));
         boolean translucent1 = !corner1.isViewBlocking(level, pos) || corner1.getLightBlock() == 0;
         BlockState corner2 = level.getBlockState(pos.setWithOffset(basePosition, info.corners[2]).move(direction));
         boolean translucent2 = !corner2.isViewBlocking(level, pos) || corner2.getLightBlock() == 0;
         BlockState corner3 = level.getBlockState(pos.setWithOffset(basePosition, info.corners[3]).move(direction));
         boolean translucent3 = !corner3.isViewBlocking(level, pos) || corner3.getLightBlock() == 0;
         float shadeCorner02;
         int lightCorner02;
         if (!translucent2 && !translucent0) {
            shadeCorner02 = shade0;
            lightCorner02 = light0;
         } else {
            pos.setWithOffset(basePosition, info.corners[0]).move(info.corners[2]);
            BlockState state02 = level.getBlockState(pos);
            shadeCorner02 = this.cache.getShadeBrightness(state02, level, pos);
            lightCorner02 = this.cache.getLightColor(state02, level, pos);
         }

         float shadeCorner03;
         int lightCorner03;
         if (!translucent3 && !translucent0) {
            shadeCorner03 = shade0;
            lightCorner03 = light0;
         } else {
            pos.setWithOffset(basePosition, info.corners[0]).move(info.corners[3]);
            BlockState state03 = level.getBlockState(pos);
            shadeCorner03 = this.cache.getShadeBrightness(state03, level, pos);
            lightCorner03 = this.cache.getLightColor(state03, level, pos);
         }

         float shadeCorner12;
         int lightCorner12;
         if (!translucent2 && !translucent1) {
            shadeCorner12 = shade0;
            lightCorner12 = light0;
         } else {
            pos.setWithOffset(basePosition, info.corners[1]).move(info.corners[2]);
            BlockState state12 = level.getBlockState(pos);
            shadeCorner12 = this.cache.getShadeBrightness(state12, level, pos);
            lightCorner12 = this.cache.getLightColor(state12, level, pos);
         }

         float shadeCorner13;
         int lightCorner13;
         if (!translucent3 && !translucent1) {
            shadeCorner13 = shade0;
            lightCorner13 = light0;
         } else {
            pos.setWithOffset(basePosition, info.corners[1]).move(info.corners[3]);
            BlockState state13 = level.getBlockState(pos);
            shadeCorner13 = this.cache.getShadeBrightness(state13, level, pos);
            lightCorner13 = this.cache.getLightColor(state13, level, pos);
         }

         int lightCenter = this.cache.getLightColor(state, level, centerPosition);
         pos.setWithOffset(centerPosition, direction);
         BlockState nextState = level.getBlockState(pos);
         if (this.faceCubic || !nextState.isSolidRender()) {
            lightCenter = this.cache.getLightColor(nextState, level, pos);
         }

         float shadeCenter = this.faceCubic
            ? this.cache.getShadeBrightness(level.getBlockState(basePosition), level, basePosition)
            : this.cache.getShadeBrightness(level.getBlockState(centerPosition), level, centerPosition);
         ModelBlockRenderer.AmbientVertexRemap remap = ModelBlockRenderer.AmbientVertexRemap.fromFacing(direction);
         if (this.facePartial && info.doNonCubicWeight) {
            float tempShade1 = (shade3 + shade0 + shadeCorner03 + shadeCenter) * 0.25F;
            float tempShade2 = (shade2 + shade0 + shadeCorner02 + shadeCenter) * 0.25F;
            float tempShade3 = (shade2 + shade1 + shadeCorner12 + shadeCenter) * 0.25F;
            float tempShade4 = (shade3 + shade1 + shadeCorner13 + shadeCenter) * 0.25F;
            float vert0weight01 = this.faceShape[info.vert0Weights[0].index] * this.faceShape[info.vert0Weights[1].index];
            float vert0weight23 = this.faceShape[info.vert0Weights[2].index] * this.faceShape[info.vert0Weights[3].index];
            float vert0weight45 = this.faceShape[info.vert0Weights[4].index] * this.faceShape[info.vert0Weights[5].index];
            float vert0weight67 = this.faceShape[info.vert0Weights[6].index] * this.faceShape[info.vert0Weights[7].index];
            float vert1weight01 = this.faceShape[info.vert1Weights[0].index] * this.faceShape[info.vert1Weights[1].index];
            float vert1weight23 = this.faceShape[info.vert1Weights[2].index] * this.faceShape[info.vert1Weights[3].index];
            float vert1weight45 = this.faceShape[info.vert1Weights[4].index] * this.faceShape[info.vert1Weights[5].index];
            float vert1weight67 = this.faceShape[info.vert1Weights[6].index] * this.faceShape[info.vert1Weights[7].index];
            float vert2weight01 = this.faceShape[info.vert2Weights[0].index] * this.faceShape[info.vert2Weights[1].index];
            float vert2weight23 = this.faceShape[info.vert2Weights[2].index] * this.faceShape[info.vert2Weights[3].index];
            float vert2weight45 = this.faceShape[info.vert2Weights[4].index] * this.faceShape[info.vert2Weights[5].index];
            float vert2weight67 = this.faceShape[info.vert2Weights[6].index] * this.faceShape[info.vert2Weights[7].index];
            float vert3weight01 = this.faceShape[info.vert3Weights[0].index] * this.faceShape[info.vert3Weights[1].index];
            float vert3weight23 = this.faceShape[info.vert3Weights[2].index] * this.faceShape[info.vert3Weights[3].index];
            float vert3weight45 = this.faceShape[info.vert3Weights[4].index] * this.faceShape[info.vert3Weights[5].index];
            float vert3weight67 = this.faceShape[info.vert3Weights[6].index] * this.faceShape[info.vert3Weights[7].index];
            this.brightness[remap.vert0] = Math.clamp(
               tempShade1 * vert0weight01 + tempShade2 * vert0weight23 + tempShade3 * vert0weight45 + tempShade4 * vert0weight67, 0.0F, 1.0F
            );
            this.brightness[remap.vert1] = Math.clamp(
               tempShade1 * vert1weight01 + tempShade2 * vert1weight23 + tempShade3 * vert1weight45 + tempShade4 * vert1weight67, 0.0F, 1.0F
            );
            this.brightness[remap.vert2] = Math.clamp(
               tempShade1 * vert2weight01 + tempShade2 * vert2weight23 + tempShade3 * vert2weight45 + tempShade4 * vert2weight67, 0.0F, 1.0F
            );
            this.brightness[remap.vert3] = Math.clamp(
               tempShade1 * vert3weight01 + tempShade2 * vert3weight23 + tempShade3 * vert3weight45 + tempShade4 * vert3weight67, 0.0F, 1.0F
            );
            int _tc1 = blend(light3, light0, lightCorner03, lightCenter);
            int _tc2 = blend(light2, light0, lightCorner02, lightCenter);
            int _tc3 = blend(light2, light1, lightCorner12, lightCenter);
            int _tc4 = blend(light3, light1, lightCorner13, lightCenter);
            this.lightmap[remap.vert0] = blend(_tc1, _tc2, _tc3, _tc4, vert0weight01, vert0weight23, vert0weight45, vert0weight67);
            this.lightmap[remap.vert1] = blend(_tc1, _tc2, _tc3, _tc4, vert1weight01, vert1weight23, vert1weight45, vert1weight67);
            this.lightmap[remap.vert2] = blend(_tc1, _tc2, _tc3, _tc4, vert2weight01, vert2weight23, vert2weight45, vert2weight67);
            this.lightmap[remap.vert3] = blend(_tc1, _tc2, _tc3, _tc4, vert3weight01, vert3weight23, vert3weight45, vert3weight67);
         } else {
            float lightLevel1 = (shade3 + shade0 + shadeCorner03 + shadeCenter) * 0.25F;
            float lightLevel2 = (shade2 + shade0 + shadeCorner02 + shadeCenter) * 0.25F;
            float lightLevel3 = (shade2 + shade1 + shadeCorner12 + shadeCenter) * 0.25F;
            float lightLevel4 = (shade3 + shade1 + shadeCorner13 + shadeCenter) * 0.25F;
            this.lightmap[remap.vert0] = blend(light3, light0, lightCorner03, lightCenter);
            this.lightmap[remap.vert1] = blend(light2, light0, lightCorner02, lightCenter);
            this.lightmap[remap.vert2] = blend(light2, light1, lightCorner12, lightCenter);
            this.lightmap[remap.vert3] = blend(light3, light1, lightCorner13, lightCenter);
            this.brightness[remap.vert0] = lightLevel1;
            this.brightness[remap.vert1] = lightLevel2;
            this.brightness[remap.vert2] = lightLevel3;
            this.brightness[remap.vert3] = lightLevel4;
         }

         float directionalBrightness = level.getShade(direction, shade);

         for (int i = 0; i < this.brightness.length; i++) {
            this.brightness[i] = this.brightness[i] * directionalBrightness;
         }
      }

      private static int blend(int a, int b, int c, final int def) {
         if (a == 0) {
            a = def;
         }

         if (b == 0) {
            b = def;
         }

         if (c == 0) {
            c = def;
         }

         return a + b + c + def >> 2 & 16711935;
      }

      private static int blend(final int a, final int b, final int c, final int d, final float fa, final float fb, final float fc, final float fd) {
         int top = (int)((a >> 16 & 0xFF) * fa + (b >> 16 & 0xFF) * fb + (c >> 16 & 0xFF) * fc + (d >> 16 & 0xFF) * fd) & 0xFF;
         int bottom = (int)((a & 0xFF) * fa + (b & 0xFF) * fb + (c & 0xFF) * fc + (d & 0xFF) * fd) & 0xFF;
         return top << 16 | bottom;
      }
   }

   private enum AmbientVertexRemap {
      DOWN(0, 1, 2, 3),
      UP(2, 3, 0, 1),
      NORTH(3, 0, 1, 2),
      SOUTH(0, 1, 2, 3),
      WEST(3, 0, 1, 2),
      EAST(1, 2, 3, 0);

      private final int vert0;
      private final int vert1;
      private final int vert2;
      private final int vert3;
      private static final ModelBlockRenderer.AmbientVertexRemap[] BY_FACING = Util.make(new ModelBlockRenderer.AmbientVertexRemap[6], map -> {
         map[Direction.DOWN.get3DDataValue()] = DOWN;
         map[Direction.UP.get3DDataValue()] = UP;
         map[Direction.NORTH.get3DDataValue()] = NORTH;
         map[Direction.SOUTH.get3DDataValue()] = SOUTH;
         map[Direction.WEST.get3DDataValue()] = WEST;
         map[Direction.EAST.get3DDataValue()] = EAST;
      });

      AmbientVertexRemap(final int vert0, final int vert1, final int vert2, final int vert3) {
         this.vert0 = vert0;
         this.vert1 = vert1;
         this.vert2 = vert2;
         this.vert3 = vert3;
      }

      public static ModelBlockRenderer.AmbientVertexRemap fromFacing(final Direction direction) {
         return BY_FACING[direction.get3DDataValue()];
      }
   }

   private static class Cache {
      private boolean enabled;
      private final Long2IntLinkedOpenHashMap colorCache = Util.make(() -> {
         Long2IntLinkedOpenHashMap map = new Long2IntLinkedOpenHashMap(100, 0.25F) {
            protected void rehash(final int newN) {
            }
         };
         map.defaultReturnValue(Integer.MAX_VALUE);
         return map;
      });
      private final Long2FloatLinkedOpenHashMap brightnessCache = Util.make(() -> {
         Long2FloatLinkedOpenHashMap map = new Long2FloatLinkedOpenHashMap(100, 0.25F) {
            protected void rehash(final int newN) {
            }
         };
         map.defaultReturnValue(Float.NaN);
         return map;
      });
      private final LevelRenderer.BrightnessGetter cachedBrightnessGetter = (level, pos) -> {
         long key = pos.asLong();
         int cached = this.colorCache.get(key);
         if (cached != Integer.MAX_VALUE) {
            return cached;
         }

         int value = LevelRenderer.BrightnessGetter.DEFAULT.packedBrightness(level, pos);
         if (this.colorCache.size() == 100) {
            this.colorCache.removeFirstInt();
         }

         this.colorCache.put(key, value);
         return value;
      };

      public void enable() {
         this.enabled = true;
      }

      public void disable() {
         this.enabled = false;
         this.colorCache.clear();
         this.brightnessCache.clear();
      }

      public int getLightColor(final BlockState state, final BlockAndTintGetter level, final BlockPos pos) {
         return LevelRenderer.getLightColor(this.enabled ? this.cachedBrightnessGetter : LevelRenderer.BrightnessGetter.DEFAULT, level, state, pos);
      }

      public float getShadeBrightness(final BlockState state, final BlockAndTintGetter level, final BlockPos pos) {
         long key = pos.asLong();
         if (this.enabled) {
            float cached = this.brightnessCache.get(key);
            if (!Float.isNaN(cached)) {
               return cached;
            }
         }

         float brightness = state.getShadeBrightness(level, pos);
         if (this.enabled) {
            if (this.brightnessCache.size() == 100) {
               this.brightnessCache.removeFirstFloat();
            }

            this.brightnessCache.put(key, brightness);
         }

         return brightness;
      }
   }

   private static class CommonRenderStorage {
      public final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
      public boolean faceCubic;
      public boolean facePartial;
      public final float[] brightness = new float[4];
      public final int[] lightmap = new int[4];
      public int tintCacheIndex = -1;
      public int tintCacheValue;
      public final ModelBlockRenderer.Cache cache = ModelBlockRenderer.CACHE.get();
   }

   protected enum SizeInfo {
      DOWN(0),
      UP(1),
      NORTH(2),
      SOUTH(3),
      WEST(4),
      EAST(5),
      FLIP_DOWN(6),
      FLIP_UP(7),
      FLIP_NORTH(8),
      FLIP_SOUTH(9),
      FLIP_WEST(10),
      FLIP_EAST(11);

      public static final int COUNT = values().length;
      private final int index;

      SizeInfo(final int index) {
         this.index = index;
      }
   }
}
