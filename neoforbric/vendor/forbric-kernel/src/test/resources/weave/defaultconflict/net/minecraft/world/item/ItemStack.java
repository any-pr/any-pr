package net.minecraft.world.item;

public final class ItemStack implements ItemInstance {
	private final String id;

	public ItemStack(String id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return id;
	}
}
