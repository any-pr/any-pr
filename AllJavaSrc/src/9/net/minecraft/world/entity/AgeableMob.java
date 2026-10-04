package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public abstract class AgeableMob extends PathfinderMob {
   private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(AgeableMob.class, EntityDataSerializers.BOOLEAN);
   public static final int BABY_START_AGE = -24000;
   private static final int FORCED_AGE_PARTICLE_TICKS = 40;
   protected static final int DEFAULT_AGE = 0;
   protected static final int DEFAULT_FORCED_AGE = 0;
   protected int age = 0;
   protected int forcedAge = 0;
   protected int forcedAgeTimer;

   protected AgeableMob(final EntityType<? extends AgeableMob> type, final Level level) {
      super(type, level);
   }

   @Override
   public @Nullable SpawnGroupData finalizeSpawn(
      final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData
   ) {
      if (groupData == null) {
         groupData = new AgeableMob.AgeableMobGroupData(true);
      }

      AgeableMob.AgeableMobGroupData ageableMobGroupData = (AgeableMob.AgeableMobGroupData)groupData;
      if (ageableMobGroupData.isShouldSpawnBaby()
         && ageableMobGroupData.getGroupSize() > 0
         && level.getRandom().nextFloat() <= ageableMobGroupData.getBabySpawnChance()) {
         this.setAge(-24000);
      }

      ageableMobGroupData.increaseGroupSizeByOne();
      return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
   }

   public abstract @Nullable AgeableMob getBreedOffspring(final ServerLevel level, AgeableMob partner);

   @Override
   protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
      super.defineSynchedData(entityData);
      entityData.define(DATA_BABY_ID, false);
   }

   public boolean canBreed() {
      return false;
   }

   public int getAge() {
      if (this.level().isClientSide()) {
         return this.entityData.get(DATA_BABY_ID) ? -1 : 1;
      } else {
         return this.age;
      }
   }

   public void ageUp(final int seconds, final boolean forced) {
      int age = this.getAge();
      int oldAge = age;
      age += seconds * 20;
      if (age > 0) {
         age = 0;
      }

      int delta = age - oldAge;
      this.setAge(age);
      if (forced) {
         this.forcedAge += delta;
         if (this.forcedAgeTimer == 0) {
            this.forcedAgeTimer = 40;
         }
      }

      if (this.getAge() == 0) {
         this.setAge(this.forcedAge);
      }
   }

   public void ageUp(final int seconds) {
      this.ageUp(seconds, false);
   }

   public void setAge(final int newAge) {
      int oldAge = this.getAge();
      this.age = newAge;
      if (oldAge < 0 && newAge >= 0 || oldAge >= 0 && newAge < 0) {
         this.entityData.set(DATA_BABY_ID, newAge < 0);
         this.ageBoundaryReached();
      }
   }

   @Override
   protected void addAdditionalSaveData(final ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.putInt("Age", this.getAge());
      output.putInt("ForcedAge", this.forcedAge);
   }

   @Override
   protected void readAdditionalSaveData(final ValueInput input) {
      super.readAdditionalSaveData(input);
      this.setAge(input.getIntOr("Age", 0));
      this.forcedAge = input.getIntOr("ForcedAge", 0);
   }

   @Override
   public void onSyncedDataUpdated(final EntityDataAccessor<?> accessor) {
      if (DATA_BABY_ID.equals(accessor)) {
         this.refreshDimensions();
      }

      super.onSyncedDataUpdated(accessor);
   }

   @Override
   public void aiStep() {
      super.aiStep();
      if (this.level().isClientSide()) {
         if (this.forcedAgeTimer > 0) {
            if (this.forcedAgeTimer % 4 == 0) {
               this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0.0, 0.0, 0.0);
            }

            this.forcedAgeTimer--;
         }
      } else if (this.isAlive()) {
         int age = this.getAge();
         if (age < 0) {
            this.setAge(++age);
         } else if (age > 0) {
            this.setAge(--age);
         }
      }
   }

   protected void ageBoundaryReached() {
      if (!this.isBaby() && this.isPassenger() && this.getVehicle() instanceof AbstractBoat boat && !boat.hasEnoughSpaceFor(this)) {
         this.stopRiding();
      }
   }

   @Override
   public boolean isBaby() {
      return this.getAge() < 0;
   }

   @Override
   public void setBaby(final boolean baby) {
      this.setAge(baby ? -24000 : 0);
   }

   public static int getSpeedUpSecondsWhenFeeding(final int ticksUntilAdult) {
      return (int)(ticksUntilAdult / 20 * 0.1F);
   }

   @VisibleForTesting
   public int getForcedAge() {
      return this.forcedAge;
   }

   @VisibleForTesting
   public int getForcedAgeTimer() {
      return this.forcedAgeTimer;
   }

   public static class AgeableMobGroupData implements SpawnGroupData {
      private int groupSize;
      private final boolean shouldSpawnBaby;
      private final float babySpawnChance;

      public AgeableMobGroupData(final boolean shouldSpawnBaby, final float babySpawnChance) {
         this.shouldSpawnBaby = shouldSpawnBaby;
         this.babySpawnChance = babySpawnChance;
      }

      public AgeableMobGroupData(final boolean shouldSpawnBaby) {
         this(shouldSpawnBaby, 0.05F);
      }

      public AgeableMobGroupData(final float babySpawnChance) {
         this(true, babySpawnChance);
      }

      public int getGroupSize() {
         return this.groupSize;
      }

      public void increaseGroupSizeByOne() {
         this.groupSize++;
      }

      public boolean isShouldSpawnBaby() {
         return this.shouldSpawnBaby;
      }

      public float getBabySpawnChance() {
         return this.babySpawnChance;
      }
   }
}
