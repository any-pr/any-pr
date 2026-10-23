package net.minecraft.nbt;

/** A stand-in: only a name, so a handler that was handed the right compound can say which one it got. */
public class CompoundTag {
	private final String name;

	public CompoundTag(String name) {
		this.name = name;
	}

	public String name() {
		return name;
	}
}
