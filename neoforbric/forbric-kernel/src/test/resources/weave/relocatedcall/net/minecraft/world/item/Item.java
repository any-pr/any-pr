package net.minecraft.world.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** Fixture stand-in: the callee whose call a guest mixin wraps. */
public class Item {
	public InteractionResult useOn(UseOnContext context) {
		System.out.println("[RelocatedCall] Item.useOn ran");
		return new InteractionResult("item");
	}
}
