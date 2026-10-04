package net.minecraft.world.entity.npc.villager;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractVillager extends AgeableMob implements Npc, Merchant, InventoryCarrier {
   private static final EntityDataAccessor<Integer> DATA_UNHAPPY_COUNTER = SynchedEntityData.defineId(AbstractVillager.class, EntityDataSerializers.INT);
   public static final int VILLAGER_SLOT_OFFSET = 300;
   private static final int VILLAGER_INVENTORY_SIZE = 8;
   private @Nullable Player tradingPlayer;
   protected @Nullable MerchantOffers offers;
   private final SimpleContainer inventory = new SimpleContainer(8);

   public AbstractVillager(final EntityType<? extends AbstractVillager> type, final Level level) {
      super(type, level);
      this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
      this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
   }

   @Override
   public @Nullable SpawnGroupData finalizeSpawn(
      final ServerLevelAccessor level, final DifficultyInstance difficulty, final EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData
   ) {
      if (groupData == null) {
         groupData = new AgeableMob.AgeableMobGroupData(false);
      }

      return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
   }

   public int getUnhappyCounter() {
      return this.entityData.get(DATA_UNHAPPY_COUNTER);
   }

   public void setUnhappyCounter(final int value) {
      this.entityData.set(DATA_UNHAPPY_COUNTER, value);
   }

   @Override
   public int getVillagerXp() {
      return 0;
   }

   @Override
   protected void defineSynchedData(final SynchedEntityData.Builder entityData) {
      super.defineSynchedData(entityData);
      entityData.define(DATA_UNHAPPY_COUNTER, 0);
   }

   @Override
   public void setTradingPlayer(final @Nullable Player player) {
      this.tradingPlayer = player;
   }

   @Override
   public @Nullable Player getTradingPlayer() {
      return this.tradingPlayer;
   }

   public boolean isTrading() {
      return this.tradingPlayer != null;
   }

   @Override
   public MerchantOffers getOffers() {
      if (this.level() instanceof ServerLevel serverLevel) {
         if (this.offers == null) {
            this.offers = new MerchantOffers();
            this.updateTrades(serverLevel);
         }

         return this.offers;
      } else {
         throw new IllegalStateException("Cannot load Villager offers on the client");
      }
   }

   @Override
   public void overrideOffers(final @Nullable MerchantOffers offers) {
   }

   @Override
   public void overrideXp(final int xp) {
   }

   @Override
   public void notifyTrade(final MerchantOffer offer) {
      offer.increaseUses();
      this.ambientSoundTime = -this.getAmbientSoundInterval();
      this.rewardTradeXp(offer);
      if (this.tradingPlayer instanceof ServerPlayer) {
         CriteriaTriggers.TRADE.trigger((ServerPlayer)this.tradingPlayer, this, offer.getResult());
      }
   }

   protected abstract void rewardTradeXp(final MerchantOffer offer);

   @Override
   public boolean showProgressBar() {
      return true;
   }

   @Override
   public void notifyTradeUpdated(final ItemStack itemStack) {
      if (!this.level().isClientSide() && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
         this.ambientSoundTime = -this.getAmbientSoundInterval();
         this.makeSound(this.getTradeUpdatedSound(!itemStack.isEmpty()));
      }
   }

   @Override
   public SoundEvent getNotifyTradeSound() {
      return SoundEvents.VILLAGER_YES;
   }

   protected SoundEvent getTradeUpdatedSound(final boolean validTrade) {
      return validTrade ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO;
   }

   public void playCelebrateSound() {
      this.makeSound(SoundEvents.VILLAGER_CELEBRATE);
   }

   @Override
   protected void addAdditionalSaveData(final ValueOutput output) {
      super.addAdditionalSaveData(output);
      if (!this.level().isClientSide()) {
         MerchantOffers offers = this.getOffers();
         if (!offers.isEmpty()) {
            output.store("Offers", MerchantOffers.CODEC, offers);
         }
      }

      this.writeInventoryToTag(output);
   }

   @Override
   protected void readAdditionalSaveData(final ValueInput input) {
      super.readAdditionalSaveData(input);
      this.offers = input.<MerchantOffers>read("Offers", MerchantOffers.CODEC).orElse(null);
      this.readInventoryFromTag(input);
   }

   @Override
   public @Nullable Entity teleport(final TeleportTransition transition) {
      this.stopTrading();
      return super.teleport(transition);
   }

   protected void stopTrading() {
      this.setTradingPlayer(null);
   }

   @Override
   public void die(final DamageSource source) {
      super.die(source);
      this.stopTrading();
   }

   protected void addParticlesAroundSelf(final ParticleOptions particle) {
      for (int i = 0; i < 5; i++) {
         double xa = this.random.nextGaussian() * 0.02;
         double ya = this.random.nextGaussian() * 0.02;
         double za = this.random.nextGaussian() * 0.02;
         this.level().addParticle(particle, this.getRandomX(1.0), this.getRandomY() + 1.0, this.getRandomZ(1.0), xa, ya, za);
      }
   }

   @Override
   public boolean canBeLeashed() {
      return false;
   }

   @Override
   public SimpleContainer getInventory() {
      return this.inventory;
   }

   @Override
   public @Nullable SlotAccess getSlot(final int slot) {
      int inventorySlot = slot - 300;
      return inventorySlot >= 0 && inventorySlot < this.inventory.getContainerSize() ? this.inventory.getSlot(inventorySlot) : super.getSlot(slot);
   }

   protected abstract void updateTrades(ServerLevel level);

   protected void addOffersFromItemListings(
      final ServerLevel level, final MerchantOffers merchantOffers, final VillagerTrades.ItemListing[] itemListings, final int numberOfOffers
   ) {
      ArrayList<VillagerTrades.ItemListing> potentialOffers = Lists.newArrayList(itemListings);
      int offersFound = 0;

      while (offersFound < numberOfOffers && !potentialOffers.isEmpty()) {
         MerchantOffer offer = potentialOffers.remove(this.random.nextInt(potentialOffers.size())).getOffer(level, this, this.random);
         if (offer != null) {
            merchantOffers.add(offer);
            offersFound++;
         }
      }
   }

   @Override
   public Vec3 getRopeHoldPosition(final float partialTickTime) {
      float yRot = Mth.lerp(partialTickTime, this.yBodyRotO, this.yBodyRot) * (float) (Math.PI / 180.0);
      Vec3 offset = new Vec3(0.0, this.getBoundingBox().getYsize() - 1.0, 0.2);
      return this.getPosition(partialTickTime).add(offset.yRot(-yRot));
   }

   @Override
   public boolean isClientSide() {
      return this.level().isClientSide();
   }

   @Override
   public boolean stillValid(final Player player) {
      return this.getTradingPlayer() == player && this.isAlive() && player.isWithinEntityInteractionRange(this, 4.0);
   }
}
