package net.minecraft.world.item;

import java.util.Set;

import net.neoforged.neoforge.common.ItemAbilities;

/** Fixture stand-in: vanilla shears declare the carving ability; a stick declares nothing. */
public final class Items {
	public static final Item SHEARS = new Item("minecraft:shears", Set.of(ItemAbilities.SHEARS_CARVE), Set.of("c:tools/shear"));
	public static final Item STICK = new Item("minecraft:stick", Set.of(), Set.of());

	private Items() {
	}
}
