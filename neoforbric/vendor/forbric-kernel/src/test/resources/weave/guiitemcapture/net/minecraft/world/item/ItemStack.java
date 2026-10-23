package net.minecraft.world.item;

/** Fixture stand-in: a named stack, empty when it has no name. */
public final class ItemStack {
	public static final ItemStack EMPTY = new ItemStack("");
	private final String item;

	public ItemStack(String item) {
		this.item = item;
	}

	public boolean isEmpty() {
		return item.isEmpty();
	}

	@Override
	public String toString() {
		return item;
	}
}
