package net.minecraft.client.renderer.blockentity;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BrightnessCombiner<S extends BlockEntity> implements DoubleBlockCombiner.Combiner<S, Int2IntFunction> {
   public Int2IntFunction acceptDouble(final S first, final S second) {
      return i -> {
         int firstCoords = LevelRenderer.getLightColor(first.getLevel(), first.getBlockPos());
         int secondCoords = LevelRenderer.getLightColor(second.getLevel(), second.getBlockPos());
         int firstBlock = LightTexture.block(firstCoords);
         int secondBlock = LightTexture.block(secondCoords);
         int firstSky = LightTexture.sky(firstCoords);
         int secondSky = LightTexture.sky(secondCoords);
         return LightTexture.pack(Math.max(firstBlock, secondBlock), Math.max(firstSky, secondSky));
      };
   }

   public Int2IntFunction acceptSingle(final S single) {
      return i -> i;
   }

   public Int2IntFunction acceptNone() {
      return i -> i;
   }
}
