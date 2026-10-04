package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

public record RuleBasedBlockStateProvider(BlockStateProvider fallback, List<RuleBasedBlockStateProvider.Rule> rules) {
   public static final Codec<RuleBasedBlockStateProvider> CODEC = RecordCodecBuilder.create(
      i -> i.group(
            BlockStateProvider.CODEC.fieldOf("fallback").forGetter(RuleBasedBlockStateProvider::fallback),
            RuleBasedBlockStateProvider.Rule.CODEC.listOf().fieldOf("rules").forGetter(RuleBasedBlockStateProvider::rules)
         )
         .apply(i, RuleBasedBlockStateProvider::new)
   );

   public static RuleBasedBlockStateProvider simple(final BlockStateProvider provider) {
      return new RuleBasedBlockStateProvider(provider, List.of());
   }

   public static RuleBasedBlockStateProvider simple(final Block block) {
      return simple(BlockStateProvider.simple(block));
   }

   public BlockState getState(final WorldGenLevel level, final RandomSource random, final BlockPos pos) {
      for (RuleBasedBlockStateProvider.Rule rule : this.rules) {
         if (rule.ifTrue().test(level, pos)) {
            return rule.then().getState(random, pos);
         }
      }

      return this.fallback.getState(random, pos);
   }

   public record Rule(BlockPredicate ifTrue, BlockStateProvider then) {
      public static final Codec<RuleBasedBlockStateProvider.Rule> CODEC = RecordCodecBuilder.create(
         i -> i.group(
               BlockPredicate.CODEC.fieldOf("if_true").forGetter(RuleBasedBlockStateProvider.Rule::ifTrue),
               BlockStateProvider.CODEC.fieldOf("then").forGetter(RuleBasedBlockStateProvider.Rule::then)
            )
            .apply(i, RuleBasedBlockStateProvider.Rule::new)
      );
   }
}
