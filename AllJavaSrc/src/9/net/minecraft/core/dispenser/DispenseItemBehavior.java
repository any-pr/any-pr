package net.minecraft.core.dispenser;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public interface DispenseItemBehavior {
   Logger LOGGER = LogUtils.getLogger();
   DispenseItemBehavior NOOP = (source, dispensed) -> dispensed;

   ItemStack dispense(BlockSource source, ItemStack dispensed);

   static void bootStrap() {
      DispenserBlock.registerProjectileBehavior(Items.ARROW);
      DispenserBlock.registerProjectileBehavior(Items.TIPPED_ARROW);
      DispenserBlock.registerProjectileBehavior(Items.SPECTRAL_ARROW);
      DispenserBlock.registerProjectileBehavior(Items.EGG);
      DispenserBlock.registerProjectileBehavior(Items.BLUE_EGG);
      DispenserBlock.registerProjectileBehavior(Items.BROWN_EGG);
      DispenserBlock.registerProjectileBehavior(Items.SNOWBALL);
      DispenserBlock.registerProjectileBehavior(Items.EXPERIENCE_BOTTLE);
      DispenserBlock.registerProjectileBehavior(Items.SPLASH_POTION);
      DispenserBlock.registerProjectileBehavior(Items.LINGERING_POTION);
      DispenserBlock.registerProjectileBehavior(Items.FIREWORK_ROCKET);
      DispenserBlock.registerProjectileBehavior(Items.FIRE_CHARGE);
      DispenserBlock.registerProjectileBehavior(Items.WIND_CHARGE);
      DefaultDispenseItemBehavior spawnEggBehavior = new DefaultDispenseItemBehavior() {
         @Override
         public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            Direction direction = source.state().getValue(DispenserBlock.FACING);
            EntityType<?> type = ((SpawnEggItem)dispensed.getItem()).getType(dispensed);
            if (type == null) {
               return dispensed;
            }

            try {
               type.spawn(source.level(), dispensed, null, source.pos().relative(direction), EntitySpawnReason.DISPENSER, direction != Direction.UP, false);
            } catch (Exception e) {
               LOGGER.error("Error while dispensing spawn egg from dispenser at {}", source.pos(), e);
               return ItemStack.EMPTY;
            }

            dispensed.shrink(1);
            source.level().gameEvent(null, GameEvent.ENTITY_PLACE, source.pos());
            return dispensed;
         }
      };

      for (SpawnEggItem item : SpawnEggItem.eggs()) {
         DispenserBlock.registerBehavior(item, spawnEggBehavior);
      }

      DispenserBlock.registerBehavior(
         Items.ARMOR_STAND,
         new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
               Direction direction = source.state().getValue(DispenserBlock.FACING);
               BlockPos pos = source.pos().relative(direction);
               ServerLevel serverLevel = source.level();
               Consumer<ArmorStand> postSpawnConfig = EntityType.appendDefaultStackConfig(
                  armorStandx -> armorStandx.setYRot(direction.toYRot()), serverLevel, dispensed, null
               );
               ArmorStand armorStand = EntityType.ARMOR_STAND.spawn(serverLevel, postSpawnConfig, pos, EntitySpawnReason.DISPENSER, false, false);
               if (armorStand != null) {
                  dispensed.shrink(1);
               }

               return dispensed;
            }
         }
      );
      DispenserBlock.registerBehavior(
         Items.CHEST,
         new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
               BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));

               for (AbstractChestedHorse abstractChestedHorse : source.level()
                  .getEntitiesOfClass(AbstractChestedHorse.class, new AABB(pos), entity -> entity.isAlive() && !entity.hasChest())) {
                  if (abstractChestedHorse.isTamed()) {
                     SlotAccess slot = abstractChestedHorse.getSlot(499);
                     if (slot != null && slot.set(dispensed)) {
                        dispensed.shrink(1);
                        this.setSuccess(true);
                        return dispensed;
                     }
                  }
               }

               return super.execute(source, dispensed);
            }
         }
      );
      DispenserBlock.registerBehavior(Items.OAK_BOAT, new BoatDispenseItemBehavior(EntityType.OAK_BOAT));
      DispenserBlock.registerBehavior(Items.SPRUCE_BOAT, new BoatDispenseItemBehavior(EntityType.SPRUCE_BOAT));
      DispenserBlock.registerBehavior(Items.BIRCH_BOAT, new BoatDispenseItemBehavior(EntityType.BIRCH_BOAT));
      DispenserBlock.registerBehavior(Items.JUNGLE_BOAT, new BoatDispenseItemBehavior(EntityType.JUNGLE_BOAT));
      DispenserBlock.registerBehavior(Items.DARK_OAK_BOAT, new BoatDispenseItemBehavior(EntityType.DARK_OAK_BOAT));
      DispenserBlock.registerBehavior(Items.ACACIA_BOAT, new BoatDispenseItemBehavior(EntityType.ACACIA_BOAT));
      DispenserBlock.registerBehavior(Items.CHERRY_BOAT, new BoatDispenseItemBehavior(EntityType.CHERRY_BOAT));
      DispenserBlock.registerBehavior(Items.MANGROVE_BOAT, new BoatDispenseItemBehavior(EntityType.MANGROVE_BOAT));
      DispenserBlock.registerBehavior(Items.PALE_OAK_BOAT, new BoatDispenseItemBehavior(EntityType.PALE_OAK_BOAT));
      DispenserBlock.registerBehavior(Items.BAMBOO_RAFT, new BoatDispenseItemBehavior(EntityType.BAMBOO_RAFT));
      DispenserBlock.registerBehavior(Items.OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.OAK_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.SPRUCE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.SPRUCE_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.BIRCH_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.BIRCH_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.JUNGLE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.JUNGLE_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.DARK_OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.DARK_OAK_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.ACACIA_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.ACACIA_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.CHERRY_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.CHERRY_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.MANGROVE_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.MANGROVE_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.PALE_OAK_CHEST_BOAT, new BoatDispenseItemBehavior(EntityType.PALE_OAK_CHEST_BOAT));
      DispenserBlock.registerBehavior(Items.BAMBOO_CHEST_RAFT, new BoatDispenseItemBehavior(EntityType.BAMBOO_CHEST_RAFT));
      DispenseItemBehavior filledBucketBehavior = new DefaultDispenseItemBehavior() {
         private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

         @Override
         public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            DispensibleContainerItem bucket = (DispensibleContainerItem)dispensed.getItem();
            BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            Level level = source.level();
            if (bucket.emptyContents(null, level, target, null)) {
               bucket.checkExtraContent(null, level, dispensed, target);
               return this.consumeWithRemainder(source, dispensed, new ItemStack(Items.BUCKET));
            } else {
               return this.defaultDispenseItemBehavior.dispense(source, dispensed);
            }
         }
      };
      DispenserBlock.registerBehavior(Items.LAVA_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.WATER_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.POWDER_SNOW_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.SALMON_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.COD_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.PUFFERFISH_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.TROPICAL_FISH_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.AXOLOTL_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.TADPOLE_BUCKET, filledBucketBehavior);
      DispenserBlock.registerBehavior(Items.BUCKET, new DefaultDispenseItemBehavior() {
         @Override
         public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            LevelAccessor level = source.level();
            BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            BlockState blockState = level.getBlockState(target);
            if (blockState.getBlock() instanceof BucketPickup bucket) {
               ItemStack pickup = bucket.pickupBlock(null, level, target, blockState);
               if (pickup.isEmpty()) {
                  return super.execute(source, dispensed);
               }

               level.gameEvent(null, GameEvent.FLUID_PICKUP, target);
               Item targetType = pickup.getItem();
               return this.consumeWithRemainder(source, dispensed, new ItemStack(targetType));
            } else {
               return super.execute(source, dispensed);
            }
         }
      });
      DispenserBlock.registerBehavior(Items.FLINT_AND_STEEL, new OptionalDispenseItemBehavior() {
         @Override
         protected ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            ServerLevel level = source.level();
            this.setSuccess(true);
            Direction facing = source.state().getValue(DispenserBlock.FACING);
            BlockPos targetPos = source.pos().relative(facing);
            BlockState target = level.getBlockState(targetPos);
            if (BaseFireBlock.canBePlacedAt(level, targetPos, facing)) {
               level.setBlockAndUpdate(targetPos, BaseFireBlock.getState(level, targetPos));
               level.gameEvent(null, GameEvent.BLOCK_PLACE, targetPos);
            } else if (CampfireBlock.canLight(target) || CandleBlock.canLight(target) || CandleCakeBlock.canLight(target)) {
               level.setBlockAndUpdate(targetPos, target.setValue(BlockStateProperties.LIT, true));
               level.gameEvent(null, GameEvent.BLOCK_CHANGE, targetPos);
            } else if (target.getBlock() instanceof TntBlock) {
               if (TntBlock.prime(level, targetPos)) {
                  level.removeBlock(targetPos, false);
               } else {
                  this.setSuccess(false);
               }
            } else {
               this.setSuccess(false);
            }

            if (this.isSuccess()) {
               dispensed.hurtAndBreak(1, level, null, item -> {});
            }

            return dispensed;
         }
      });
      DispenserBlock.registerBehavior(Items.BONE_MEAL, new OptionalDispenseItemBehavior() {
         @Override
         protected ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            this.setSuccess(true);
            Level level = source.level();
            BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            if (!BoneMealItem.growCrop(dispensed, level, target) && !BoneMealItem.growWaterPlant(dispensed, level, target, null)) {
               this.setSuccess(false);
            } else if (!level.isClientSide()) {
               level.levelEvent(1505, target, 15);
            }

            return dispensed;
         }
      });
      DispenserBlock.registerBehavior(Blocks.TNT, new OptionalDispenseItemBehavior() {
         @Override
         protected ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            ServerLevel level = source.level();
            if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) {
               this.setSuccess(false);
               return dispensed;
            } else {
               BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
               PrimedTnt tnt = new PrimedTnt(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, null);
               level.addFreshEntity(tnt);
               level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
               level.gameEvent(null, GameEvent.ENTITY_PLACE, target);
               dispensed.shrink(1);
               this.setSuccess(true);
               return dispensed;
            }
         }
      });
      DispenserBlock.registerBehavior(
         Items.WITHER_SKELETON_SKULL,
         new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(final BlockSource source, final ItemStack dispensed) {
               Level level = source.level();
               Direction direction = source.state().getValue(DispenserBlock.FACING);
               BlockPos target = source.pos().relative(direction);
               if (level.isEmptyBlock(target) && WitherSkullBlock.canSpawnMob(level, target, dispensed)) {
                  level.setBlock(
                     target, Blocks.WITHER_SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(direction)), 3
                  );
                  level.gameEvent(null, GameEvent.BLOCK_PLACE, target);
                  BlockEntity skull = level.getBlockEntity(target);
                  if (skull instanceof SkullBlockEntity) {
                     WitherSkullBlock.checkSpawn(level, target, (SkullBlockEntity)skull);
                  }

                  dispensed.shrink(1);
                  this.setSuccess(true);
               } else {
                  this.setSuccess(EquipmentDispenseItemBehavior.dispenseEquipment(source, dispensed));
               }

               return dispensed;
            }
         }
      );
      DispenserBlock.registerBehavior(Blocks.CARVED_PUMPKIN, new OptionalDispenseItemBehavior() {
         @Override
         protected ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            Level level = source.level();
            BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            CarvedPumpkinBlock pumpkinBlock = (CarvedPumpkinBlock)Blocks.CARVED_PUMPKIN;
            if (level.isEmptyBlock(target) && pumpkinBlock.canSpawnGolem(level, target)) {
               if (!level.isClientSide()) {
                  level.setBlock(target, pumpkinBlock.defaultBlockState(), 3);
                  level.gameEvent(null, GameEvent.BLOCK_PLACE, target);
               }

               dispensed.shrink(1);
               this.setSuccess(true);
            } else {
               this.setSuccess(EquipmentDispenseItemBehavior.dispenseEquipment(source, dispensed));
            }

            return dispensed;
         }
      });
      DispenserBlock.registerBehavior(Blocks.SHULKER_BOX.asItem(), new ShulkerBoxDispenseBehavior());

      for (DyeColor color : DyeColor.values()) {
         DispenserBlock.registerBehavior(ShulkerBoxBlock.getBlockByColor(color).asItem(), new ShulkerBoxDispenseBehavior());
      }

      DispenserBlock.registerBehavior(
         Items.GLASS_BOTTLE.asItem(),
         new OptionalDispenseItemBehavior() {
            private ItemStack takeLiquid(final BlockSource source, final ItemStack dispensed, final ItemStack filledItemStack) {
               source.level().gameEvent(null, GameEvent.FLUID_PICKUP, source.pos());
               return this.consumeWithRemainder(source, dispensed, filledItemStack);
            }

            @Override
            public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
               this.setSuccess(false);
               ServerLevel level = source.level();
               BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
               BlockState state = level.getBlockState(target);
               if (state.is(BlockTags.BEEHIVES, s -> s.hasProperty(BeehiveBlock.HONEY_LEVEL) && s.getBlock() instanceof BeehiveBlock)
                  && state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
                  ((BeehiveBlock)state.getBlock()).releaseBeesAndResetHoneyLevel(level, state, target, null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                  this.setSuccess(true);
                  return this.takeLiquid(source, dispensed, new ItemStack(Items.HONEY_BOTTLE));
               } else if (level.getFluidState(target).is(FluidTags.WATER)) {
                  this.setSuccess(true);
                  return this.takeLiquid(source, dispensed, PotionContents.createItemStack(Items.POTION, Potions.WATER));
               } else {
                  return super.execute(source, dispensed);
               }
            }
         }
      );
      DispenserBlock.registerBehavior(Items.GLOWSTONE, new OptionalDispenseItemBehavior() {
         @Override
         public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            Direction direction = source.state().getValue(DispenserBlock.FACING);
            BlockPos pos = source.pos().relative(direction);
            Level level = source.level();
            BlockState blockState = level.getBlockState(pos);
            this.setSuccess(true);
            if (blockState.is(Blocks.RESPAWN_ANCHOR)) {
               if (blockState.getValue(RespawnAnchorBlock.CHARGE) != 4) {
                  RespawnAnchorBlock.charge(null, level, pos, blockState);
                  dispensed.shrink(1);
               } else {
                  this.setSuccess(false);
               }

               return dispensed;
            } else {
               return super.execute(source, dispensed);
            }
         }
      });
      DispenserBlock.registerBehavior(Items.SHEARS.asItem(), new ShearsDispenseItemBehavior());
      DispenserBlock.registerBehavior(Items.BRUSH.asItem(), new OptionalDispenseItemBehavior() {
         @Override
         protected ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            ServerLevel level = source.level();
            BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            List<Armadillo> armadillos = level.getEntitiesOfClass(Armadillo.class, new AABB(pos), EntitySelector.NO_SPECTATORS);
            if (armadillos.isEmpty()) {
               this.setSuccess(false);
               return dispensed;
            }

            for (Armadillo armadillo : armadillos) {
               if (armadillo.brushOffScute(null, dispensed)) {
                  dispensed.hurtAndBreak(16, level, null, item -> {});
                  return dispensed;
               }
            }

            this.setSuccess(false);
            return dispensed;
         }
      });
      DispenserBlock.registerBehavior(Items.HONEYCOMB, new OptionalDispenseItemBehavior() {
         @Override
         public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
            BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            Level level = source.level();
            BlockState blockState = level.getBlockState(pos);
            Optional<BlockState> maybeWaxed = HoneycombItem.getWaxed(blockState);
            if (maybeWaxed.isPresent()) {
               level.setBlockAndUpdate(pos, maybeWaxed.get());
               level.levelEvent(3003, pos, 0);
               dispensed.shrink(1);
               this.setSuccess(true);
               return dispensed;
            } else {
               return super.execute(source, dispensed);
            }
         }
      });
      DispenserBlock.registerBehavior(
         Items.POTION,
         new DefaultDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

            @Override
            public ItemStack execute(final BlockSource source, final ItemStack dispensed) {
               PotionContents potion = dispensed.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
               if (!potion.is(Potions.WATER)) {
                  return this.defaultDispenseItemBehavior.dispense(source, dispensed);
               }

               ServerLevel level = source.level();
               BlockPos pos = source.pos();
               BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
               if (!level.getBlockState(target).is(BlockTags.CONVERTABLE_TO_MUD)) {
                  return this.defaultDispenseItemBehavior.dispense(source, dispensed);
               }

               if (!level.isClientSide()) {
                  for (int i = 0; i < 5; i++) {
                     level.sendParticles(
                        ParticleTypes.SPLASH,
                        pos.getX() + level.random.nextDouble(),
                        pos.getY() + 1,
                        pos.getZ() + level.random.nextDouble(),
                        1,
                        0.0,
                        0.0,
                        0.0,
                        1.0
                     );
                  }
               }

               level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
               level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
               level.setBlockAndUpdate(target, Blocks.MUD.defaultBlockState());
               return this.consumeWithRemainder(source, dispensed, new ItemStack(Items.GLASS_BOTTLE));
            }
         }
      );
      DispenserBlock.registerBehavior(Items.MINECART, new MinecartDispenseItemBehavior(EntityType.MINECART));
      DispenserBlock.registerBehavior(Items.CHEST_MINECART, new MinecartDispenseItemBehavior(EntityType.CHEST_MINECART));
      DispenserBlock.registerBehavior(Items.FURNACE_MINECART, new MinecartDispenseItemBehavior(EntityType.FURNACE_MINECART));
      DispenserBlock.registerBehavior(Items.TNT_MINECART, new MinecartDispenseItemBehavior(EntityType.TNT_MINECART));
      DispenserBlock.registerBehavior(Items.HOPPER_MINECART, new MinecartDispenseItemBehavior(EntityType.HOPPER_MINECART));
      DispenserBlock.registerBehavior(Items.COMMAND_BLOCK_MINECART, new MinecartDispenseItemBehavior(EntityType.COMMAND_BLOCK_MINECART));
   }
}
