package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.statue.CopperGolemStatueModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.golem.CopperGolemOxidationLevels;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import org.joml.Vector3fc;

public class CopperGolemStatueSpecialRenderer implements NoDataSpecialModelRenderer {
   private static final Direction MODEL_STATE = Direction.SOUTH;
   private final CopperGolemStatueModel model;
   private final Identifier texture;

   public CopperGolemStatueSpecialRenderer(final CopperGolemStatueModel model, final Identifier texture) {
      this.model = model;
      this.texture = texture;
   }

   @Override
   public void submit(
      final ItemDisplayContext type,
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final int overlayCoords,
      final boolean hasFoil,
      final int outlineColor
   ) {
      positionModel(poseStack);
      submitNodeCollector.submitModel(
         this.model, Direction.SOUTH, poseStack, RenderTypes.entityCutoutNoCull(this.texture), lightCoords, overlayCoords, -1, null, outlineColor, null
      );
   }

   @Override
   public void getExtents(final Consumer<Vector3fc> output) {
      PoseStack poseStack = new PoseStack();
      positionModel(poseStack);
      this.model.setupAnim(MODEL_STATE);
      this.model.root().getExtentsForGui(poseStack, output);
   }

   private static void positionModel(final PoseStack poseStack) {
      poseStack.translate(0.5F, 1.5F, 0.5F);
      poseStack.scale(-1.0F, -1.0F, 1.0F);
   }

   public record Unbaked(Identifier texture, CopperGolemStatueBlock.Pose pose) implements SpecialModelRenderer.Unbaked {
      public static final MapCodec<CopperGolemStatueSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
               Identifier.CODEC.fieldOf("texture").forGetter(CopperGolemStatueSpecialRenderer.Unbaked::texture),
               CopperGolemStatueBlock.Pose.CODEC.fieldOf("pose").forGetter(CopperGolemStatueSpecialRenderer.Unbaked::pose)
            )
            .apply(i, CopperGolemStatueSpecialRenderer.Unbaked::new)
      );

      public Unbaked(final WeatheringCopper.WeatherState state, final CopperGolemStatueBlock.Pose pose) {
         this(CopperGolemOxidationLevels.getOxidationLevel(state).texture(), pose);
      }

      @Override
      public MapCodec<CopperGolemStatueSpecialRenderer.Unbaked> type() {
         return MAP_CODEC;
      }

      @Override
      public SpecialModelRenderer<?> bake(final SpecialModelRenderer.BakingContext context) {
         CopperGolemStatueModel model = new CopperGolemStatueModel(context.entityModelSet().bakeLayer(getModel(this.pose)));
         return new CopperGolemStatueSpecialRenderer(model, this.texture);
      }

      private static ModelLayerLocation getModel(final CopperGolemStatueBlock.Pose pose) {
         return switch (pose) {
            case STANDING -> ModelLayers.COPPER_GOLEM;
            case SITTING -> ModelLayers.COPPER_GOLEM_SITTING;
            case STAR -> ModelLayers.COPPER_GOLEM_STAR;
            case RUNNING -> ModelLayers.COPPER_GOLEM_RUNNING;
         };
      }
   }
}
