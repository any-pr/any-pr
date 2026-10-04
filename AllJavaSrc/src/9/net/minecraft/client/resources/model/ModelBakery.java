package net.minecraft.client.resources.model;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.SimpleModelWrapper;
import net.minecraft.client.renderer.block.model.SingleVariant;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.thread.ParallelMapTransform;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;
import org.slf4j.Logger;

public class ModelBakery {
   public static final Material FIRE_0 = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("fire_0");
   public static final Material FIRE_1 = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("fire_1");
   public static final Material LAVA_STILL = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("lava_still");
   public static final Material LAVA_FLOW = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("lava_flow");
   public static final Material WATER_STILL = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("water_still");
   public static final Material WATER_FLOW = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("water_flow");
   public static final Material WATER_OVERLAY = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("water_overlay");
   public static final Material BANNER_BASE = new Material(Sheets.BANNER_SHEET, Identifier.withDefaultNamespace("entity/banner_base"));
   public static final Material SHIELD_BASE = new Material(Sheets.SHIELD_SHEET, Identifier.withDefaultNamespace("entity/shield_base"));
   public static final Material NO_PATTERN_SHIELD = new Material(Sheets.SHIELD_SHEET, Identifier.withDefaultNamespace("entity/shield_base_nopattern"));
   public static final int DESTROY_STAGE_COUNT = 10;
   public static final List<Identifier> DESTROY_STAGES = IntStream.range(0, 10)
      .mapToObj(i -> Identifier.withDefaultNamespace("block/destroy_stage_" + i))
      .collect(Collectors.toList());
   public static final List<Identifier> BREAKING_LOCATIONS = DESTROY_STAGES.stream()
      .map(location -> location.withPath(path -> "textures/" + path + ".png"))
      .collect(Collectors.toList());
   public static final List<RenderType> DESTROY_TYPES = BREAKING_LOCATIONS.stream().map(RenderTypes::crumbling).collect(Collectors.toList());
   private static final Logger LOGGER = LogUtils.getLogger();
   private final EntityModelSet entityModelSet;
   private final MaterialSet materials;
   private final PlayerSkinRenderCache playerSkinRenderCache;
   private final Map<BlockState, BlockStateModel.UnbakedRoot> unbakedBlockStateModels;
   private final Map<Identifier, ClientItem> clientInfos;
   private final Map<Identifier, ResolvedModel> resolvedModels;
   private final ResolvedModel missingModel;

   public ModelBakery(
      final EntityModelSet entityModelSet,
      final MaterialSet materials,
      final PlayerSkinRenderCache playerSkinRenderCache,
      final Map<BlockState, BlockStateModel.UnbakedRoot> unbakedBlockStateModels,
      final Map<Identifier, ClientItem> clientInfos,
      final Map<Identifier, ResolvedModel> resolvedModels,
      final ResolvedModel missingModel
   ) {
      this.entityModelSet = entityModelSet;
      this.materials = materials;
      this.playerSkinRenderCache = playerSkinRenderCache;
      this.unbakedBlockStateModels = unbakedBlockStateModels;
      this.clientInfos = clientInfos;
      this.resolvedModels = resolvedModels;
      this.missingModel = missingModel;
   }

   public CompletableFuture<ModelBakery.BakingResult> bakeModels(final SpriteGetter sprites, final Executor taskExecutor) {
      ModelBakery.PartCacheImpl parts = new ModelBakery.PartCacheImpl();
      ModelBakery.MissingModels missingModels = ModelBakery.MissingModels.bake(this.missingModel, sprites, parts);
      ModelBakery.ModelBakerImpl baker = new ModelBakery.ModelBakerImpl(sprites, parts, missingModels);
      CompletableFuture<Map<BlockState, BlockStateModel>> bakedBlockStateModelFuture = ParallelMapTransform.schedule(
         this.unbakedBlockStateModels, (blockState, model) -> {
            try {
               return model.bake(blockState, baker);
            } catch (Exception e) {
               LOGGER.warn("Unable to bake model: '{}': {}", blockState, e);
               return null;
            }
         }, taskExecutor
      );
      CompletableFuture<Map<Identifier, ItemModel>> bakedItemStackModelFuture = ParallelMapTransform.schedule(
         this.clientInfos,
         (location, clientInfo) -> {
            try {
               return clientInfo.model()
                  .bake(
                     new ItemModel.BakingContext(
                        baker, this.entityModelSet, this.materials, this.playerSkinRenderCache, missingModels.item, clientInfo.registrySwapper()
                     )
                  );
            } catch (Exception e) {
               LOGGER.warn("Unable to bake item model: '{}'", location, e);
               return null;
            }
         },
         taskExecutor
      );
      Map<Identifier, ClientItem.Properties> itemStackModelProperties = new HashMap<>(this.clientInfos.size());
      this.clientInfos.forEach((id, clientInfo) -> {
         ClientItem.Properties properties = clientInfo.properties();
         if (!properties.equals(ClientItem.Properties.DEFAULT)) {
            itemStackModelProperties.put(id, properties);
         }
      });
      return bakedBlockStateModelFuture.thenCombine(
         bakedItemStackModelFuture,
         (bakedBlockStateModels, bakedItemStateModels) -> new ModelBakery.BakingResult(
            missingModels, (Map<BlockState, BlockStateModel>)bakedBlockStateModels, (Map<Identifier, ItemModel>)bakedItemStateModels, itemStackModelProperties
         )
      );
   }

