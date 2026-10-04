package net.minecraft.world.item;

import com.google.common.collect.ImmutableBiMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopperBlocks;

public record WeatheringCopperItems(
   Item unaffected, Item exposed, Item weathered, Item oxidized, Item waxed, Item waxedExposed, Item waxedWeathered, Item waxedOxidized
) {
   public static WeatheringCopperItems create(final WeatheringCopperBlocks blocks, final Function<Block, Item> itemFactory) {
      return new WeatheringCopperItems(
         itemFactory.apply(blocks.unaffected()),
         itemFactory.apply(blocks.exposed()),
         itemFactory.apply(blocks.weathered()),
         itemFactory.apply(blocks.oxidized()),
         itemFactory.apply(blocks.waxed()),
         itemFactory.apply(blocks.waxedExposed()),
         itemFactory.apply(blocks.waxedWeathered()),
         itemFactory.apply(blocks.waxedOxidized())
      );
   }

   public ImmutableBiMap<Item, Item> waxedMapping() {
      return ImmutableBiMap.of(
         this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized
      );
   }

   public void forEach(final Consumer<Item> consumer) {
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
