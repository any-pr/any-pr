package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.apache.commons.lang3.function.TriFunction;

public record WeatheringCopperBlocks(
   Block unaffected, Block exposed, Block weathered, Block oxidized, Block waxed, Block waxedExposed, Block waxedWeathered, Block waxedOxidized
) {
   public static <WaxedBlock extends Block, WeatheringBlock extends Block & WeatheringCopper> WeatheringCopperBlocks create(
      final String id,
      final TriFunction<String, Function<BlockBehaviour.Properties, Block>, BlockBehaviour.Properties, Block> register,
      final Function<BlockBehaviour.Properties, WaxedBlock> waxedBlockFactory,
      final BiFunction<WeatheringCopper.WeatherState, BlockBehaviour.Properties, WeatheringBlock> weatheringFactory,
      final Function<WeatheringCopper.WeatherState, BlockBehaviour.Properties> propertiesSupplier
   ) {
      return new WeatheringCopperBlocks(
         (Block)register.apply(
            id,
            (Function<BlockBehaviour.Properties, Block>)p -> weatheringFactory.apply(WeatheringCopper.WeatherState.UNAFFECTED, p),
            propertiesSupplier.apply(WeatheringCopper.WeatherState.UNAFFECTED)
         ),
         (Block)register.apply(
            "exposed_" + id,
            (Function<BlockBehaviour.Properties, Block>)p -> weatheringFactory.apply(WeatheringCopper.WeatherState.EXPOSED, p),
            propertiesSupplier.apply(WeatheringCopper.WeatherState.EXPOSED)
         ),
         (Block)register.apply(
            "weathered_" + id,
            (Function<BlockBehaviour.Properties, Block>)p -> weatheringFactory.apply(WeatheringCopper.WeatherState.WEATHERED, p),
            propertiesSupplier.apply(WeatheringCopper.WeatherState.WEATHERED)
         ),
         (Block)register.apply(
            "oxidized_" + id,
            (Function<BlockBehaviour.Properties, Block>)p -> weatheringFactory.apply(WeatheringCopper.WeatherState.OXIDIZED, p),
            propertiesSupplier.apply(WeatheringCopper.WeatherState.OXIDIZED)
         ),
         (Block)register.apply("waxed_" + id, waxedBlockFactory::apply, propertiesSupplier.apply(WeatheringCopper.WeatherState.UNAFFECTED)),
         (Block)register.apply("waxed_exposed_" + id, waxedBlockFactory::apply, propertiesSupplier.apply(WeatheringCopper.WeatherState.EXPOSED)),
         (Block)register.apply("waxed_weathered_" + id, waxedBlockFactory::apply, propertiesSupplier.apply(WeatheringCopper.WeatherState.WEATHERED)),
         (Block)register.apply("waxed_oxidized_" + id, waxedBlockFactory::apply, propertiesSupplier.apply(WeatheringCopper.WeatherState.OXIDIZED))
      );
   }

   public ImmutableBiMap<Block, Block> weatheringMapping() {
      return ImmutableBiMap.of(this.unaffected, this.exposed, this.exposed, this.weathered, this.weathered, this.oxidized);
   }

   public ImmutableBiMap<Block, Block> waxedMapping() {
      return ImmutableBiMap.of(
         this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized
      );
   }

   public ImmutableList<Block> asList() {
      return ImmutableList.of(
         this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized
      );
   }

   public void forEach(final Consumer<Block> consumer) {
      consumer.accept(this.unaffected);
      consumer.accept(this.exposed);
      consumer.accept(this.weathered);
      consumer.accept(this.oxidized);
      consumer.accept(this.waxed);
      consumer.accept(this.waxedExposed);
      consumer.accept(this.waxedWeathered);
      consumer.accept(this.waxedOxidized);
   }
}
