package net.minecraft.client.renderer;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class EndFlashState {
   public static final int SOUND_DELAY_IN_TICKS = 30;
   private static final int FLASH_INTERVAL_IN_TICKS = 600;
   private static final int MAX_FLASH_OFFSET_IN_TICKS = 200;
   private static final int MIN_FLASH_DURATION_IN_TICKS = 100;
   private static final int MAX_FLASH_DURATION_IN_TICKS = 380;
   private long flashSeed;
   private int offset;
   private int duration;
   private float intensity;
   private float oldIntensity;
   private float xAngle;
   private float yAngle;

   public void tick(final long gameTime) {
      this.calculateFlashParameters(gameTime);
      this.oldIntensity = this.intensity;
      this.intensity = this.calculateIntensity(gameTime);
   }

   private void calculateFlashParameters(final long gameTime) {
      long newSeed = gameTime / 600L;
      if (newSeed != this.flashSeed) {
         RandomSource randomSource = RandomSource.create(newSeed);
         randomSource.nextFloat();
         this.offset = Mth.randomBetweenInclusive(randomSource, 0, 200);
         this.duration = Mth.randomBetweenInclusive(randomSource, 100, Math.min(380, 600 - this.offset));
         this.xAngle = Mth.randomBetween(randomSource, -60.0F, 10.0F);
         this.yAngle = Mth.randomBetween(randomSource, -180.0F, 180.0F);
         this.flashSeed = newSeed;
      }
   }

   private float calculateIntensity(final long gameTime) {
      long gameTimeWithinInterval = gameTime % 600L;
      return gameTimeWithinInterval >= this.offset && gameTimeWithinInterval <= this.offset + this.duration
         ? Mth.sin((float)(gameTimeWithinInterval - this.offset) * (float) Math.PI / this.duration)
         : 0.0F;
   }

   public float getXAngle() {
      return this.xAngle;
   }

   public float getYAngle() {
      return this.yAngle;
   }

   public float getIntensity(final float partialTicks) {
      return Mth.lerp(partialTicks, this.oldIntensity, this.intensity);
   }

   public boolean flashStartedThisTick() {
      return this.intensity > 0.0F && this.oldIntensity <= 0.0F;
   }
}
