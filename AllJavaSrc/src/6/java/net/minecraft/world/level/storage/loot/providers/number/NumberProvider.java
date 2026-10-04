package net.minecraft.world.level.storage.loot.providers.number;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextUser;

public interface NumberProvider extends LootContextUser {
   float getFloat(final LootContext context);

   default int getInt(final LootContext context) {
      return Math.round(this.getFloat(context));
   }

   LootNumberProviderType getType();
}
