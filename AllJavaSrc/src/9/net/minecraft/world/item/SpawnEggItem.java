package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpawnEggItem extends Item {
   private static final Map<EntityType<?>, SpawnEggItem> BY_ID = Maps.newIdentityHashMap();

   public SpawnEggItem(final Item.Properties properties) {
      super(properties);
      TypedEntityData<EntityType<?>> entityType = this.components().get(DataComponents.ENTITY_DATA);
      if (entityType != null) {
         BY_ID.put(entityType.type(), this);
      }
   }

   @Override
   public InteractionResult useOn(final UseOnContext context) {
      Level level = context.getLevel();
      if (!(level instanceof ServerLevel serverLevel)) {
         return InteractionResult.SUCCESS;
      } else {
         ItemStack itemStack = context.getItemInHand();
         BlockPos pos = context.getClickedPos();
         Direction clickedFace = context.getClickedFace();
         BlockState blockState = level.getBlockState(pos);
         if (level.getBlockEntity(pos) instanceof Spawner spawnerHolder) {
            EntityType<?> type = this.getType(itemStack);
            if (type == null) {
               return InteractionResult.FAIL;
            }

            if (!serverLevel.isSpawnerBlockEnabled()) {
               if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                  serverPlayer.sendSystemMessage(Component.translatable("advMode.notEnabled.spawner"));
               }

               return InteractionResult.FAIL;
            } else {
               spawnerHolder.setEntityId(type, level.getRandom());
               level.sendBlockUpdated(pos, blockState, blockState, 3);
               level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, pos);
               itemStack.shrink(1);
               return InteractionResult.SUCCESS;
            }
         } else {
            BlockPos spawnPos;
            if (blockState.getCollisionShape(level, pos).isEmpty()) {
               spawnPos = pos;
            } else {
               spawnPos = pos.relative(clickedFace);
            }

            return this.spawnMob(context.getPlayer(), itemStack, level, spawnPos, true, !Objects.equals(pos, spawnPos) && clickedFace == Direction.UP);
         }
      }
   }

   private InteractionResult spawnMob(
      final @Nullable LivingEntity user,
      final ItemStack itemStack,
      final Level level,
      final BlockPos spawnPos,
      final boolean tryMoveDown,
      final boolean movedUp
   ) {
      EntityType<?> type = this.getType(itemStack);
      if (type == null) {
         return InteractionResult.FAIL;
      }

      if (!type.isAllowedInPeaceful() && level.getDifficulty() == Difficulty.PEACEFUL) {
         return InteractionResult.FAIL;
      }

      if (type.spawn((ServerLevel)level, itemStack, user, spawnPos, EntitySpawnReason.SPAWN_ITEM_USE, tryMoveDown, movedUp) != null) {
         itemStack.consume(1, user);
         level.gameEvent(user, GameEvent.ENTITY_PLACE, spawnPos);
      }

      return InteractionResult.SUCCESS;
   }

   @Override
   public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
      ItemStack itemStack = player.getItemInHand(hand);
      BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
      if (hitResult.getType() != HitResult.Type.BLOCK) {
         return InteractionResult.PASS;
      }

      if (level instanceof ServerLevel serverLevel) {
         BlockPos pos = hitResult.getBlockPos();
         if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) {
            return InteractionResult.PASS;
         }

         if (level.mayInteract(player, pos) && player.mayUseItemAt(pos, hitResult.getDirection(), itemStack)) {
            InteractionResult result = this.spawnMob(player, itemStack, level, pos, false, false);
            if (result == InteractionResult.SUCCESS) {
               player.awardStat(Stats.ITEM_USED.get(this));
            }

            return result;
         } else {
            return InteractionResult.FAIL;
         }
      } else {
         return InteractionResult.SUCCESS;
      }
   }

   public boolean spawnsEntity(final ItemStack itemStack, final EntityType<?> type) {
      return Objects.equals(this.getType(itemStack), type);
   }

   public static @Nullable SpawnEggItem byId(final @Nullable EntityType<?> type) {
      return BY_ID.get(type);
   }

   public static Iterable<SpawnEggItem> eggs() {
      return Iterables.unmodifiableIterable(BY_ID.values());
   }

   public @Nullable EntityType<?> getType(final ItemStack itemStack) {
      TypedEntityData<EntityType<?>> entityData = itemStack.get(DataComponents.ENTITY_DATA);
      return entityData != null ? entityData.type() : null;
   }

   @Override
   public FeatureFlagSet requiredFeatures() {
      return Optional.ofNullable(this.components().get(DataComponents.ENTITY_DATA))
         .map(TypedEntityData::type)
         .map(EntityType::requiredFeatures)
         .orElseGet(FeatureFlagSet::of);
   }

   public Optional<Mob> spawnOffspringFromSpawnEgg(
      final Player player, final Mob parent, final EntityType<? extends Mob> type, final ServerLevel level, final Vec3 pos, final ItemStack spawnEggStack
   ) {
      if (!this.spawnsEntity(spawnEggStack, type)) {
         return Optional.empty();
      }

      Mob offspring;
      if (parent instanceof AgeableMob) {
         offspring = ((AgeableMob)parent).getBreedOffspring(level, (AgeableMob)parent);
      } else {
         offspring = type.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
      }

      if (offspring == null) {
         return Optional.empty();
      }

      offspring.setBaby(true);
      if (!offspring.isBaby()) {
         return Optional.empty();
      }

      offspring.snapTo(pos.x(), pos.y(), pos.z(), 0.0F, 0.0F);
      offspring.applyComponentsFromItemStack(spawnEggStack);
      level.addFreshEntityWithPassengers(offspring);
      spawnEggStack.consume(1, player);
      return Optional.of(offspring);
   }

   @Override
   public boolean shouldPrintOpWarning(final ItemStack stack, final @Nullable Player player) {
      if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
         TypedEntityData<EntityType<?>> entityData = stack.get(DataComponents.ENTITY_DATA);
         if (entityData != null) {
            return entityData.type().onlyOpCanSetNbt();
         }
      }

      return false;
   }
}
