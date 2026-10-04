package net.minecraft.world.entity.animal.parrot;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowMobGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Parrot extends ShoulderRidingEntity implements FlyingAnimal {
   private static final EntityDataAccessor<Integer> DATA_VARIANT_ID = SynchedEntityData.defineId(Parrot.class, EntityDataSerializers.INT);
   private static final Predicate<Mob> NOT_PARROT_PREDICATE = new Predicate<Mob>() {
      public boolean test(final @Nullable Mob input) {
         return input != null && Parrot.MOB_SOUND_MAP.containsKey(input.getType());
      }
   };
   private static final Map<EntityType<?>, SoundEvent> MOB_SOUND_MAP = Util.make(Maps.newHashMap(), map -> {
      map.put(EntityType.BLAZE, SoundEvents.PARROT_IMITATE_BLAZE);
      map.put(EntityType.BOGGED, SoundEvents.PARROT_IMITATE_BOGGED);
      map.put(EntityType.BREEZE, SoundEvents.PARROT_IMITATE_BREEZE);
      map.put(EntityType.CAMEL_HUSK, SoundEvents.PARROT_IMITATE_CAMEL_HUSK);
      map.put(EntityType.CAVE_SPIDER, SoundEvents.PARROT_IMITATE_SPIDER);
      map.put(EntityType.CREAKING, SoundEvents.PARROT_IMITATE_CREAKING);
      map.put(EntityType.CREEPER, SoundEvents.PARROT_IMITATE_CREEPER);
      map.put(EntityType.DROWNED, SoundEvents.PARROT_IMITATE_DROWNED);
      map.put(EntityType.ELDER_GUARDIAN, SoundEvents.PARROT_IMITATE_ELDER_GUARDIAN);
      map.put(EntityType.ENDER_DRAGON, SoundEvents.PARROT_IMITATE_ENDER_DRAGON);
      map.put(EntityType.ENDERMITE, SoundEvents.PARROT_IMITATE_ENDERMITE);
      map.put(EntityType.EVOKER, SoundEvents.PARROT_IMITATE_EVOKER);
      map.put(EntityType.GHAST, SoundEvents.PARROT_IMITATE_GHAST);
      map.put(EntityType.HAPPY_GHAST, SoundEvents.EMPTY);
      map.put(EntityType.GUARDIAN, SoundEvents.PARROT_IMITATE_GUARDIAN);
      map.put(EntityType.HOGLIN, SoundEvents.PARROT_IMITATE_HOGLIN);
      map.put(EntityType.HUSK, SoundEvents.PARROT_IMITATE_HUSK);
      map.put(EntityType.ILLUSIONER, SoundEvents.PARROT_IMITATE_ILLUSIONER);
      map.put(EntityType.MAGMA_CUBE, SoundEvents.PARROT_IMITATE_MAGMA_CUBE);
      map.put(EntityType.PARCHED, SoundEvents.PARROT_IMITATE_PARCHED);
      map.put(EntityType.PHANTOM, SoundEvents.PARROT_IMITATE_PHANTOM);
      map.put(EntityType.PIGLIN, SoundEvents.PARROT_IMITATE_PIGLIN);
      map.put(EntityType.PIGLIN_BRUTE, SoundEvents.PARROT_IMITATE_PIGLIN_BRUTE);
      map.put(EntityType.PILLAGER, SoundEvents.PARROT_IMITATE_PILLAGER);
      map.put(EntityType.RAVAGER, SoundEvents.PARROT_IMITATE_RAVAGER);
      map.put(EntityType.SHULKER, SoundEvents.PARROT_IMITATE_SHULKER);
      map.put(EntityType.SILVERFISH, SoundEvents.PARROT_IMITATE_SILVERFISH);
      map.put(EntityType.SKELETON, SoundEvents.PARROT_IMITATE_SKELETON);
      map.put(EntityType.SLIME, SoundEvents.PARROT_IMITATE_SLIME);
      map.put(EntityType.SPIDER, SoundEvents.PARROT_IMITATE_SPIDER);
      map.put(EntityType.STRAY, SoundEvents.PARROT_IMITATE_STRAY);
      map.put(EntityType.VEX, SoundEvents.PARROT_IMITATE_VEX);
      map.put(EntityType.VINDICATOR, SoundEvents.PARROT_IMITATE_VINDICATOR);
      map.put(EntityType.WARDEN, SoundEvents.PARROT_IMITATE_WARDEN);
      map.put(EntityType.WITCH, SoundEvents.PARROT_IMITATE_WITCH);
      map.put(EntityType.WITHER, SoundEvents.PARROT_IMITATE_WITHER);
      map.put(EntityType.WITHER_SKELETON, SoundEvents.PARROT_IMITATE_WITHER_SKELETON);
      map.put(EntityType.ZOGLIN, SoundEvents.PARROT_IMITATE_ZOGLIN);
      map.put(EntityType.ZOMBIE, SoundEvents.PARROT_IMITATE_ZOMBIE);
      map.put(EntityType.ZOMBIE_HORSE, SoundEvents.PARROT_IMITATE_ZOMBIE_HORSE);
      map.put(EntityType.ZOMBIE_NAUTILUS, SoundEvents.PARROT_IMITATE_ZOMBIE_NAUTILUS);
      map.put(EntityType.ZOMBIE_VILLAGER, SoundEvents.PARROT_IMITATE_ZOMBIE_VILLAGER);
   });
   public float flap;
   public float flapSpeed;
   public float oFlapSpeed;
   public float oFlap;
   private float flapping = 1.0F;
   private float nextFlap = 1.0F;
   private boolean partyParrot;
   private @Nullable BlockPos jukebox;

   public Parrot(final EntityType<? extends Parrot> type, final Level level) {
      super(type, level);
      this.moveControl = new FlyingMoveControl(this, 10, false);
      this.setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
      this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
      this.setPathfindingMalus(PathType.COCOA, -1.0F);
   }

   @Override
   public @Nullable SpawnGroupData finalizeSpawn(
      final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData
   ) {
      this.setVariant(Util.getRandom(Parrot.Variant.values(), level.getRandom()));
      if (groupData == null) {
         groupData = new AgeableMob.AgeableMobGroupData(false);
      }

      return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
   }

   @Override
   public boolean isBaby() {
      return false;
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(0, new TamableAnimal.TamableAnimalPanicGoal(1.25));
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
      this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
      this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0, 5.0F, 1.0F));
      this.goalSelector.addGoal(2, new Parrot.ParrotWanderGoal(this, 1.0));
      this.goalSelector.addGoal(3, new LandOnOwnersShoulderGoal(this));
      this.goalSelector.addGoal(3, new FollowMobGoal(this, 1.0, 3.0F, 7.0F));
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Animal.createAnimalAttributes()
         .add(Attributes.MAX_HEALTH, 6.0)
         .add(Attributes.FLYING_SPEED, 0.4F)
         .add(Attributes.MOVEMENT_SPEED, 0.2F)
         .add(Attributes.ATTACK_DAMAGE, 3.0);
   }

   @Override
   protected PathNavigation createNavigation(final Level level) {
      FlyingPathNavigation flyingPathNavigation = new FlyingPathNavigation(this, level);
      flyingPathNavigation.setCanOpenDoors(false);
      flyingPathNavigation.setCanFloat(true);
      return flyingPathNavigation;
   }

   @Override
   public void aiStep() {
      if (this.jukebox == null || !this.jukebox.closerToCenterThan(this.position(), 3.46) || !this.level().getBlockState(this.jukebox).is(Blocks.JUKEBOX)) {
         this.partyParrot = false;
         this.jukebox = null;
      }

      if (this.level().random.nextInt(400) == 0) {
         imitateNearbyMobs(this.level(), this);
      }

      super.aiStep();
      this.calculateFlapping();
   }

   @Override
   public void setRecordPlayingNearby(final BlockPos jukebox, final boolean isPlaying) {
      this.jukebox = jukebox;
      this.partyParrot = isPlaying;
   }

   public boolean isPartyParrot() {
      return this.partyParrot;
   }

   private void calculateFlapping() {
      this.oFlap = this.flap;
      this.oFlapSpeed = this.flapSpeed;
      this.flapSpeed = this.flapSpeed + (!this.onGround() && !this.isPassenger() ? 4 : -1) * 0.3F;
      this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
      if (!this.onGround() && this.flapping < 1.0F) {
         this.flapping = 1.0F;
      }

      this.flapping *= 0.9F;
      Vec3 movement = this.getDeltaMovement();
      if (!this.onGround() && movement.y < 0.0) {
         this.setDeltaMovement(movement.multiply(1.0, 0.6, 1.0));
      }

      this.flap = this.flap + this.flapping * 2.0F;
   }

   public static boolean imitateNearbyMobs(final Level level, final Entity entity) {
      if (entity.isAlive() && !entity.isSilent() && level.random.nextInt(2) == 0) {
         List<Mob> mobs = level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(20.0), NOT_PARROT_PREDICATE);
         if (!mobs.isEmpty()) {
            Mob mob = mobs.get(level.random.nextInt(mobs.size()));
            if (!mob.isSilent()) {
               SoundEvent soundEvent = getImitatedSound(mob.getType());
               level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), soundEvent, entity.getSoundSource(), 0.7F, getPitch(level.random));
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @Override
   public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
      ItemStack itemStack = player.getItemInHand(hand);
      if (!this.isTame() && itemStack.is(ItemTags.PARROT_FOOD)) {
         this.usePlayerItem(player, hand, itemStack);
         if (!this.isSilent()) {
            this.level()
               .playSound(
                  null,
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  SoundEvents.PARROT_EAT,
                  this.getSoundSource(),
                  1.0F,
                  1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F
               );
         }

         if (!this.level().isClientSide()) {
            if (this.random.nextInt(10) == 0) {
               this.tame(player);
               this.level().broadcastEntityEvent(this, (byte)7);
            } else {
               this.level().broadcastEntityEvent(this, (byte)6);
            }
         }

         return InteractionResult.SUCCESS;
      } else if (!itemStack.is(ItemTags.PARROT_POISONOUS_FOOD)) {
         if (!this.isFlying() && this.isTame() && this.isOwnedBy(player)) {
            if (!this.level().isClientSide()) {
               this.setOrderedToSit(!this.isOrderedToSit());
            }

            return InteractionResult.SUCCESS;
         } else {
            return super.mobInteract(player, hand);
         }
      } else {
         this.usePlayerItem(player, hand, itemStack);
         this.addEffect(new MobEffectInstance(MobEffects.POISON, 900));
         if (player.isCreative() || !this.isInvulnerable()) {
            this.hurt(this.damageSources().playerAttack(player), Float.MAX_VALUE);
         }

         return InteractionResult.SUCCESS;
      }
   }

   @Override
   public boolean isFood(final ItemStack itemStack) {
      return false;
   }

   public static boolean checkParrotSpawnRules(
      final EntityType<Parrot> type, final LevelAccessor level, final EntitySpawnReason spawnReason, final BlockPos pos, final RandomSource random
   ) {
      return level.getBlockState(pos.below()).is(BlockTags.PARROTS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
   }

   @Override
   protected void checkFallDamage(final double ya, final boolean onGround, final BlockState onState, final BlockPos pos) {
   }

   @Override
   public boolean canMate(final Animal partner) {
      return false;
   }

   @Override
   public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
      return null;
   }

   @Override
   public @Nullable SoundEvent getAmbientSound() {
      return getAmbient(this.level(), this.level().random);
   }

   public static SoundEvent getAmbient(final Level level, final RandomSource random) {
      if (level.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(1000) == 0) {
         List<EntityType<?>> keys = Lists.newArrayList(MOB_SOUND_MAP.keySet());
         return getImitatedSound(keys.get(random.nextInt(keys.size())));
      } else {
         return SoundEvents.PARROT_AMBIENT;
      }
   }

   private static SoundEvent getImitatedSound(final EntityType<?> id) {
      return MOB_SOUND_MAP.getOrDefault(id, SoundEvents.PARROT_AMBIENT);
   }

   @Override
   protected SoundEvent getHurtSound(final DamageSource source) {
      return SoundEvents.PARROT_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.PARROT_DEATH;
   }

   @Override
   protected void playStepSound(final BlockPos pos, final BlockState blockState) {
      this.playSound(SoundEvents.PARROT_STEP, 0.15F, 1.0F);
   }

   @Override
   protected boolean isFlapping() {
      return this.flyDist > this.nextFlap;
   }

   @Override
   protected void onFlap() {
      this.playSound(SoundEvents.PARROT_FLY, 0.15F, 1.0F);
      this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
   }

   @Override
   public float getVoicePitch() {
      return getPitch(this.random);
   }

   public static float getPitch(final RandomSource random) {
      return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
   }

   @Override
   public SoundSource getSoundSource() {
      return SoundSource.NEUTRAL;
   }

   @Override
   public boolean isPushable() {
      return true;
   }

   @Override
   protected void doPush(final Entity entity) {
      if (!(entity instanceof Player)) {
         super.doPush(entity);
      }
   }

   @Override
   public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
      if (this.isInvulnerableTo(level, source)) {
         return false;
      }

      this.setOrderedToSit(false);
      return super.hurtServer(level, source, damage);
   }

   public Parrot.Variant getVariant() {
      return Parrot.Variant.byId(this.entityData.get(DATA_VARIANT_ID));
   }

   private void setVariant(final Parrot.Variant variant) {
      this.entityData.set(DATA_VARIANT_ID, variant.id);
   }

   @Override
   public <T> @Nullable T get(final DataComponentType<? extends T> type) {
      return type == DataComponents.PARROT_VARIANT ? castComponentValue((DataComponentType<T>)type, this.getVariant()) : super.get(type);
   }

   @Override
   protected void applyImplicitComponents(final DataComponentGetter components) {
      this.applyImplicitComponentIfPresent(components, DataComponents.PARROT_VARIANT);
      super.applyImplicitComponents(components);
   }

   @Override
   protected <T> boolean applyImplicitComponent(final DataComponentType<T> type, final T value) {
      if (type == DataComponents.PARROT_VARIANT) {
         this.setVariant(castComponentValue(DataComponents.PARROT_VARIANT, value));
         return true;
      } else {
         return super.applyImplicitComponent(type, value);
      }
   }

   @Override
   protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
      super.defineSynchedData(entityData);
      entityData.define(DATA_VARIANT_ID, Parrot.Variant.DEFAULT.id);
   }

   @Override
   protected void addAdditionalSaveData(final ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.store("Variant", Parrot.Variant.LEGACY_CODEC, this.getVariant());
   }

   @Override
   protected void readAdditionalSaveData(final ValueInput input) {
      super.readAdditionalSaveData(input);
      this.setVariant(input.<Parrot.Variant>read("Variant", Parrot.Variant.LEGACY_CODEC).orElse(Parrot.Variant.DEFAULT));
   }

   @Override
   public boolean isFlying() {
      return !this.onGround();
   }

   @Override
   protected boolean canFlyToOwner() {
      return true;
   }

   @Override
   public Vec3 getLeashOffset() {
      return new Vec3(0.0, 0.5F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
   }

   private static class ParrotWanderGoal extends WaterAvoidingRandomFlyingGoal {
      public ParrotWanderGoal(final PathfinderMob mob, final double speedModifier) {
         super(mob, speedModifier);
      }

      @Override
      protected @Nullable Vec3 getPosition() {
         Vec3 pos = null;
         if (this.mob.isInWater()) {
            pos = LandRandomPos.getPos(this.mob, 15, 15);
         }

         if (this.mob.getRandom().nextFloat() >= this.probability) {
            pos = this.getTreePos();
         }

         return pos == null ? super.getPosition() : pos;
      }

      private @Nullable Vec3 getTreePos() {
         BlockPos mobPos = this.mob.blockPosition();
         BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos();
         BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

         for (BlockPos pos : BlockPos.betweenClosed(
            Mth.floor(this.mob.getX() - 3.0),
            Mth.floor(this.mob.getY() - 6.0),
            Mth.floor(this.mob.getZ() - 3.0),
            Mth.floor(this.mob.getX() + 3.0),
            Mth.floor(this.mob.getY() + 6.0),
            Mth.floor(this.mob.getZ() + 3.0)
         )) {
            if (!mobPos.equals(pos)) {
               BlockState state = this.mob.level().getBlockState(belowPos.setWithOffset(pos, Direction.DOWN));
               boolean canSitOn = state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LOGS);
               if (canSitOn && this.mob.level().isEmptyBlock(pos) && this.mob.level().isEmptyBlock(abovePos.setWithOffset(pos, Direction.UP))) {
                  return Vec3.atBottomCenterOf(pos);
               }
            }
         }

         return null;
      }
   }

   public enum Variant implements StringRepresentable {
      RED_BLUE(0, "red_blue"),
      BLUE(1, "blue"),
      GREEN(2, "green"),
      YELLOW_BLUE(3, "yellow_blue"),
      GRAY(4, "gray");

      public static final Parrot.Variant DEFAULT = RED_BLUE;
      private static final IntFunction<Parrot.Variant> BY_ID = ByIdMap.continuous(Parrot.Variant::getId, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
      public static final Codec<Parrot.Variant> CODEC = StringRepresentable.fromEnum(Parrot.Variant::values);
      @Deprecated
      public static final Codec<Parrot.Variant> LEGACY_CODEC = Codec.INT.xmap(BY_ID::apply, Parrot.Variant::getId);
      public static final StreamCodec<ByteBuf, Parrot.Variant> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Parrot.Variant::getId);
      private final int id;
      private final String name;

      Variant(final int id, final String name) {
         this.id = id;
         this.name = name;
      }

      public int getId() {
         return this.id;
      }

      public static Parrot.Variant byId(final int id) {
         return BY_ID.apply(id);
      }

      @Override
      public String getSerializedName() {
         return this.name;
      }
   }
}
