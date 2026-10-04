package net.minecraft.world.entity.animal.nautilus;

import com.mojang.serialization.Dynamic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class Nautilus extends AbstractNautilus {
   private static final int NAUTILUS_TOTAL_AIR_SUPPLY = 300;

   public Nautilus(final EntityType<? extends Nautilus> type, final Level level) {
      super(type, level);
   }

   @Override
   protected Brain.Provider<Nautilus> brainProvider() {
      return NautilusAi.brainProvider();
   }

   @Override
   protected Brain<?> makeBrain(final Dynamic<?> input) {
      return NautilusAi.makeBrain(this.brainProvider().makeBrain(input));
   }

   @Override
   public Brain<Nautilus> getBrain() {
      return (Brain<Nautilus>)super.getBrain();
   }

   public @Nullable Nautilus getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
      Nautilus baby = EntityType.NAUTILUS.create(level, EntitySpawnReason.BREEDING);
      if (baby != null && this.isTame()) {
         baby.setOwnerReference(this.getOwnerReference());
         baby.setTame(true, true);
      }

      return baby;
   }

   @Override
   protected void customServerAiStep(final ServerLevel level) {
      ProfilerFiller profiler = Profiler.get();
      profiler.push("nautilusBrain");
      this.getBrain().tick(level, this);
      profiler.pop();
      profiler.push("nautilusActivityUpdate");
      NautilusAi.updateActivity(this);
      profiler.pop();
      super.customServerAiStep(level);
   }

   @Override
   protected SoundEvent getAmbientSound() {
      if (this.isBaby()) {
         return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_AMBIENT : SoundEvents.BABY_NAUTILUS_AMBIENT_ON_LAND;
      } else {
         return this.isUnderWater() ? SoundEvents.NAUTILUS_AMBIENT : SoundEvents.NAUTILUS_AMBIENT_ON_LAND;
      }
   }

   @Override
   protected SoundEvent getHurtSound(final DamageSource source) {
      if (this.isBaby()) {
         return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_HURT : SoundEvents.BABY_NAUTILUS_HURT_ON_LAND;
      } else {
         return this.isUnderWater() ? SoundEvents.NAUTILUS_HURT : SoundEvents.NAUTILUS_HURT_ON_LAND;
      }
   }

   @Override
   protected SoundEvent getDeathSound() {
      if (this.isBaby()) {
         return this.isUnderWater() ? SoundEvents.BABY_NAUTILUS_DEATH : SoundEvents.BABY_NAUTILUS_DEATH_ON_LAND;
      } else {
         return this.isUnderWater() ? SoundEvents.NAUTILUS_DEATH : SoundEvents.NAUTILUS_DEATH_ON_LAND;
      }
   }

   @Override
   protected SoundEvent getDashSound() {
      return this.isUnderWater() ? SoundEvents.NAUTILUS_DASH : SoundEvents.NAUTILUS_DASH_ON_LAND;
   }

   @Override
   protected SoundEvent getDashReadySound() {
      return this.isUnderWater() ? SoundEvents.NAUTILUS_DASH_READY : SoundEvents.NAUTILUS_DASH_READY_ON_LAND;
   }

   @Override
   protected void playEatingSound() {
      SoundEvent nautilusEatSound = this.isBaby() ? SoundEvents.BABY_NAUTILUS_EAT : SoundEvents.NAUTILUS_EAT;
      this.makeSound(nautilusEatSound);
   }

   @Override
   protected SoundEvent getSwimSound() {
      return this.isBaby() ? SoundEvents.BABY_NAUTILUS_SWIM : SoundEvents.NAUTILUS_SWIM;
   }

   @Override
   public int getMaxAirSupply() {
      return 300;
   }

   protected void handleAirSupply(final ServerLevel level, final int preTickAirSupply) {
      if (this.isAlive() && !this.isInWater()) {
         this.setAirSupply(preTickAirSupply - 1);
         if (this.getAirSupply() <= -20) {
            this.setAirSupply(0);
            this.hurtServer(level, this.damageSources().dryOut(), 2.0F);
         }
      } else {
         this.setAirSupply(300);
      }
   }

   @Override
   public void baseTick() {
      int airSupply = this.getAirSupply();
      super.baseTick();
      if (!this.isNoAi() && this.level() instanceof ServerLevel serverLevel) {
         this.handleAirSupply(serverLevel, airSupply);
      }
   }

   @Override
   public boolean canBeLeashed() {
      return !this.isAggravated();
   }
}
