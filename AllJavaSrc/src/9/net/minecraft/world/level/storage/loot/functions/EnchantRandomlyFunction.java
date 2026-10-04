package net.minecraft.world.level.storage.loot.functions;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.slf4j.Logger;

public class EnchantRandomlyFunction extends LootItemConditionalFunction {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final MapCodec<EnchantRandomlyFunction> CODEC = RecordCodecBuilder.mapCodec(
      i -> commonFields(i)
         .and(
            i.group(
               RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).optionalFieldOf("options").forGetter(f -> f.options),
               Codec.BOOL.optionalFieldOf("only_compatible", true).forGetter(f -> f.onlyCompatible)
            )
         )
         .apply(i, EnchantRandomlyFunction::new)
   );
   private final Optional<HolderSet<Enchantment>> options;
   private final boolean onlyCompatible;

   private EnchantRandomlyFunction(final List<LootItemCondition> predicates, final Optional<HolderSet<Enchantment>> options, final boolean onlyCompatible) {
      super(predicates);
      this.options = options;
      this.onlyCompatible = onlyCompatible;
   }

   @Override
   public LootItemFunctionType<EnchantRandomlyFunction> getType() {
      return LootItemFunctions.ENCHANT_RANDOMLY;
   }

   @Override
   public ItemStack run(final ItemStack itemStack, final LootContext context) {
      RandomSource random = context.getRandom();
      boolean targetIsBook = itemStack.is(Items.BOOK);
      boolean shouldCheckCompatibility = !targetIsBook && this.onlyCompatible;
      Stream<Holder<Enchantment>> compatibleEnchantmentsStream = this.options
         .map(HolderSet::stream)
         .orElseGet(() -> context.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().map(Function.identity()))
         .filter(candidate -> !shouldCheckCompatibility || candidate.value().canEnchant(itemStack));
      List<Holder<Enchantment>> compatibleEnchantments = compatibleEnchantmentsStream.toList();
      Optional<Holder<Enchantment>> enchantment = Util.getRandomSafe(compatibleEnchantments, random);
      if (enchantment.isEmpty()) {
         LOGGER.warn("Couldn't find a compatible enchantment for {}", itemStack);
         return itemStack;
      } else {
         return enchantItem(itemStack, enchantment.get(), random);
      }
   }

   private static ItemStack enchantItem(ItemStack itemStack, final Holder<Enchantment> enchantment, final RandomSource random) {
      int level = Mth.nextInt(random, enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
      if (itemStack.is(Items.BOOK)) {
         itemStack = new ItemStack(Items.ENCHANTED_BOOK);
      }

      itemStack.enchant(enchantment, level);
      return itemStack;
   }

   public static EnchantRandomlyFunction.Builder randomEnchantment() {
      return new EnchantRandomlyFunction.Builder();
   }

   public static EnchantRandomlyFunction.Builder randomApplicableEnchantment(final HolderLookup.Provider registries) {
      return randomEnchantment().withOneOf(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.ON_RANDOM_LOOT));
   }

   public static class Builder extends LootItemConditionalFunction.Builder<EnchantRandomlyFunction.Builder> {
      private Optional<HolderSet<Enchantment>> options = Optional.empty();
      private boolean onlyCompatible = true;

      protected EnchantRandomlyFunction.Builder getThis() {
         return this;
      }

      public EnchantRandomlyFunction.Builder withEnchantment(final Holder<Enchantment> enchantment) {
         this.options = Optional.of(HolderSet.direct(enchantment));
         return this;
      }

      public EnchantRandomlyFunction.Builder withOneOf(final HolderSet<Enchantment> enchantments) {
         this.options = Optional.of(enchantments);
         return this;
      }

      public EnchantRandomlyFunction.Builder allowingIncompatibleEnchantments() {
         this.onlyCompatible = false;
         return this;
      }

      @Override
      public LootItemFunction build() {
         return new EnchantRandomlyFunction(this.getConditions(), this.options, this.onlyCompatible);
      }
   }
}
