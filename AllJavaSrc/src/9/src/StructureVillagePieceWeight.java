package net.minecraft.src;

public class StructureVillagePieceWeight {
	public Class field_35607_a;
	public final int field_35605_b;
	public int field_35606_c;
	public int field_35604_d;

	public StructureVillagePieceWeight(Class var1, int var2, int var3) {
		this.field_35607_a = var1;
		this.field_35605_b = var2;
		this.field_35604_d = var3;
	}

	public boolean func_35602_a(int var1) {
		return this.field_35604_d == 0 || this.field_35606_c < this.field_35604_d;
	}

	public boolean func_35603_a() {
		return this.field_35604_d == 0 || this.field_35606_c < this.field_35604_d;
	}
}
