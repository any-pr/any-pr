package net.minecraft.world.entity.animal.nautilus;

import com.mojang.serialization.Dynamic;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class ZombieNautilus extends AbstractNautilus {
   private static final EntityDataAccessor<Holder<ZombieNautilusVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(
      ZombieNautilus.class, EntityDataSerializers.ZOMBIE_NAUTILUS_VARIANT
   );

   public ZombieNautilus(final EntityType<? extends ZombieNautilus> type, final Level level) {
      super(type, level);
   }

   public static AttributeSupplier.Builder createAttributes() {
      return AbstractNautilus.createAttributes().add(Attributes.MOVEMENT_SPEED, 1.1F);
   }

   public @Nullable ZombieNautilus getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
      return null;
   }

   @Override
   protected EquipmentSlot sunProtectionSlot() {
      return EquipmentSlot.BODY;
   }

   @Override
   protected Brain.Provider<ZombieNautilus> brainProvider() {
      return ZombieNautilusAi.brainProvider();
   }

   @Override
   protected Brain<?> makeBrain(final Dynamic<?> input) {
      return ZombieNautilusAi.makeBrain(this.brainProvider().makeBrain(input));
   }

   @Override
   public Brain<ZombieNautilus> getBrain() {
      return (Brain<ZombieNautilus>)super.getBrain();
   }

   @Override
   protected void customServerAiStep(final ServerLevel level) {
      ProfilerFiller profiler = Profiler.get();
      profiler.push("zombieNautilusBrain");
      this.getBrain().tick(level, this);
      profiler.pop();
      profiler.push("zombieNautilusActivityUpdate");
      ZombieNautilusAi.updateActivity(this);
      profiler.pop();
      super.customServerAiStep(level);
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_AMBIENT : SoundEvents.ZOMBIE_NAUTILUS_AMBIENT_ON_LAND;
   }

   @Override
   protected SoundEvent getHurtSound(final DamageSource source) {
      return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_HURT : SoundEvents.ZOMBIE_NAUTILUS_HURT_ON_LAND;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_DEATH : SoundEvents.ZOMBIE_NAUTILUS_DEATH_ON_LAND;
   }

   @Override
   protected SoundEvent getDashSound() {
      return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_DASH : SoundEvents.ZOMBIE_NAUTILUS_DASH_ON_LAND;
   }

   @Override
   protected SoundEvent getDashReadySound() {
      return this.isUnderWater() ? SoundEvents.ZOMBIE_NAUTILUS_DASH_READY : SoundEvents.ZOMBIE_NAUTILUS_DASH_READY_ON_LAND;
   }

   @Override
   protected void playEatingSound() {
      this.makeSound(SoundEvents.ZOMBIE_NAUTILUS_EAT);
   }

   @Override
   protected SoundEvent getSwimSound() {
      return SoundEvents.ZOMBIE_NAUTILUS_SWIM;
   }

   @Override
   protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
      super.defineSynchedData(entityData);
      entityData.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), ZombieNautilusVariants.TEMPERATE));
   }

   @Override
   protected void readAdditionalSaveData(final ValueInput input) {
      super.readAdditionalSaveData(input);
      VariantUtils.readVariant(input, Registries.ZOMBIE_NAUTILUS_VARIANT).ifPresent(this::setVariant);
   }

   @Override
   protected void addAdditionalSaveData(final ValueOutput output) {
      super.addAdditionalSaveData(output);
      VariantUtils.writeVariant(output, this.getVariant());
   }

   public void setVariant(final Holder<ZombieNautilusVariant> variant) {
      this.entityData.set(DATA_VARIANT_ID, variant);
   }

   public Holder<ZombieNautilusVariant> getVariant() {
      return this.entityData.get(DATA_VARIANT_ID);
   }

   @Override
   public <T> @Nullable T get(final DataComponentType<? extends T> type) {
      return type == DataComponents.ZOMBIE_NAUTILUS_VARIANT
         ? castComponentValue((DataComponentType<T>)type, new EitherHolder<>(this.getVariant()))
         : super.get(type);
   }

   @Override
   protected void applyImplicitComponents(final DataComponentGetter components) {
      this.applyImplicitComponentIfPresent(components, DataComponents.ZOMBIE_NAUTILUS_VARIANT);
      super.applyImplicitComponents(components);
   }

   @Override
   protected <T> boolean applyImplicitComponent(final DataComponentType<T> type, final T value) {
      if (type == DataComponents.ZOMBIE_NAUTILUS_VARIANT) {
         Optional<Holder<ZombieNautilusVariant>> variant = castComponentValue(DataComponents.ZOMBIE_NAUTILUS_VARIANT, value).unwrap(this.registryAccess());
         if (variant.isPresent()) {
            this.setVariant(variant.get());
            return true;
         } else {
            return false;
         }
      } else {
         return super.applyImplicitComponent(type, value);
      }
   }

   @Override
   public SpawnGroupData finalizeSpawn(
      final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, final @Nullable SpawnGroupData groupData
   ) {
      VariantUtils.selectVariantToSpawn(SpawnContext.create(level, this.blockPosition()), Registries.ZOMBIE_NAUTILUS_VARIANT).ifPresent(this::setVariant);
      return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
   }

   @Override
   public boolean canBeLeashed() {
      return !this.isAggravated() && !this.isMobControlled();
   }

   @Override
   public boolean isBaby() {
      return false;
   }
}
