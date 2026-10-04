package net.minecraft.world.entity.animal.goat;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Goat extends Animal {
   public static final EntityDimensions LONG_JUMPING_DIMENSIONS = EntityDimensions.scalable(0.9F, 1.3F).scale(0.7F);
   private static final int ADULT_ATTACK_DAMAGE = 2;
   private static final int BABY_ATTACK_DAMAGE = 1;
   protected static final ImmutableList<SensorType<? extends Sensor<? super Goat>>> SENSOR_TYPES = ImmutableList.of(
      SensorType.NEAREST_LIVING_ENTITIES,
      SensorType.NEAREST_PLAYERS,
      SensorType.NEAREST_ITEMS,
      SensorType.NEAREST_ADULT,
      SensorType.HURT_BY,
      SensorType.FOOD_TEMPTATIONS
   );
   protected static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
      MemoryModuleType.LOOK_TARGET,
      MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
      MemoryModuleType.WALK_TARGET,
      MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
      MemoryModuleType.PATH,
      MemoryModuleType.ATE_RECENTLY,
      MemoryModuleType.BREED_TARGET,
      MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS,
      MemoryModuleType.LONG_JUMP_MID_JUMP,
      MemoryModuleType.TEMPTING_PLAYER,
      MemoryModuleType.NEAREST_VISIBLE_ADULT,
      MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
      new MemoryModuleType[]{MemoryModuleType.IS_TEMPTED, MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryModuleType.RAM_TARGET, MemoryModuleType.IS_PANICKING}
   );
   public static final int GOAT_FALL_DAMAGE_REDUCTION = 10;
   public static final double GOAT_SCREAMING_CHANCE = 0.02;
   public static final double UNIHORN_CHANCE = 0.1F;
   private static final EntityDataAccessor<Boolean> DATA_IS_SCREAMING_GOAT = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> DATA_HAS_LEFT_HORN = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> DATA_HAS_RIGHT_HORN = SynchedEntityData.defineId(Goat.class, EntityDataSerializers.BOOLEAN);
   private static final boolean DEFAULT_IS_SCREAMING = false;
   private static final boolean DEFAULT_HAS_LEFT_HORN = true;
   private static final boolean DEFAULT_HAS_RIGHT_HORN = true;
   private boolean isLoweringHead;
   private int lowerHeadTick;

   public Goat(final EntityType<? extends Goat> type, final Level level) {
      super(type, level);
      this.getNavigation().setCanFloat(true);
      this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
      this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
   }

   public ItemStack createHorn() {
      RandomSource random = RandomSource.create(this.getUUID().hashCode());
      TagKey<Instrument> key = this.isScreamingGoat() ? InstrumentTags.SCREAMING_GOAT_HORNS : InstrumentTags.REGULAR_GOAT_HORNS;
      return this.level()
         .registryAccess()
         .lookupOrThrow(Registries.INSTRUMENT)
         .getRandomElementOf(key, random)
         .map(instrument -> InstrumentItem.create(Items.GOAT_HORN, (Holder<Instrument>)instrument))
         .orElseGet(() -> new ItemStack(Items.GOAT_HORN));
   }

   @Override
   protected Brain.Provider<Goat> brainProvider() {
      return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
   }

   @Override
   protected Brain<?> makeBrain(final Dynamic<?> input) {
      return GoatAi.makeBrain(this.brainProvider().makeBrain(input));
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.2F).add(Attributes.ATTACK_DAMAGE, 2.0);
   }

   @Override
   protected void ageBoundaryReached() {
      if (this.isBaby()) {
         this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
         this.removeHorns();
      } else {
         this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0);
         this.addHorns();
      }
   }

   @Override
   protected int calculateFallDamage(final double fallDistance, final float damageModifier) {
      return super.calculateFallDamage(fallDistance, damageModifier) - 10;
   }

   @Override
   protected SoundEvent getAmbientSound() {
      return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_AMBIENT : SoundEvents.GOAT_AMBIENT;
   }

   @Override
   protected SoundEvent getHurtSound(final DamageSource source) {
      return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_HURT : SoundEvents.GOAT_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_DEATH : SoundEvents.GOAT_DEATH;
   }

   @Override
   protected void playStepSound(final BlockPos pos, final BlockState blockState) {
      this.playSound(SoundEvents.GOAT_STEP, 0.15F, 1.0F);
   }

   protected SoundEvent getMilkingSound() {
      return this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_MILK : SoundEvents.GOAT_MILK;
   }

   public @Nullable Goat getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
      Goat newGoat = EntityType.GOAT.create(level, EntitySpawnReason.BREEDING);
      if (newGoat != null) {
         GoatAi.initMemories(newGoat, level.getRandom());
         boolean babyIsScreaming = (level.getRandom().nextBoolean() ? this : partner) instanceof Goat goat && goat.isScreamingGoat()
            || level.getRandom().nextDouble() < 0.02;
         newGoat.setScreamingGoat(babyIsScreaming);
      }

      return newGoat;
   }

   @Override
   public Brain<Goat> getBrain() {
      return (Brain<Goat>)super.getBrain();
   }

   @Override
   protected void customServerAiStep(final ServerLevel level) {
      ProfilerFiller profiler = Profiler.get();
      profiler.push("goatBrain");
      this.getBrain().tick(level, this);
      profiler.pop();
      profiler.push("goatActivityUpdate");
      GoatAi.updateActivity(this);
      profiler.pop();
      super.customServerAiStep(level);
   }

   @Override
   public int getMaxHeadYRot() {
      return 15;
   }

   @Override
   public void setYHeadRot(final float yHeadRot) {
      int maxHeadYRot = this.getMaxHeadYRot();
      float deltaFromBody = Mth.degreesDifference(this.yBodyRot, yHeadRot);
      float deltaFromBodyClamped = Mth.clamp(deltaFromBody, -maxHeadYRot, maxHeadYRot);
      super.setYHeadRot(this.yBodyRot + deltaFromBodyClamped);
   }

   @Override
   protected void playEatingSound() {
      this.level()
         .playSound(
            null,
            this,
            this.isScreamingGoat() ? SoundEvents.GOAT_SCREAMING_EAT : SoundEvents.GOAT_EAT,
            SoundSource.NEUTRAL,
            1.0F,
            Mth.randomBetween(this.level().random, 0.8F, 1.2F)
         );
   }

   @Override
   public boolean isFood(final ItemStack itemStack) {
      return itemStack.is(ItemTags.GOAT_FOOD);
   }

   @Override
   public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
      ItemStack heldItem = player.getItemInHand(hand);
      if (heldItem.is(Items.BUCKET) && !this.isBaby()) {
         player.playSound(this.getMilkingSound(), 1.0F, 1.0F);
         ItemStack bucketOrMilkBucket = ItemUtils.createFilledResult(heldItem, player, Items.MILK_BUCKET.getDefaultInstance());
         player.setItemInHand(hand, bucketOrMilkBucket);
         return InteractionResult.SUCCESS;
      }

      InteractionResult interactionResult = super.mobInteract(player, hand);
      if (interactionResult.consumesAction() && this.isFood(heldItem)) {
         this.playEatingSound();
      }

      return interactionResult;
   }

   @Override
   public SpawnGroupData finalizeSpawn(
      final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, final @Nullable SpawnGroupData groupData
   ) {
      RandomSource random = level.getRandom();
      GoatAi.initMemories(this, random);
      this.setScreamingGoat(random.nextDouble() < 0.02);
      this.ageBoundaryReached();
      if (!this.isBaby() && random.nextFloat() < 0.1F) {
         EntityDataAccessor<Boolean> hornToRemove = random.nextBoolean() ? DATA_HAS_LEFT_HORN : DATA_HAS_RIGHT_HORN;
         this.entityData.set(hornToRemove, false);
      }

      return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
   }

   @Override
   public EntityDimensions getDefaultDimensions(final Pose pose) {
      return pose == Pose.LONG_JUMPING ? LONG_JUMPING_DIMENSIONS.scale(this.getAgeScale()) : super.getDefaultDimensions(pose);
   }

   @Override
   protected void addAdditionalSaveData(final ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.putBoolean("IsScreamingGoat", this.isScreamingGoat());
      output.putBoolean("HasLeftHorn", this.hasLeftHorn());
      output.putBoolean("HasRightHorn", this.hasRightHorn());
   }

   @Override
   protected void readAdditionalSaveData(final ValueInput input) {
      super.readAdditionalSaveData(input);
      this.setScreamingGoat(input.getBooleanOr("IsScreamingGoat", false));
      this.entityData.set(DATA_HAS_LEFT_HORN, input.getBooleanOr("HasLeftHorn", true));
      this.entityData.set(DATA_HAS_RIGHT_HORN, input.getBooleanOr("HasRightHorn", true));
   }

   @Override
   public void handleEntityEvent(final byte id) {
      if (id == 58) {
         this.isLoweringHead = true;
      } else if (id == 59) {
         this.isLoweringHead = false;
      } else {
         super.handleEntityEvent(id);
      }
   }

   @Override
   public void aiStep() {
      if (this.isLoweringHead) {
         this.lowerHeadTick++;
      } else {
         this.lowerHeadTick -= 2;
      }

      this.lowerHeadTick = Mth.clamp(this.lowerHeadTick, 0, 20);
      super.aiStep();
   }

   @Override
   protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
      super.defineSynchedData(entityData);
      entityData.define(DATA_IS_SCREAMING_GOAT, false);
      entityData.define(DATA_HAS_LEFT_HORN, true);
      entityData.define(DATA_HAS_RIGHT_HORN, true);
   }

   public boolean hasLeftHorn() {
      return this.entityData.get(DATA_HAS_LEFT_HORN);
   }

   public boolean hasRightHorn() {
      return this.entityData.get(DATA_HAS_RIGHT_HORN);
   }

   public boolean dropHorn() {
      boolean hasLeft = this.hasLeftHorn();
      boolean hasRight = this.hasRightHorn();
      if (!hasLeft && !hasRight) {
         return false;
      }

      EntityDataAccessor<Boolean> hornToDrop;
      if (!hasLeft) {
         hornToDrop = DATA_HAS_RIGHT_HORN;
      } else if (!hasRight) {
         hornToDrop = DATA_HAS_LEFT_HORN;
      } else {
         hornToDrop = this.random.nextBoolean() ? DATA_HAS_LEFT_HORN : DATA_HAS_RIGHT_HORN;
      }

      this.entityData.set(hornToDrop, false);
      Vec3 bodyPosition = this.position();
      ItemStack item = this.createHorn();
      double deltaX = Mth.randomBetween(this.random, -0.2F, 0.2F);
      double deltaY = Mth.randomBetween(this.random, 0.3F, 0.7F);
      double deltaZ = Mth.randomBetween(this.random, -0.2F, 0.2F);
      ItemEntity itemEntity = new ItemEntity(this.level(), bodyPosition.x(), bodyPosition.y(), bodyPosition.z(), item, deltaX, deltaY, deltaZ);
      this.level().addFreshEntity(itemEntity);
      return true;
   }

   public void addHorns() {
      this.entityData.set(DATA_HAS_LEFT_HORN, true);
      this.entityData.set(DATA_HAS_RIGHT_HORN, true);
   }

   public void removeHorns() {
      this.entityData.set(DATA_HAS_LEFT_HORN, false);
      this.entityData.set(DATA_HAS_RIGHT_HORN, false);
   }

   public boolean isScreamingGoat() {
      return this.entityData.get(DATA_IS_SCREAMING_GOAT);
   }

   public void setScreamingGoat(final boolean isScreamingGoat) {
      this.entityData.set(DATA_IS_SCREAMING_GOAT, isScreamingGoat);
   }

   public float getRammingXHeadRot() {
      return this.lowerHeadTick / 20.0F * 30.0F * (float) (Math.PI / 180.0);
   }

   public static boolean checkGoatSpawnRules(
      final EntityType<? extends Animal> type, final LevelAccessor level, final EntitySpawnReason spawnReason, final BlockPos pos, final RandomSource random
   ) {
      return level.getBlockState(pos.below()).is(BlockTags.GOATS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
   }
}
