package net.minecraft.world.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

public class AirItem extends Item {
   public AirItem(final Block block, final Item.Properties properties) {
      super(properties);
   }

   @Override
   public Component getName(final ItemStack itemStack) {
      return this.getName();
   }
}
