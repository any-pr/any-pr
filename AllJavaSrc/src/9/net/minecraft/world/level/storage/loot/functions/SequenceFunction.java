package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public class SequenceFunction implements LootItemFunction {
   public static final MapCodec<SequenceFunction> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(LootItemFunctions.TYPED_CODEC.listOf().fieldOf("functions").forGetter(f -> f.functions)).apply(i, SequenceFunction::new)
   );
   public static final Codec<SequenceFunction> INLINE_CODEC = LootItemFunctions.TYPED_CODEC.listOf().xmap(SequenceFunction::new, f -> f.functions);
   private final List<LootItemFunction> functions;
   private final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;

   private SequenceFunction(final List<LootItemFunction> functions) {
      this.functions = functions;
      this.compositeFunction = LootItemFunctions.compose(functions);
   }

   public static SequenceFunction of(final List<LootItemFunction> functions) {
      return new SequenceFunction(List.copyOf(functions));
   }

   public ItemStack apply(final ItemStack stack, final LootContext context) {
      return this.compositeFunction.apply(stack, context);
   }

   @Override
   public void validate(final ValidationContext output) {
      LootItemFunction.super.validate(output);

      for (int i = 0; i < this.functions.size(); i++) {
         this.functions.get(i).validate(output.forChild(new ProblemReporter.IndexedFieldPathElement("functions", i)));
      }
   }

   @Override
   public LootItemFunctionType<SequenceFunction> getType() {
      return LootItemFunctions.SEQUENCE;
   }
}
