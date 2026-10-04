package net.minecraft.world.item;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.math.Fraction;

public class BundleItem extends Item {
   public static final int MAX_SHOWN_GRID_ITEMS_X = 4;
   public static final int MAX_SHOWN_GRID_ITEMS_Y = 3;
   public static final int MAX_SHOWN_GRID_ITEMS = 12;
   public static final int OVERFLOWING_MAX_SHOWN_GRID_ITEMS = 11;
   private static final int FULL_BAR_COLOR = ARGB.colorFromFloat(1.0F, 1.0F, 0.33F, 0.33F);
   private static final int BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.44F, 0.53F, 1.0F);
   private static final int TICKS_AFTER_FIRST_THROW = 10;
   private static final int TICKS_BETWEEN_THROWS = 2;
   private static final int TICKS_MAX_THROW_DURATION = 200;

   public BundleItem(final Item.Properties properties) {
      super(properties);
   }

   public static float getFullnessDisplay(final ItemStack itemStack) {
      BundleContents contents = itemStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
      return contents.weight().floatValue();
   }

   @Override
   public boolean overrideStackedOnOther(final ItemStack self, final Slot slot, final ClickAction clickAction, final Player player) {
      BundleContents initialContents = self.get(DataComponents.BUNDLE_CONTENTS);
      if (initialContents == null) {
         return false;
      }

      ItemStack other = slot.getItem();
      BundleContents.Mutable contents = new BundleContents.Mutable(initialContents);
      if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
         if (contents.tryTransfer(slot, player) > 0) {
            playInsertSound(player);
         } else {
            playInsertFailSound(player);
         }

         self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
         this.broadcastChangesOnContainerMenu(player);
         return true;
      } else if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
         ItemStack itemStack = contents.removeOne();
         if (itemStack != null) {
            ItemStack remainder = slot.safeInsert(itemStack);
            if (remainder.getCount() > 0) {
               contents.tryInsert(remainder);
            } else {
               playRemoveOneSound(player);
            }
         }

         self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
         this.broadcastChangesOnContainerMenu(player);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean overrideOtherStackedOnMe(
      final ItemStack self, final ItemStack other, final Slot slot, final ClickAction clickAction, final Player player, final SlotAccess carriedItem
   ) {
      if (clickAction == ClickAction.PRIMARY && other.isEmpty()) {
         toggleSelectedItem(self, -1);
         return false;
      }

      BundleContents initialContents = self.get(DataComponents.BUNDLE_CONTENTS);
      if (initialContents == null) {
         return false;
      }

      BundleContents.Mutable contents = new BundleContents.Mutable(initialContents);
      if (clickAction == ClickAction.PRIMARY && !other.isEmpty()) {
         if (slot.allowModification(player) && contents.tryInsert(other) > 0) {
            playInsertSound(player);
         } else {
            playInsertFailSound(player);
         }

         self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
         this.broadcastChangesOnContainerMenu(player);
         return true;
      } else if (clickAction == ClickAction.SECONDARY && other.isEmpty()) {
         if (slot.allowModification(player)) {
            ItemStack removed = contents.removeOne();
            if (removed != null) {
               playRemoveOneSound(player);
               carriedItem.set(removed);
            }
         }

         self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
         this.broadcastChangesOnContainerMenu(player);
         return true;
      } else {
         toggleSelectedItem(self, -1);
         return false;
      }
   }

   @Override
   public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
      player.startUsingItem(hand);
      return InteractionResult.SUCCESS;
   }

   private void dropContent(final Level level, final Player player, final ItemStack itemStack) {
      if (this.dropContent(itemStack, player)) {
         playDropContentsSound(level, player);
         player.awardStat(Stats.ITEM_USED.get(this));
      }
   }

   @Override
   public boolean isBarVisible(final ItemStack stack) {
      BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
      return contents.weight().compareTo(Fraction.ZERO) > 0;
   }

   @Override
   public int getBarWidth(final ItemStack stack) {
      BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
      return Math.min(1 + Mth.mulAndTruncate(contents.weight(), 12), 13);
   }

   @Override
   public int getBarColor(final ItemStack stack) {
      BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
      return contents.weight().compareTo(Fraction.ONE) >= 0 ? FULL_BAR_COLOR : BAR_COLOR;
   }

   public static void toggleSelectedItem(final ItemStack stack, final int selectedItem) {
      BundleContents initialContents = stack.get(DataComponents.BUNDLE_CONTENTS);
      if (initialContents != null) {
         BundleContents.Mutable contents = new BundleContents.Mutable(initialContents);
         contents.toggleSelectedItem(selectedItem);
         stack.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
      }
   }

   public static boolean hasSelectedItem(final ItemStack stack) {
      BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
      return contents != null && contents.getSelectedItem() != -1;
   }

   public static int getSelectedItem(final ItemStack stack) {
      BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
      return contents.getSelectedItem();
   }

   public static ItemStack getSelectedItemStack(final ItemStack stack) {
      BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
      return contents != null && contents.getSelectedItem() != -1 ? contents.getItemUnsafe(contents.getSelectedItem()) : ItemStack.EMPTY;
   }

   public static int getNumberOfItemsToShow(final ItemStack stack) {
      BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
      return contents.getNumberOfItemsToShow();
   }

   private boolean dropContent(final ItemStack bundle, final Player player) {
      BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
      if (contents != null && !contents.isEmpty()) {
         Optional<ItemStack> itemStack = removeOneItemFromBundle(bundle, player, contents);
         if (itemStack.isPresent()) {
            player.drop(itemStack.get(), true);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static Optional<ItemStack> removeOneItemFromBundle(final ItemStack self, final Player player, final BundleContents initialContents) {
      BundleContents.Mutable contents = new BundleContents.Mutable(initialContents);
      ItemStack removed = contents.removeOne();
      if (removed != null) {
         playRemoveOneSound(player);
         self.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
         return Optional.of(removed);
      } else {
         return Optional.empty();
      }
   }

   @Override
   public void onUseTick(final Level level, final LivingEntity livingEntity, final ItemStack itemStack, final int ticksRemaining) {
      if (livingEntity instanceof Player player) {
         int useDuration = this.getUseDuration(itemStack, livingEntity);
         boolean isFirstTick = ticksRemaining == useDuration;
         if (isFirstTick || ticksRemaining < useDuration - 10 && ticksRemaining % 2 == 0) {
            this.dropContent(level, player, itemStack);
         }
      }
   }

   @Override
   public int getUseDuration(final ItemStack itemStack, final LivingEntity entity) {
      return 200;
   }

   @Override
   public ItemUseAnimation getUseAnimation(final ItemStack itemStack) {
      return ItemUseAnimation.BUNDLE;
   }

   @Override
   public Optional<TooltipComponent> getTooltipImage(final ItemStack bundle) {
      TooltipDisplay display = bundle.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
      return !display.shows(DataComponents.BUNDLE_CONTENTS)
         ? Optional.empty()
         : Optional.ofNullable(bundle.get(DataComponents.BUNDLE_CONTENTS)).map(BundleTooltip::new);
   }

   @Override
   public void onDestroyed(final ItemEntity entity) {
      BundleContents contents = entity.getItem().get(DataComponents.BUNDLE_CONTENTS);
      if (contents != null) {
         entity.getItem().set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
         ItemUtils.onContainerDestroyed(entity, contents.itemsCopy());
      }
   }

   public static List<BundleItem> getAllBundleItemColors() {
      return Stream.of(
            Items.BUNDLE,
            Items.WHITE_BUNDLE,
            Items.ORANGE_BUNDLE,
            Items.MAGENTA_BUNDLE,
            Items.LIGHT_BLUE_BUNDLE,
            Items.YELLOW_BUNDLE,
            Items.LIME_BUNDLE,
            Items.PINK_BUNDLE,
            Items.GRAY_BUNDLE,
            Items.LIGHT_GRAY_BUNDLE,
            Items.CYAN_BUNDLE,
            Items.BLACK_BUNDLE,
            Items.BROWN_BUNDLE,
            Items.GREEN_BUNDLE,
            Items.RED_BUNDLE,
            Items.BLUE_BUNDLE,
            Items.PURPLE_BUNDLE
         )
         .map(item -> (BundleItem)item)
         .toList();
   }

   public static Item getByColor(final DyeColor color) {
      return switch (color) {
         case WHITE -> Items.WHITE_BUNDLE;
         case ORANGE -> Items.ORANGE_BUNDLE;
         case MAGENTA -> Items.MAGENTA_BUNDLE;
         case LIGHT_BLUE -> Items.LIGHT_BLUE_BUNDLE;
         case YELLOW -> Items.YELLOW_BUNDLE;
         case LIME -> Items.LIME_BUNDLE;
         case PINK -> Items.PINK_BUNDLE;
         case GRAY -> Items.GRAY_BUNDLE;
         case LIGHT_GRAY -> Items.LIGHT_GRAY_BUNDLE;
         case CYAN -> Items.CYAN_BUNDLE;
         case BLUE -> Items.BLUE_BUNDLE;
         case BROWN -> Items.BROWN_BUNDLE;
         case GREEN -> Items.GREEN_BUNDLE;
         case RED -> Items.RED_BUNDLE;
         case BLACK -> Items.BLACK_BUNDLE;
         case PURPLE -> Items.PURPLE_BUNDLE;
      };
   }

   private static void playRemoveOneSound(final Entity entity) {
      entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
   }

   private static void playInsertSound(final Entity entity) {
      entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
   }

   private static void playInsertFailSound(final Entity entity) {
      entity.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
   }

   private static void playDropContentsSound(final Level level, final Entity entity) {
      level.playSound(
         null, entity.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F
      );
   }

   private void broadcastChangesOnContainerMenu(final Player player) {
      AbstractContainerMenu containerMenu = player.containerMenu;
      if (containerMenu != null) {
         containerMenu.slotsChanged(player.getInventory());
      }
   }
}
