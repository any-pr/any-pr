package net.minecraft.world.level.storage.loot.providers.number;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;

public record BinomialDistributionGenerator(NumberProvider n, NumberProvider p) implements NumberProvider {
   public static final MapCodec<BinomialDistributionGenerator> CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(
            NumberProviders.CODEC.fieldOf("n").forGetter(BinomialDistributionGenerator::n),
            NumberProviders.CODEC.fieldOf("p").forGetter(BinomialDistributionGenerator::p)
         )
         .apply(i, BinomialDistributionGenerator::new)
   );

   @Override
   public LootNumberProviderType getType() {
      return NumberProviders.BINOMIAL;
   }

   @Override
   public int getInt(final LootContext context) {
      int n = this.n.getInt(context);
      float p = this.p.getFloat(context);
      RandomSource random = context.getRandom();
      int result = 0;

      for (int i = 0; i < n; i++) {
         if (random.nextFloat() < p) {
            result++;
         }
      }

      return result;
   }

   @Override
   public float getFloat(final LootContext context) {
      return this.getInt(context);
   }

   public static BinomialDistributionGenerator binomial(final int n, final float p) {
      return new BinomialDistributionGenerator(ConstantValue.exactly(n), ConstantValue.exactly(p));
   }

   @Override
   public Set<ContextKey<?>> getReferencedContextParams() {
      return Sets.union(this.n.getReferencedContextParams(), this.p.getReferencedContextParams());
   }
}
