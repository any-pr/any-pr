package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class ShieldSpecialRenderer implements SpecialModelRenderer<DataComponentMap> {
   private final MaterialSet materials;
   private final ShieldModel model;

   public ShieldSpecialRenderer(final MaterialSet materials, final ShieldModel model) {
      this.materials = materials;
      this.model = model;
   }

   public @Nullable DataComponentMap extractArgument(final ItemStack stack) {
      return stack.immutableComponents();
   }

   public void submit(
      final @Nullable DataComponentMap components,
      final ItemDisplayContext type,
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final int overlayCoords,
      final boolean hasFoil,
      final int outlineColor
   ) {
      BannerPatternLayers patterns = components != null
         ? components.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
         : BannerPatternLayers.EMPTY;
      DyeColor baseColor = components != null ? components.get(DataComponents.BASE_COLOR) : null;
      boolean hasPatterns = !patterns.layers().isEmpty() || baseColor != null;
      poseStack.pushPose();
      poseStack.scale(1.0F, -1.0F, -1.0F);
      Material base = hasPatterns ? ModelBakery.SHIELD_BASE : ModelBakery.NO_PATTERN_SHIELD;
      submitNodeCollector.submitModelPart(
         this.model.handle(),
         poseStack,
         this.model.renderType(base.atlasLocation()),
         lightCoords,
         overlayCoords,
         this.materials.get(base),
         false,
         false,
         -1,
         null,
         outlineColor
      );
      if (hasPatterns) {
         BannerRenderer.submitPatterns(
            this.materials,
            poseStack,
            submitNodeCollector,
            lightCoords,
            overlayCoords,
            this.model,
            Unit.INSTANCE,
            base,
            false,
            Objects.requireNonNullElse(baseColor, DyeColor.WHITE),
            patterns,
            hasFoil,
            null,
            outlineColor
         );
      } else {
         submitNodeCollector.submitModelPart(
            this.model.plate(),
            poseStack,
            this.model.renderType(base.atlasLocation()),
            lightCoords,
            overlayCoords,
            this.materials.get(base),
            false,
            hasFoil,
            -1,
            null,
            outlineColor
         );
      }

      poseStack.popPose();
   }

   @Override
   public void getExtents(final Consumer<Vector3fc> output) {
      PoseStack poseStack = new PoseStack();
      poseStack.scale(1.0F, -1.0F, -1.0F);
      this.model.root().getExtentsForGui(poseStack, output);
   }

   public record Unbaked() implements SpecialModelRenderer.Unbaked {
      public static final ShieldSpecialRenderer.Unbaked INSTANCE = new ShieldSpecialRenderer.Unbaked();
      public static final MapCodec<ShieldSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

      @Override
      public MapCodec<ShieldSpecialRenderer.Unbaked> type() {
         return MAP_CODEC;
      }

      @Override
      public SpecialModelRenderer<?> bake(final SpecialModelRenderer.BakingContext context) {
         return new ShieldSpecialRenderer(context.materials(), new ShieldModel(context.entityModelSet().bakeLayer(ModelLayers.SHIELD)));
      }
   }
}
