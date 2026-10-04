package net.minecraft.src;

public final class WorldSettings {
	private final long field_35523_a;
	private final int field_35521_b;
	private final boolean field_35522_c;

	public WorldSettings(long var1, int var3, boolean var4) {
		this.field_35523_a = var1;
		this.field_35521_b = var3;
		this.field_35522_c = var4;
	}

	public long func_35518_a() {
		return this.field_35523_a;
	}

	public int func_35519_b() {
		return this.field_35521_b;
	}

	public boolean func_35520_c() {
		return this.field_35522_c;
	}
}
