package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public interface SpecialModelRenderer<T> {
   void submit(
      @Nullable T argument,
      ItemDisplayContext type,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      int overlayCoords,
      boolean hasFoil,
      final int outlineColor
   );

   void getExtents(Consumer<Vector3fc> output);

   @Nullable T extractArgument(ItemStack stack);

   interface BakingContext {
      EntityModelSet entityModelSet();

      MaterialSet materials();

      PlayerSkinRenderCache playerSkinRenderCache();

      record Simple(EntityModelSet entityModelSet, MaterialSet materials, PlayerSkinRenderCache playerSkinRenderCache)
         implements SpecialModelRenderer.BakingContext {
      }
   }

   interface Unbaked {
      @Nullable SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context);

      MapCodec<? extends SpecialModelRenderer.Unbaked> type();
   }
}
