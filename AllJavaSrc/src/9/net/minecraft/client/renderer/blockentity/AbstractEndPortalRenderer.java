package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public abstract class AbstractEndPortalRenderer<T extends TheEndPortalBlockEntity, S extends EndPortalRenderState> implements BlockEntityRenderer<T, S> {
   public static final Identifier END_SKY_LOCATION = Identifier.withDefaultNamespace("textures/environment/end_sky.png");
   public static final Identifier END_PORTAL_LOCATION = Identifier.withDefaultNamespace("textures/entity/end_portal.png");

   public void extractRenderState(
      final T blockEntity,
      final S state,
      final float partialTicks,
      final Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
   ) {
      BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
      state.facesToShow.clear();

      for (Direction direction : Direction.values()) {
         if (blockEntity.shouldRenderFace(direction)) {
            state.facesToShow.add(direction);
         }
      }
   }

   public void submit(final S state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
      submitNodeCollector.submitCustomGeometry(poseStack, this.renderType(), (pose1, buffer) -> this.renderCube(state.facesToShow, pose1.pose(), buffer));
   }

   private void renderCube(final EnumSet<Direction> facesToShow, final Matrix4f pose, final VertexConsumer builder) {
      float offsetDown = this.getOffsetDown();
      float offsetUp = this.getOffsetUp();
      this.renderFace(facesToShow, pose, builder, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, Direction.SOUTH);
      this.renderFace(facesToShow, pose, builder, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, Direction.NORTH);
      this.renderFace(facesToShow, pose, builder, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, Direction.EAST);
      this.renderFace(facesToShow, pose, builder, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, Direction.WEST);
      this.renderFace(facesToShow, pose, builder, 0.0F, 1.0F, offsetDown, offsetDown, 0.0F, 0.0F, 1.0F, 1.0F, Direction.DOWN);
      this.renderFace(facesToShow, pose, builder, 0.0F, 1.0F, offsetUp, offsetUp, 1.0F, 1.0F, 0.0F, 0.0F, Direction.UP);
   }

   private void renderFace(
      final EnumSet<Direction> facesToShow,
      final Matrix4f pose,
      final VertexConsumer builder,
      final float x1,
      final float x2,
      final float y1,
      final float y2,
      final float z1,
      final float z2,
      final float z3,
      final float z4,
      final Direction face
   ) {
      if (facesToShow.contains(face)) {
         builder.addVertex(pose, x1, y1, z1);
         builder.addVertex(pose, x2, y1, z2);
         builder.addVertex(pose, x2, y2, z3);
         builder.addVertex(pose, x1, y2, z4);
      }
   }

   protected float getOffsetUp() {
      return 0.75F;
   }

   protected float getOffsetDown() {
      return 0.375F;
   }

   protected RenderType renderType() {
      return RenderTypes.endPortal();
   }
}
