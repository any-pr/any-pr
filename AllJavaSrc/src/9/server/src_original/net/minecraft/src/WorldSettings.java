package net.minecraft.src;

public final class WorldSettings {
	private final long field_35043_a;
	private final int field_35041_b;
	private final boolean field_35042_c;

	public WorldSettings(long var1, int var3, boolean var4) {
		this.field_35043_a = var1;
		this.field_35041_b = var3;
		this.field_35042_c = var4;
	}

	public long func_35038_a() {
		return this.field_35043_a;
	}

	public int func_35039_b() {
		return this.field_35041_b;
	}

	public boolean func_35040_c() {
		return this.field_35042_c;
	}

	public static int func_35037_a(int var0) {
		switch(var0) {
		case 0:
		case 1:
			return var0;
		default:
			return 0;
		}
	}
}
