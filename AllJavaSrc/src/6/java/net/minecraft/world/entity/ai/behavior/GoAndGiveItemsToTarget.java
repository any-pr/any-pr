package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.allay.AllayAi;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GoAndGiveItemsToTarget<E extends LivingEntity & InventoryCarrier> extends Behavior<E> {
   private static final int CLOSE_ENOUGH_DISTANCE_TO_TARGET = 3;
   private static final int ITEM_PICKUP_COOLDOWN_AFTER_THROWING = 60;
   private final Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter;
   private final float speedModifier;

   public GoAndGiveItemsToTarget(
      final Function<LivingEntity, Optional<PositionTracker>> targetPositionGetter, final float speedModifier, final int timeoutDuration
   ) {
      super(
         Map.of(
            MemoryModuleType.LOOK_TARGET,
            MemoryStatus.REGISTERED,
            MemoryModuleType.WALK_TARGET,
            MemoryStatus.REGISTERED,
            MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
            MemoryStatus.REGISTERED
         ),
         timeoutDuration
      );
      this.targetPositionGetter = targetPositionGetter;
      this.speedModifier = speedModifier;
   }

   @Override
   protected boolean checkExtraStartConditions(final ServerLevel level, final E body) {
      return this.canThrowItemToTarget(body);
   }

   @Override
   protected boolean canStillUse(final ServerLevel level, final E body, final long timestamp) {
      return this.canThrowItemToTarget(body);
   }

   @Override
   protected void start(final ServerLevel level, final E body, final long timestamp) {
      this.targetPositionGetter
         .apply(body)
         .ifPresent(positionTracker -> BehaviorUtils.setWalkAndLookTargetMemories(body, positionTracker, this.speedModifier, 3));
   }

   @Override
   protected void tick(final ServerLevel level, final E body, final long timestamp) {
      Optional<PositionTracker> targetPosition = this.targetPositionGetter.apply(body);
      if (!targetPosition.isEmpty()) {
         PositionTracker depositTarget = targetPosition.get();
         double distanceToTarget = depositTarget.currentPosition().distanceTo(body.getEyePosition());
         if (distanceToTarget < 3.0) {
            ItemStack item = body.getInventory().removeItem(0, 1);
            if (!item.isEmpty()) {
               throwItem(body, item, getThrowPosition(depositTarget));
               if (body instanceof Allay allay) {
                  AllayAi.getLikedPlayer(allay).ifPresent(player -> this.triggerDropItemOnBlock(depositTarget, item, player));
               }

               body.getBrain().setMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, 60);
            }
         }
      }
   }

   private void triggerDropItemOnBlock(final PositionTracker depositTarget, final ItemStack item, final ServerPlayer player) {
      BlockPos belowPos = depositTarget.currentBlockPosition().below();
      CriteriaTriggers.ALLAY_DROP_ITEM_ON_BLOCK.trigger(player, belowPos, item);
   }

   private boolean canThrowItemToTarget(final E body) {
      if (body.getInventory().isEmpty()) {
         return false;
      }

      Optional<PositionTracker> positionTracker = this.targetPositionGetter.apply(body);
      return positionTracker.isPresent();
   }

   private static Vec3 getThrowPosition(final PositionTracker depositTarget) {
      return depositTarget.currentPosition().add(0.0, 1.0, 0.0);
   }

   public static void throwItem(final LivingEntity thrower, final ItemStack item, final Vec3 targetPos) {
      Vec3 throwVelocity = new Vec3(0.2F, 0.3F, 0.2F);
      BehaviorUtils.throwItem(thrower, item, targetPos, throwVelocity, 0.2F);
      Level level = thrower.level();
      if (level.getGameTime() % 7L == 0L && level.random.nextDouble() < 0.9) {
         float pitch = Util.<Float>getRandom(Allay.THROW_SOUND_PITCHES, level.getRandom());
         level.playSound(null, thrower, SoundEvents.ALLAY_THROW, SoundSource.NEUTRAL, 1.0F, pitch);
      }
   }
}
