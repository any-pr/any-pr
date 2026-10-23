package fixture.fabricenchantment;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Enchants a plain sword and a Fabric item whose own answer also accepts {@code soulbound}, by command and on the
 * anvil. Natively only {@code sharpness} fits either.
 */
public class Probe {
	public String run() {
		Holder<Enchantment> sharpness = new Holder<>(new Enchantment("sharpness"));
		Holder<Enchantment> soulbound = new Holder<>(new Enchantment("soulbound"));
		List<Entity> holders = List.of(new Entity(new ItemStack(sword())), new Entity(new ItemStack(new SoulItem())));
		int bySharpness = EnchantCommand.enchant(new CommandSourceStack(), holders, sharpness, 1);
		int bySoulbound = EnchantCommand.enchant(new CommandSourceStack(), holders, soulbound, 1);

		AnvilMenu anvil = new AnvilMenu();
		anvil.input = new ItemStack(new SoulItem());
		anvil.book = soulbound;
		anvil.createResult();
		return "command sharpness=" + bySharpness + " soulbound=" + bySoulbound + " anvil=" + anvil.result;
	}

	private static Item sword() {
		return new Item("sword", Set.of("sharpness"));
	}

	/** A Fabric item that accepts one enchantment the native rules do not. */
	static final class SoulItem extends Item {
		SoulItem() {
			super("soul_blade", Set.of("sharpness"));
		}

		@Override
		public boolean canBeEnchantedWith(ItemStack stack, Holder<Enchantment> enchantment, EnchantingContext context) {
			return enchantment.value().name().equals("soulbound") || super.canBeEnchantedWith(stack, enchantment, context);
		}
	}
}
