package net.minecraft.world.entity.npc.villager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Villager extends AbstractVillager implements VillagerDataHolder, ReputationEventHandler {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(Villager.class, EntityDataSerializers.VILLAGER_DATA);
   public static final int BREEDING_FOOD_THRESHOLD = 12;
   public static final Map<Item, Integer> FOOD_POINTS = ImmutableMap.of(Items.BREAD, 4, Items.POTATO, 1, Items.CARROT, 1, Items.BEETROOT, 1);
   private static final int TRADES_PER_LEVEL = 2;
   private static final int MAX_GOSSIP_TOPICS = 10;
   private static final int GOSSIP_COOLDOWN = 1200;
   private static final int GOSSIP_DECAY_INTERVAL = 24000;
   private static final int HOW_FAR_AWAY_TO_TALK_TO_OTHER_VILLAGERS_ABOUT_GOLEMS = 10;
   private static final int HOW_MANY_VILLAGERS_NEED_TO_AGREE_TO_SPAWN_A_GOLEM = 5;
   private static final long TIME_SINCE_SLEEPING_FOR_GOLEM_SPAWNING = 24000L;
   @VisibleForTesting
   public static final float SPEED_MODIFIER = 0.5F;
   private static final int DEFAULT_XP = 0;
   private static final byte DEFAULT_FOOD_LEVEL = 0;
   private static final int DEFAULT_LAST_RESTOCK = 0;
   private static final int DEFAULT_LAST_GOSSIP_DECAY = 0;
   private static final int DEFAULT_RESTOCKS_TODAY = 0;
   private static final boolean DEFAULT_ASSIGN_PROFESSION_WHEN_SPAWNED = false;
   private int updateMerchantTimer;
   private boolean increaseProfessionLevelOnUpdate;
   private @Nullable Player lastTradedPlayer;
   private boolean chasing;
   private int foodLevel = 0;
   private final GossipContainer gossips = new GossipContainer();
   private long lastGossipTime;
   private long lastGossipDecayTime = 0L;
   private int villagerXp = 0;
   private long lastRestockGameTime = 0L;
   private int numberOfRestocksToday = 0;
   private long lastRestockCheckDay;
   private boolean assignProfessionWhenSpawned = false;
   private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
      MemoryModuleType.HOME,
      MemoryModuleType.JOB_SITE,
      MemoryModuleType.POTENTIAL_JOB_SITE,
      MemoryModuleType.MEETING_POINT,
      MemoryModuleType.NEAREST_LIVING_ENTITIES,
      MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
      MemoryModuleType.VISIBLE_VILLAGER_BABIES,
      MemoryModuleType.NEAREST_PLAYERS,
      MemoryModuleType.NEAREST_VISIBLE_PLAYER,
      MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
      MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
      MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
      new MemoryModuleType[]{
         MemoryModuleType.WALK_TARGET,
         MemoryModuleType.LOOK_TARGET,
         MemoryModuleType.INTERACTION_TARGET,
         MemoryModuleType.BREED_TARGET,
         MemoryModuleType.PATH,
         MemoryModuleType.DOORS_TO_CLOSE,
         MemoryModuleType.NEAREST_BED,
         MemoryModuleType.HURT_BY,
         MemoryModuleType.HURT_BY_ENTITY,
         MemoryModuleType.NEAREST_HOSTILE,
         MemoryModuleType.SECONDARY_JOB_SITE,
         MemoryModuleType.HIDING_PLACE,
         MemoryModuleType.HEARD_BELL_TIME,
         MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
         MemoryModuleType.LAST_SLEPT,
         MemoryModuleType.LAST_WOKEN,
         MemoryModuleType.LAST_WORKED_AT_POI,
         MemoryModuleType.GOLEM_DETECTED_RECENTLY
      }
   );
   private static final ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSOR_TYPES = ImmutableList.of(
      SensorType.NEAREST_LIVING_ENTITIES,
      SensorType.NEAREST_PLAYERS,
      SensorType.NEAREST_ITEMS,
      SensorType.NEAREST_BED,
      SensorType.HURT_BY,
      SensorType.VILLAGER_HOSTILES,
      SensorType.VILLAGER_BABIES,
      SensorType.SECONDARY_POIS,
      SensorType.GOLEM_DETECTED
   );
   public static final Map<MemoryModuleType<GlobalPos>, BiPredicate<Villager, Holder<PoiType>>> POI_MEMORIES = ImmutableMap.of(
      MemoryModuleType.HOME,
      (BiPredicate<Villager, Holder>)(villager, poiType) -> poiType.is(PoiTypes.HOME),
      MemoryModuleType.JOB_SITE,
      (BiPredicate<Villager, Holder>)(villager, poiType) -> villager.getVillagerData().profession().value().heldJobSite().test(poiType),
      MemoryModuleType.POTENTIAL_JOB_SITE,
      (BiPredicate<Villager, Holder>)(villager, poiType) -> VillagerProfession.ALL_ACQUIRABLE_JOBS.test(poiType),
      MemoryModuleType.MEETING_POINT,
      (BiPredicate<Villager, Holder>)(villager, poiType) -> poiType.is(PoiTypes.MEETING)
   );

   public Villager(final EntityType<? extends Villager> type, final Level level) {
      this(type, level, VillagerType.PLAINS);
   }

   public Villager(final EntityType<? extends Villager> entityType, final Level level, final ResourceKey<VillagerType> type) {
      this(entityType, level, level.registryAccess().getOrThrow(type));
   }

   public Villager(final EntityType<? extends Villager> entityType, final Level level, final Holder<VillagerType> type) {
      super(entityType, level);
      this.getNavigation().setCanOpenDoors(true);
      this.getNavigation().setCanFloat(true);
      this.getNavigation().setRequiredPathLength(48.0F);
      this.setCanPickUpLoot(true);
      this.setVillagerData(this.getVillagerData().withType(type).withProfession(level.registryAccess(), VillagerProfession.NONE));
   }

   @Override
   public Brain<Villager> getBrain() {
      return (Brain<Villager>)super.getBrain();
   }

   @Override
   protected Brain.Provider<Villager> brainProvider() {
      return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
   }

   @Override
   protected Brain<?> makeBrain(final Dynamic<?> input) {
      Brain<Villager> brain = this.brainProvider().makeBrain(input);
      this.registerBrainGoals(brain);
      return brain;
   }

   public void refreshBrain(final ServerLevel level) {
      Brain<Villager> oldBrain = this.getBrain();
      oldBrain.stopAll(level, this);
      this.brain = oldBrain.copyWithoutBehaviors();
      this.registerBrainGoals(this.getBrain());
   }

   private void registerBrainGoals(final Brain<Villager> brain) {
      Holder<VillagerProfession> profession = this.getVillagerData().profession();
      if (this.isBaby()) {
         brain.setSchedule(EnvironmentAttributes.BABY_VILLAGER_ACTIVITY);
         brain.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(0.5F));
      } else {
         brain.setSchedule(EnvironmentAttributes.VILLAGER_ACTIVITY);
         brain.addActivityWithConditions(
            Activity.WORK,
            VillagerGoalPackages.getWorkPackage(profession, 0.5F),
            ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT))
         );
      }

      brain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(profession, 0.5F));
      brain.addActivityWithConditions(
         Activity.MEET,
         VillagerGoalPackages.getMeetPackage(profession, 0.5F),
         ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT))
      );
      brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(profession, 0.5F));
      brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(profession, 0.5F));
      brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(profession, 0.5F));
      brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(profession, 0.5F));
      brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(profession, 0.5F));
      brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(profession, 0.5F));
      brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
      brain.setDefaultActivity(Activity.IDLE);
      brain.setActiveActivityIfPossible(Activity.IDLE);
      brain.updateActivityFromSchedule(this.level().environmentAttributes(), this.level().getGameTime(), this.position());
   }

   @Override
   protected void ageBoundaryReached() {
      super.ageBoundaryReached();
      if (this.level() instanceof ServerLevel) {
         this.refreshBrain((ServerLevel)this.level());
      }
   }

   public static AttributeSupplier.Builder createAttributes() {
      return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.5);
   }

   public boolean assignProfessionWhenSpawned() {
      return this.assignProfessionWhenSpawned;
   }

   @Override
   protected void customServerAiStep(final ServerLevel level) {
      ProfilerFiller profiler = Profiler.get();
      profiler.push("villagerBrain");
      this.getBrain().tick(level, this);
      profiler.pop();
      if (this.assignProfessionWhenSpawned) {
         this.assignProfessionWhenSpawned = false;
      }

      if (!this.isTrading() && this.updateMerchantTimer > 0) {
         this.updateMerchantTimer--;
         if (this.updateMerchantTimer <= 0) {
            if (this.increaseProfessionLevelOnUpdate) {
               this.increaseMerchantCareer(level);
               this.increaseProfessionLevelOnUpdate = false;
            }

            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
         }
      }

      if (this.lastTradedPlayer != null) {
         level.onReputationEvent(ReputationEventType.TRADE, this.lastTradedPlayer, this);
         level.broadcastEntityEvent(this, (byte)14);
         this.lastTradedPlayer = null;
      }

      if (!this.isNoAi() && this.random.nextInt(100) == 0) {
         Raid raid = level.getRaidAt(this.blockPosition());
         if (raid != null && raid.isActive() && !raid.isOver()) {
            level.broadcastEntityEvent(this, (byte)42);
         }
      }

      if (this.getVillagerData().profession().is(VillagerProfession.NONE) && this.isTrading()) {
         this.stopTrading();
      }

      super.customServerAiStep(level);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.getUnhappyCounter() > 0) {
         this.setUnhappyCounter(this.getUnhappyCounter() - 1);
      }

      this.maybeDecayGossip();
   }

   @Override
   public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
      ItemStack itemStack = player.getItemInHand(hand);
      if (itemStack.is(Items.VILLAGER_SPAWN_EGG) || !this.isAlive() || this.isTrading() || this.isSleeping()) {
         return super.mobInteract(player, hand);
      }

      if (this.isBaby()) {
         this.setUnhappy();
         return InteractionResult.SUCCESS;
      }

      if (!this.level().isClientSide()) {
         boolean noOffers = this.getOffers().isEmpty();
         if (hand == InteractionHand.MAIN_HAND) {
            if (noOffers) {
               this.setUnhappy();
            }

            player.awardStat(Stats.TALKED_TO_VILLAGER);
         }

         if (noOffers) {
            return InteractionResult.CONSUME;
         }

         this.startTrading(player);
      }

      return InteractionResult.SUCCESS;
   }

   private void setUnhappy() {
      this.setUnhappyCounter(40);
      if (!this.level().isClientSide()) {
         this.makeSound(SoundEvents.VILLAGER_NO);
      }
   }

   private void startTrading(final Player player) {
      this.updateSpecialPrices(player);
      this.setTradingPlayer(player);
      this.openTradingScreen(player, this.getDisplayName(), this.getVillagerData().level());
   }

   @Override
   public void setTradingPlayer(final @Nullable Player player) {
      boolean shouldStop = this.getTradingPlayer() != null && player == null;
      super.setTradingPlayer(player);
      if (shouldStop) {
         this.stopTrading();
      }
   }

   @Override
   protected void stopTrading() {
      super.stopTrading();
      this.resetSpecialPrices();
   }

   private void resetSpecialPrices() {
      if (!this.level().isClientSide()) {
         for (MerchantOffer offer : this.getOffers()) {
            offer.resetSpecialPriceDiff();
         }
      }
   }

   @Override
   public boolean canRestock() {
      return true;
   }

   public void restock() {
      this.updateDemand();

      for (MerchantOffer offer : this.getOffers()) {
         offer.resetUses();
      }

      this.resendOffersToTradingPlayer();
      this.lastRestockGameTime = this.level().getGameTime();
      this.numberOfRestocksToday++;
   }

   private void resendOffersToTradingPlayer() {
      MerchantOffers offers = this.getOffers();
      Player tradingPlayer = this.getTradingPlayer();
      if (tradingPlayer != null && !offers.isEmpty()) {
         tradingPlayer.sendMerchantOffers(
            tradingPlayer.containerMenu.containerId, offers, this.getVillagerData().level(), this.getVillagerXp(), this.showProgressBar(), this.canRestock()
         );
      }
   }

   private boolean needsToRestock() {
      for (MerchantOffer offer : this.getOffers()) {
         if (offer.needsRestock()) {
            return true;
         }
      }

      return false;
   }

   private boolean allowedToRestock() {
      return this.numberOfRestocksToday == 0 || this.numberOfRestocksToday < 2 && this.level().getGameTime() > this.lastRestockGameTime + 2400L;
   }

   public boolean shouldRestock(final ServerLevel level) {
      long halfDayPassedTime = this.lastRestockGameTime + 12000L;
      long gameTime = this.level().getGameTime();
      boolean isNewDay = gameTime > halfDayPassedTime;
      long currentDay = level.getDayCount();
      isNewDay |= this.lastRestockCheckDay > 0L && currentDay > this.lastRestockCheckDay;
      this.lastRestockCheckDay = currentDay;
      if (isNewDay) {
         this.lastRestockGameTime = gameTime;
         this.resetNumberOfRestocks();
      }

      return this.allowedToRestock() && this.needsToRestock();
   }

   private void catchUpDemand() {
      int missedUpdates = 2 - this.numberOfRestocksToday;
      if (missedUpdates > 0) {
         for (MerchantOffer offer : this.getOffers()) {
            offer.resetUses();
         }
      }

      for (int i = 0; i < missedUpdates; i++) {
         this.updateDemand();
      }

      this.resendOffersToTradingPlayer();
   }

   private void updateDemand() {
      for (MerchantOffer offer : this.getOffers()) {
         offer.updateDemand();
      }
   }

   private void updateSpecialPrices(final Player player) {
      int reputation = this.getPlayerReputation(player);
      if (reputation != 0) {
         for (MerchantOffer offer : this.getOffers()) {
            offer.addToSpecialPriceDiff(-Mth.floor(reputation * offer.getPriceMultiplier()));
         }
      }

      if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
         MobEffectInstance effect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
         int amplifier = effect.getAmplifier();

         for (MerchantOffer offer : this.getOffers()) {
            double modifier = 0.3 + 0.0625 * amplifier;
            int costReduction = (int)Math.floor(modifier * offer.getBaseCostA().getCount());
            offer.addToSpecialPriceDiff(-Math.max(costReduction, 1));
         }
      }
   }

   @Override
   protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
      super.defineSynchedData(entityData);
      entityData.define(DATA_VILLAGER_DATA, createDefaultVillagerData());
   }

   public static VillagerData createDefaultVillagerData() {
      return new VillagerData(
         BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE), 1
      );
   }

   @Override
   protected void addAdditionalSaveData(final ValueOutput output) {
      super.addAdditionalSaveData(output);
      output.store("VillagerData", VillagerData.CODEC, this.getVillagerData());
      output.putByte("FoodLevel", (byte)this.foodLevel);
      output.store("Gossips", GossipContainer.CODEC, this.gossips);
      output.putInt("Xp", this.villagerXp);
      output.putLong("LastRestock", this.lastRestockGameTime);
      output.putLong("LastGossipDecay", this.lastGossipDecayTime);
      output.putInt("RestocksToday", this.numberOfRestocksToday);
      if (this.assignProfessionWhenSpawned) {
         output.putBoolean("AssignProfessionWhenSpawned", true);
      }
   }

   @Override
   protected void readAdditionalSaveData(final ValueInput input) {
      super.readAdditionalSaveData(input);
      this.entityData.set(DATA_VILLAGER_DATA, input.<VillagerData>read("VillagerData", VillagerData.CODEC).orElseGet(Villager::createDefaultVillagerData));
      this.foodLevel = input.getByteOr("FoodLevel", (byte)0);
      this.gossips.clear();
      input.<GossipContainer>read("Gossips", GossipContainer.CODEC).ifPresent(this.gossips::putAll);
      this.villagerXp = input.getIntOr("Xp", 0);
      this.lastRestockGameTime = input.getLongOr("LastRestock", 0L);
      this.lastGossipDecayTime = input.getLongOr("LastGossipDecay", 0L);
      if (this.level() instanceof ServerLevel) {
         this.refreshBrain((ServerLevel)this.level());
      }

      this.numberOfRestocksToday = input.getIntOr("RestocksToday", 0);
      this.assignProfessionWhenSpawned = input.getBooleanOr("AssignProfessionWhenSpawned", false);
   }

   @Override
   public boolean removeWhenFarAway(final double distSqr) {
      return false;
   }

   @Override
   protected @Nullable SoundEvent getAmbientSound() {
      if (this.isSleeping()) {
         return null;
      } else {
         return this.isTrading() ? SoundEvents.VILLAGER_TRADE : SoundEvents.VILLAGER_AMBIENT;
      }
   }

   @Override
   protected SoundEvent getHurtSound(final DamageSource source) {
      return SoundEvents.VILLAGER_HURT;
   }

   @Override
   protected SoundEvent getDeathSound() {
      return SoundEvents.VILLAGER_DEATH;
   }

   public void playWorkSound() {
      this.makeSound(this.getVillagerData().profession().value().workSound());
   }

   @Override
   public void setVillagerData(final VillagerData data) {
      VillagerData currentData = this.getVillagerData();
      if (!currentData.profession().equals(data.profession())) {
         this.offers = null;
      }

      this.entityData.set(DATA_VILLAGER_DATA, data);
   }

   @Override
   public VillagerData getVillagerData() {
      return this.entityData.get(DATA_VILLAGER_DATA);
   }

   @Override
   protected void rewardTradeXp(final MerchantOffer offer) {
      int popXp = 3 + this.random.nextInt(4);
      this.villagerXp = this.villagerXp + offer.getXp();
      this.lastTradedPlayer = this.getTradingPlayer();
      if (this.shouldIncreaseLevel()) {
         this.updateMerchantTimer = 40;
         this.increaseProfessionLevelOnUpdate = true;
         popXp += 5;
      }

      if (offer.shouldRewardExp()) {
         this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), popXp));
      }
   }

   @Override
   public void setLastHurtByMob(final @Nullable LivingEntity hurtBy) {
      if (hurtBy != null && this.level() instanceof ServerLevel) {
         ((ServerLevel)this.level()).onReputationEvent(ReputationEventType.VILLAGER_HURT, hurtBy, this);
         if (this.isAlive() && hurtBy instanceof Player) {
            this.level().broadcastEntityEvent(this, (byte)13);
         }
      }

      super.setLastHurtByMob(hurtBy);
   }

   @Override
   public void die(final DamageSource source) {
      LOGGER.info("Villager {} died, message: '{}'", this, source.getLocalizedDeathMessage(this).getString());
      Entity murderer = source.getEntity();
      if (murderer != null) {
         this.tellWitnessesThatIWasMurdered(murderer);
      }

      this.releaseAllPois();
      super.die(source);
   }

   private void releaseAllPois() {
      this.releasePoi(MemoryModuleType.HOME);
      this.releasePoi(MemoryModuleType.JOB_SITE);
      this.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
      this.releasePoi(MemoryModuleType.MEETING_POINT);
   }

   private void tellWitnessesThatIWasMurdered(final Entity murderer) {
      if (this.level() instanceof ServerLevel serverLevel) {
         Optional<NearestVisibleLivingEntities> witnesses = this.brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
         if (!witnesses.isEmpty()) {
            witnesses.get()
               .findAll(ReputationEventHandler.class::isInstance)
               .forEach(witness -> serverLevel.onReputationEvent(ReputationEventType.VILLAGER_KILLED, murderer, (ReputationEventHandler)witness));
         }
      }
   }

   public void releasePoi(final MemoryModuleType<GlobalPos> memoryType) {
      if (this.level() instanceof ServerLevel) {
         MinecraftServer server = ((ServerLevel)this.level()).getServer();
         this.brain.getMemory(memoryType).ifPresent(memory -> {
            ServerLevel poiLevel = server.getLevel(memory.dimension());
            if (poiLevel != null) {
               PoiManager poiManager = poiLevel.getPoiManager();
               Optional<Holder<PoiType>> type = poiManager.getType(memory.pos());
               BiPredicate<Villager, Holder<PoiType>> poiTypePredicate = POI_MEMORIES.get(memoryType);
               if (type.isPresent() && poiTypePredicate.test(this, type.get())) {
                  poiManager.release(memory.pos());
                  poiLevel.debugSynchronizers().updatePoi(memory.pos());
               }
            }
         });
      }
   }

   @Override
   public boolean canBreed() {
      return this.foodLevel + this.countFoodPointsInInventory() >= 12 && !this.isSleeping() && this.getAge() == 0;
   }

   private boolean hungry() {
      return this.foodLevel < 12;
   }

   private void eatUntilFull() {
      if (this.hungry() && this.countFoodPointsInInventory() != 0) {
         for (int slot = 0; slot < this.getInventory().getContainerSize(); slot++) {
            ItemStack itemStack = this.getInventory().getItem(slot);
            if (!itemStack.isEmpty()) {
               Integer value = FOOD_POINTS.get(itemStack.getItem());
               if (value != null) {
                  int itemCount = itemStack.getCount();

                  for (int count = itemCount; count > 0; count--) {
                     this.foodLevel = this.foodLevel + value;
                     this.getInventory().removeItem(slot, 1);
                     if (!this.hungry()) {
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   public int getPlayerReputation(final Player player) {
      return this.gossips.getReputation(player.getUUID(), t -> true);
   }

   private void digestFood(final int amount) {
      this.foodLevel -= amount;
   }

   public void eatAndDigestFood() {
      this.eatUntilFull();
      this.digestFood(12);
   }

   public void setOffers(final MerchantOffers offers) {
      this.offers = offers;
   }

   private boolean shouldIncreaseLevel() {
      int currentLevel = this.getVillagerData().level();
      return VillagerData.canLevelUp(currentLevel) && this.villagerXp >= VillagerData.getMaxXpPerLevel(currentLevel);
   }

   private void increaseMerchantCareer(final ServerLevel level) {
      this.setVillagerData(this.getVillagerData().withLevel(this.getVillagerData().level() + 1));
      this.updateTrades(level);
   }

   @Override
   protected Component getTypeName() {
      return this.getVillagerData().profession().value().name();
   }

   @Override
   public void handleEntityEvent(final byte id) {
      if (id == 12) {
         this.addParticlesAroundSelf(ParticleTypes.HEART);
      } else if (id == 13) {
         this.addParticlesAroundSelf(ParticleTypes.ANGRY_VILLAGER);
      } else if (id == 14) {
         this.addParticlesAroundSelf(ParticleTypes.HAPPY_VILLAGER);
      } else if (id == 42) {
         this.addParticlesAroundSelf(ParticleTypes.SPLASH);
      } else {
         super.handleEntityEvent(id);
      }
   }

   @Override
   public @Nullable SpawnGroupData finalizeSpawn(
      final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, final @Nullable SpawnGroupData groupData
   ) {
      if (spawnReason == EntitySpawnReason.BREEDING) {
         this.setVillagerData(this.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE));
      }

      if (spawnReason == EntitySpawnReason.COMMAND
         || spawnReason == EntitySpawnReason.SPAWN_ITEM_USE
         || EntitySpawnReason.isSpawner(spawnReason)
         || spawnReason == EntitySpawnReason.DISPENSER) {
         this.setVillagerData(this.getVillagerData().withType(level.registryAccess(), VillagerType.byBiome(level.getBiome(this.blockPosition()))));
      }

      if (spawnReason == EntitySpawnReason.STRUCTURE) {
         this.assignProfessionWhenSpawned = true;
      }

      return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
   }

   public @Nullable Villager getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
      double random = this.random.nextDouble();
      Holder<VillagerType> type;
      if (random < 0.5) {
         type = level.registryAccess().getOrThrow(VillagerType.byBiome(level.getBiome(this.blockPosition())));
      } else if (random < 0.75) {
         type = this.getVillagerData().type();
      } else {
         type = ((Villager)partner).getVillagerData().type();
      }

      Villager villager = new Villager(EntityType.VILLAGER, level, type);
      villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), EntitySpawnReason.BREEDING, null);
      return villager;
   }

   @Override
   public void thunderHit(final ServerLevel level, final LightningBolt lightningBolt) {
      if (level.getDifficulty() != Difficulty.PEACEFUL) {
         LOGGER.info("Villager {} was struck by lightning {}.", this, lightningBolt);
         Witch witch = this.convertTo(EntityType.WITCH, ConversionParams.single(this, false, false), w -> {
            w.finalizeSpawn(level, level.getCurrentDifficultyAt(w.blockPosition()), EntitySpawnReason.CONVERSION, null);
            w.setPersistenceRequired();
            this.releaseAllPois();
         });
         if (witch == null) {
            super.thunderHit(level, lightningBolt);
         }
      } else {
         super.thunderHit(level, lightningBolt);
      }
   }

   @Override
   protected void pickUpItem(final ServerLevel level, final ItemEntity entity) {
      InventoryCarrier.pickUpItem(level, this, this, entity);
   }

   @Override
   public boolean wantsToPickUp(final ServerLevel level, final ItemStack itemStack) {
      Item item = itemStack.getItem();
      return (itemStack.is(ItemTags.VILLAGER_PICKS_UP) || this.getVillagerData().profession().value().requestedItems().contains(item))
         && this.getInventory().canAddItem(itemStack);
   }

   public boolean hasExcessFood() {
      return this.countFoodPointsInInventory() >= 24;
   }

   public boolean wantsMoreFood() {
      return this.countFoodPointsInInventory() < 12;
   }

   private int countFoodPointsInInventory() {
      SimpleContainer inventory = this.getInventory();
      return FOOD_POINTS.entrySet().stream().mapToInt(entry -> inventory.countItem(entry.getKey()) * entry.getValue()).sum();
   }

   public boolean hasFarmSeeds() {
      return this.getInventory().hasAnyMatching(item -> item.is(ItemTags.VILLAGER_PLANTABLE_SEEDS));
   }

   @Override
   protected void updateTrades(final ServerLevel level) {
      VillagerData data = this.getVillagerData();
      ResourceKey<VillagerProfession> profession = data.profession().unwrapKey().orElse(null);
      if (profession != null) {
         Int2ObjectMap<VillagerTrades.ItemListing[]> tradesByLevel;
         if (this.level().enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> experimentalTrades = VillagerTrades.EXPERIMENTAL_TRADES.get(profession);
            tradesByLevel = experimentalTrades != null ? experimentalTrades : VillagerTrades.TRADES.get(profession);
         } else {
            tradesByLevel = VillagerTrades.TRADES.get(profession);
         }

         if (tradesByLevel != null && !tradesByLevel.isEmpty()) {
            VillagerTrades.ItemListing[] itemListings = (VillagerTrades.ItemListing[])tradesByLevel.get(data.level());
            if (itemListings != null) {
               MerchantOffers offers = this.getOffers();
               this.addOffersFromItemListings(level, offers, itemListings, 2);
               if (SharedConstants.DEBUG_UNLOCK_ALL_TRADES && data.level() < tradesByLevel.size()) {
                  this.increaseMerchantCareer(level);
               }
            }
         }
      }
   }

   public void gossip(final ServerLevel level, final Villager target, final long timestamp) {
      if ((timestamp < this.lastGossipTime || timestamp >= this.lastGossipTime + 1200L)
         && (timestamp < target.lastGossipTime || timestamp >= target.lastGossipTime + 1200L)) {
         this.gossips.transferFrom(target.gossips, this.random, 10);
         this.lastGossipTime = timestamp;
         target.lastGossipTime = timestamp;
         this.spawnGolemIfNeeded(level, timestamp, 5);
      }
   }

   private void maybeDecayGossip() {
      long timestamp = this.level().getGameTime();
      if (this.lastGossipDecayTime == 0L) {
         this.lastGossipDecayTime = timestamp;
      } else if (timestamp >= this.lastGossipDecayTime + 24000L) {
         this.gossips.decay();
         this.lastGossipDecayTime = timestamp;
      }
   }

   public void spawnGolemIfNeeded(final ServerLevel level, final long timestamp, final int villagersNeededToAgree) {
      if (this.wantsToSpawnGolem(timestamp)) {
         AABB villagerSearchBox = this.getBoundingBox().inflate(10.0, 10.0, 10.0);
         List<Villager> nearbyVillagers = level.getEntitiesOfClass(Villager.class, villagerSearchBox);
         List<Villager> nearbyVillagersThatWantAGolem = nearbyVillagers.stream().filter(villager -> villager.wantsToSpawnGolem(timestamp)).limit(5L).toList();
         if (nearbyVillagersThatWantAGolem.size() >= villagersNeededToAgree) {
            if (!SpawnUtil.trySpawnMob(
                  EntityType.IRON_GOLEM, EntitySpawnReason.MOB_SUMMONED, level, this.blockPosition(), 10, 8, 6, SpawnUtil.Strategy.LEGACY_IRON_GOLEM, false
               )
               .isEmpty()) {
               nearbyVillagers.forEach(GolemSensor::golemDetected);
            }
         }
      }
   }

   public boolean wantsToSpawnGolem(final long timestamp) {
      return !this.golemSpawnConditionsMet(this.level().getGameTime()) ? false : !this.brain.hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
   }

   @Override
   public void onReputationEventFrom(final ReputationEventType type, final Entity source) {
      if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
         this.gossips.add(source.getUUID(), GossipType.MAJOR_POSITIVE, 20);
         this.gossips.add(source.getUUID(), GossipType.MINOR_POSITIVE, 25);
      } else if (type == ReputationEventType.TRADE) {
         this.gossips.add(source.getUUID(), GossipType.TRADING, 2);
      } else if (type == ReputationEventType.VILLAGER_HURT) {
         this.gossips.add(source.getUUID(), GossipType.MINOR_NEGATIVE, 25);
      } else if (type == ReputationEventType.VILLAGER_KILLED) {
         this.gossips.add(source.getUUID(), GossipType.MAJOR_NEGATIVE, 25);
      }
   }

   @Override
   public int getVillagerXp() {
      return this.villagerXp;
   }

   public void setVillagerXp(final int value) {
      this.villagerXp = value;
   }

   private void resetNumberOfRestocks() {
      this.catchUpDemand();
      this.numberOfRestocksToday = 0;
   }

   public GossipContainer getGossips() {
      return this.gossips;
   }

   public void setGossips(final GossipContainer gossips) {
      this.gossips.putAll(gossips);
   }

   @Override
   public void startSleeping(final BlockPos bedPosition) {
      super.startSleeping(bedPosition);
      this.brain.setMemory(MemoryModuleType.LAST_SLEPT, this.level().getGameTime());
      this.brain.eraseMemory(MemoryModuleType.WALK_TARGET);
      this.brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
   }

   @Override
   public void stopSleeping() {
      super.stopSleeping();
      this.brain.setMemory(MemoryModuleType.LAST_WOKEN, this.level().getGameTime());
   }

   private boolean golemSpawnConditionsMet(final long gameTime) {
      Optional<Long> sleepMemory = this.brain.getMemory(MemoryModuleType.LAST_SLEPT);
      return sleepMemory.filter(aLong -> gameTime - aLong < 24000L).isPresent();
   }

   @Override
   public <T> @Nullable T get(final DataComponentType<? extends T> type) {
      return type == DataComponents.VILLAGER_VARIANT ? castComponentValue((DataComponentType<T>)type, this.getVillagerData().type()) : super.get(type);
   }

   @Override
   protected void applyImplicitComponents(final DataComponentGetter components) {
      this.applyImplicitComponentIfPresent(components, DataComponents.VILLAGER_VARIANT);
      super.applyImplicitComponents(components);
   }

   @Override
   protected <T> boolean applyImplicitComponent(final DataComponentType<T> type, final T value) {
      if (type == DataComponents.VILLAGER_VARIANT) {
         Holder<VillagerType> variant = castComponentValue(DataComponents.VILLAGER_VARIANT, value);
         this.setVillagerData(this.getVillagerData().withType(variant));
         return true;
      } else {
         return super.applyImplicitComponent(type, value);
      }
   }
}