   public record BakingResult(
      ModelBakery.MissingModels missingModels,
      Map<BlockState, BlockStateModel> blockStateModels,
      Map<Identifier, ItemModel> itemStackModels,
      Map<Identifier, ClientItem.Properties> itemProperties
   ) {
   }

   public record MissingModels(BlockModelPart blockPart, BlockStateModel block, ItemModel item) {
      public static ModelBakery.MissingModels bake(final ResolvedModel unbaked, final SpriteGetter sprites, final ModelBaker.PartCache parts) {
         ModelBaker missingModelBakery = new ModelBaker() {
            @Override
            public ResolvedModel getModel(final Identifier location) {
               throw new IllegalStateException("Missing model can't have dependencies, but asked for " + location);
            }

            @Override
            public BlockModelPart missingBlockModelPart() {
               throw new IllegalStateException();
            }

            @Override
            public <T> T compute(final ModelBaker.SharedOperationKey<T> key) {
               return key.compute(this);
            }

            @Override
            public SpriteGetter sprites() {
               return sprites;
            }

            @Override
            public ModelBaker.PartCache parts() {
               return parts;
            }
         };
         TextureSlots textureSlots = unbaked.getTopTextureSlots();
         boolean hasAmbientOcclusion = unbaked.getTopAmbientOcclusion();
         boolean usesBlockLight = unbaked.getTopGuiLight().lightLikeBlock();
         ItemTransforms transforms = unbaked.getTopTransforms();
         QuadCollection geometry = unbaked.bakeTopGeometry(textureSlots, missingModelBakery, BlockModelRotation.IDENTITY);
         TextureAtlasSprite particleSprite = unbaked.resolveParticleSprite(textureSlots, missingModelBakery);
         SimpleModelWrapper missingModelPart = new SimpleModelWrapper(geometry, hasAmbientOcclusion, particleSprite);
         BlockStateModel bakedBlockModel = new SingleVariant(missingModelPart);
         ItemModel bakedItemModel = new MissingItemModel(geometry.getAll(), new ModelRenderProperties(usesBlockLight, particleSprite, transforms));
         return new ModelBakery.MissingModels(missingModelPart, bakedBlockModel, bakedItemModel);
      }
   }

   private class ModelBakerImpl implements ModelBaker {
      private final SpriteGetter sprites;
      private final ModelBaker.PartCache parts;
      private final ModelBakery.MissingModels missingModels;
      private final Map<ModelBaker.SharedOperationKey<Object>, Object> operationCache = new ConcurrentHashMap<>();
      private final Function<ModelBaker.SharedOperationKey<Object>, Object> cacheComputeFunction = k -> k.compute(this);

      private ModelBakerImpl(final SpriteGetter textures, final ModelBaker.PartCache parts, final ModelBakery.MissingModels missingModels) {
         this.sprites = textures;
         this.parts = parts;
         this.missingModels = missingModels;
      }

      @Override
      public BlockModelPart missingBlockModelPart() {
         return this.missingModels.blockPart;
      }

      @Override
      public SpriteGetter sprites() {
         return this.sprites;
      }

      @Override
      public ModelBaker.PartCache parts() {
         return this.parts;
      }

      @Override
      public ResolvedModel getModel(final Identifier location) {
         ResolvedModel result = ModelBakery.this.resolvedModels.get(location);
         if (result == null) {
            ModelBakery.LOGGER.warn("Requested a model that was not discovered previously: {}", location);
            return ModelBakery.this.missingModel;
         } else {
            return result;
         }
      }

      @Override
      public <T> T compute(final ModelBaker.SharedOperationKey<T> key) {
         return (T)this.operationCache.computeIfAbsent(key, this.cacheComputeFunction);
      }
   }

   private static class PartCacheImpl implements ModelBaker.PartCache {
      private final Interner<Vector3fc> vectors = Interners.newStrongInterner();

      @Override
      public Vector3fc vector(final Vector3fc v) {
         return (Vector3fc)this.vectors.intern(v);
      }
   }
}
