package net.minecraft.world.item.component;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

public final class BundleContents implements TooltipComponent {
   public static final BundleContents EMPTY = new BundleContents(List.of());
   public static final Codec<BundleContents> CODEC = ItemStack.CODEC
      .listOf()
      .flatXmap(BundleContents::checkAndCreate, contents -> DataResult.success(contents.items));
   public static final StreamCodec<RegistryFriendlyByteBuf, BundleContents> STREAM_CODEC = ItemStack.STREAM_CODEC
      .apply(ByteBufCodecs.list())
      .map(BundleContents::new, contents -> contents.items);
   private static final Fraction BUNDLE_IN_BUNDLE_WEIGHT = Fraction.getFraction(1, 16);
   private static final int NO_STACK_INDEX = -1;
   public static final int NO_SELECTED_ITEM_INDEX = -1;
   private final List<ItemStack> items;
   private final Fraction weight;
   private final int selectedItem;

   private BundleContents(final List<ItemStack> items, final Fraction weight, final int selectedItem) {
      this.items = items;
      this.weight = weight;
      this.selectedItem = selectedItem;
   }

   private static DataResult<BundleContents> checkAndCreate(final List<ItemStack> items) {
      try {
         Fraction weight = computeContentWeight(items);
         return DataResult.success(new BundleContents(items, weight, -1));
      } catch (ArithmeticException exception) {
         return DataResult.error(() -> "Excessive total bundle weight");
      }
   }

   public BundleContents(final List<ItemStack> items) {
      this(items, computeContentWeight(items), -1);
   }

   private static Fraction computeContentWeight(final List<ItemStack> items) {
      Fraction weight = Fraction.ZERO;

      for (ItemStack stack : items) {
         weight = weight.add(getWeight(stack).multiplyBy(Fraction.getFraction(stack.getCount(), 1)));
      }

      return weight;
   }

   private static Fraction getWeight(final ItemStack stack) {
      BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
      if (bundle != null) {
         return BUNDLE_IN_BUNDLE_WEIGHT.add(bundle.weight());
      }

      List<BeehiveBlockEntity.Occupant> bees = stack.getOrDefault(DataComponents.BEES, Bees.EMPTY).bees();
      return !bees.isEmpty() ? Fraction.ONE : Fraction.getFraction(1, stack.getMaxStackSize());
   }

   public static boolean canItemBeInBundle(final ItemStack itemsToAdd) {
      return !itemsToAdd.isEmpty() && itemsToAdd.getItem().canFitInsideContainerItems();
   }

   public int getNumberOfItemsToShow() {
      int numberOfItemStacks = this.size();
      int availableItemsToShow = numberOfItemStacks > 12 ? 11 : 12;
      int itemsOnNonFullRow = numberOfItemStacks % 4;
      int emptySpaceOnNonFullRow = itemsOnNonFullRow == 0 ? 0 : 4 - itemsOnNonFullRow;
      return Math.min(numberOfItemStacks, availableItemsToShow - emptySpaceOnNonFullRow);
   }

   public ItemStack getItemUnsafe(final int index) {
      return this.items.get(index);
   }

   public Stream<ItemStack> itemCopyStream() {
      return this.items.stream().map(ItemStack::copy);
   }

   public Iterable<ItemStack> items() {
      return this.items;
   }

   public Iterable<ItemStack> itemsCopy() {
      return Lists.transform(this.items, ItemStack::copy);
   }

   public int size() {
      return this.items.size();
   }

   public Fraction weight() {
      return this.weight;
   }

   public boolean isEmpty() {
      return this.items.isEmpty();
   }

   public int getSelectedItem() {
      return this.selectedItem;
   }

   public boolean hasSelectedItem() {
      return this.selectedItem != -1;
   }

   @Override
   public boolean equals(final Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof BundleContents contents) ? false : this.weight.equals(contents.weight) && ItemStack.listMatches(this.items, contents.items);
      }
   }

   @Override
   public int hashCode() {
      return ItemStack.hashStackList(this.items);
   }

   @Override
   public String toString() {
      return "BundleContents" + this.items;
   }

   public static class Mutable {
      private final List<ItemStack> items;
      private Fraction weight;
      private int selectedItem;

      public Mutable(final BundleContents contents) {
         this.items = new ArrayList<>(contents.items);
         this.weight = contents.weight;
         this.selectedItem = contents.selectedItem;
      }

      public BundleContents.Mutable clearItems() {
         this.items.clear();
         this.weight = Fraction.ZERO;
         this.selectedItem = -1;
         return this;
      }

      private int findStackIndex(final ItemStack itemsToAdd) {
         if (!itemsToAdd.isStackable()) {
            return -1;
         }

         for (int i = 0; i < this.items.size(); i++) {
            if (ItemStack.isSameItemSameComponents(this.items.get(i), itemsToAdd)) {
               return i;
            }
         }

         return -1;
      }

      private int getMaxAmountToAdd(final ItemStack item) {
         Fraction remainingWeight = Fraction.ONE.subtract(this.weight);
         return Math.max(remainingWeight.divideBy(BundleContents.getWeight(item)).intValue(), 0);
      }

      public int tryInsert(final ItemStack itemsToAdd) {
         if (!BundleContents.canItemBeInBundle(itemsToAdd)) {
            return 0;
         }

         int amountToAdd = Math.min(itemsToAdd.getCount(), this.getMaxAmountToAdd(itemsToAdd));
         if (amountToAdd == 0) {
            return 0;
         }

         this.weight = this.weight.add(BundleContents.getWeight(itemsToAdd).multiplyBy(Fraction.getFraction(amountToAdd, 1)));
         int stackIndex = this.findStackIndex(itemsToAdd);
         if (stackIndex != -1) {
            ItemStack removedStack = this.items.remove(stackIndex);
            ItemStack mergedStack = removedStack.copyWithCount(removedStack.getCount() + amountToAdd);
            itemsToAdd.shrink(amountToAdd);
            this.items.add(0, mergedStack);
         } else {
            this.items.add(0, itemsToAdd.split(amountToAdd));
         }

         return amountToAdd;
      }

      public int tryTransfer(final Slot slot, final Player player) {
         ItemStack other = slot.getItem();
         int maxAmount = this.getMaxAmountToAdd(other);
         return BundleContents.canItemBeInBundle(other) ? this.tryInsert(slot.safeTake(other.getCount(), maxAmount, player)) : 0;
      }

      public void toggleSelectedItem(final int selectedItem) {
         this.selectedItem = this.selectedItem != selectedItem && !this.indexIsOutsideAllowedBounds(selectedItem) ? selectedItem : -1;
      }

      private boolean indexIsOutsideAllowedBounds(final int selectedItem) {
         return selectedItem < 0 || selectedItem >= this.items.size();
      }

      public @Nullable ItemStack removeOne() {
         if (this.items.isEmpty()) {
            return null;
         }

         int removeIndex = this.indexIsOutsideAllowedBounds(this.selectedItem) ? 0 : this.selectedItem;
         ItemStack stack = this.items.remove(removeIndex).copy();
         this.weight = this.weight.subtract(BundleContents.getWeight(stack).multiplyBy(Fraction.getFraction(stack.getCount(), 1)));
         this.toggleSelectedItem(-1);
         return stack;
      }

      public Fraction weight() {
         return this.weight;
      }

      public BundleContents toImmutable() {
         return new BundleContents(List.copyOf(this.items), this.weight, this.selectedItem);
      }
   }
}
