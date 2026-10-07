package net.minecraft.world.item;

/** Fixture stand-in: a stack is just the text of how it was made. */
public final class ItemStack {
	private final String made;

	public ItemStack(String made) {
		this.made = made;
	}

	@Override
	public String toString() {
		return made;
	}
}
