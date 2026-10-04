package net.minecraft.world.effect;

public class InstantenousMobEffect extends MobEffect {
   public InstantenousMobEffect(final MobEffectCategory category, final int color) {
      super(category, color);
   }

   @Override
   public boolean isInstantenous() {
      return true;
   }

   @Override
   public boolean shouldApplyEffectTickThisTick(final int remainingDuration, final int amplification) {
      return remainingDuration >= 1;
   }
}
