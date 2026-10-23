package fixture.defaultconflict.mixin;

import fixture.defaultconflict.FabricItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

/** Puts the mod's extension on vanilla's Item: the shape fabric-item-api's injected interface leaves it in. */
@Mixin(Item.class)
public abstract class ItemMixin implements FabricItem {
}
