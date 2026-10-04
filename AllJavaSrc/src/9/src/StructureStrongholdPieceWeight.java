package net.minecraft.src;

class StructureStrongholdPieceWeight {
	public Class field_35618_a;
	public final int field_35616_b;
	public int field_35617_c;
	public int field_35615_d;

	public StructureStrongholdPieceWeight(Class var1, int var2, int var3) {
		this.field_35618_a = var1;
		this.field_35616_b = var2;
		this.field_35615_d = var3;
	}

	public boolean func_35613_a(int var1) {
		return this.field_35615_d == 0 || this.field_35617_c < this.field_35615_d;
	}

	public boolean func_35614_a() {
		return this.field_35615_d == 0 || this.field_35617_c < this.field_35615_d;
	}
}
