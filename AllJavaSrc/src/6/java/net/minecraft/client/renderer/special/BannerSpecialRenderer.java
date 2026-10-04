package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public class BannerSpecialRenderer implements SpecialModelRenderer<BannerPatternLayers> {
   private final BannerRenderer bannerRenderer;
   private final DyeColor baseColor;

   public BannerSpecialRenderer(final DyeColor baseColor, final BannerRenderer bannerRenderer) {
      this.bannerRenderer = bannerRenderer;
      this.baseColor = baseColor;
   }

   public @Nullable BannerPatternLayers extractArgument(final ItemStack stack) {
      return stack.get(DataComponents.BANNER_PATTERNS);
   }

   public void submit(
      final @Nullable BannerPatternLayers patterns,
      final ItemDisplayContext type,
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      final int overlayCoords,
      final boolean hasFoil,
      final int outlineColor
   ) {
      this.bannerRenderer
         .submitSpecial(
            poseStack,
            submitNodeCollector,
            lightCoords,
            overlayCoords,
            this.baseColor,
            Objects.requireNonNullElse(patterns, BannerPatternLayers.EMPTY),
            outlineColor
         );
   }

   @Override
   public void getExtents(final Consumer<Vector3fc> output) {
      this.bannerRenderer.getExtents(output);
   }

   public record Unbaked(DyeColor baseColor) implements SpecialModelRenderer.Unbaked {
      public static final MapCodec<BannerSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(DyeColor.CODEC.fieldOf("color").forGetter(BannerSpecialRenderer.Unbaked::baseColor)).apply(i, BannerSpecialRenderer.Unbaked::new)
      );

      @Override
      public MapCodec<BannerSpecialRenderer.Unbaked> type() {
         return MAP_CODEC;
      }

      @Override
      public SpecialModelRenderer<?> bake(final SpecialModelRenderer.BakingContext context) {
         return new BannerSpecialRenderer(this.baseColor, new BannerRenderer(context));
      }
   }
}
