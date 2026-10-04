package net.minecraft.commands.arguments.item;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemInput {
   private static final Dynamic2CommandExceptionType ERROR_STACK_TOO_BIG = new Dynamic2CommandExceptionType(
      (item, count) -> Component.translatableEscape("arguments.item.overstacked", item, count)
   );
   private final Holder<Item> item;
   private final DataComponentPatch components;

   public ItemInput(final Holder<Item> item, final DataComponentPatch components) {
      this.item = item;
      this.components = components;
   }

   public Item getItem() {
      return this.item.value();
   }

   public ItemStack createItemStack(final int count, final boolean checkSize) throws CommandSyntaxException {
      ItemStack result = new ItemStack(this.item, count);
      result.applyComponents(this.components);
      if (checkSize && count > result.getMaxStackSize()) {
         throw ERROR_STACK_TOO_BIG.create(this.getItemName(), result.getMaxStackSize());
      } else {
         return result;
      }
   }

   public String serialize(final HolderLookup.Provider registries) {
      StringBuilder result = new StringBuilder(this.getItemName());
      String serializedComponents = this.serializeComponents(registries);
      if (!serializedComponents.isEmpty()) {
         result.append('[');
         result.append(serializedComponents);
         result.append(']');
      }

      return result.toString();
   }

   private String serializeComponents(final HolderLookup.Provider registries) {
      DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
      return this.components.entrySet().stream().flatMap(entry -> {
         DataComponentType<?> type = entry.getKey();
         Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
         if (key == null) {
            return Stream.empty();
         } else {
            Optional<?> value = entry.getValue();
            if (value.isPresent()) {
               TypedDataComponent<?> typedComponent = TypedDataComponent.createUnchecked(type, value.get());
               return typedComponent.encodeValue(ops).result().stream().map(tag -> key.toString() + "=" + tag);
            } else {
               return Stream.of("!" + key.toString());
            }
         }
      }).collect(Collectors.joining(String.valueOf(',')));
   }

   private String getItemName() {
      return this.item.unwrapKey().map(ResourceKey::identifier).orElseGet(() -> "unknown[" + this.item + "]").toString();
   }
}
