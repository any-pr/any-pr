package net.minecraft.src;

public class StructureVillagePieceWeight {
	public Class field_35496_a;
	public final int field_35494_b;
	public int field_35495_c;
	public int field_35493_d;

	public StructureVillagePieceWeight(Class var1, int var2, int var3) {
		this.field_35496_a = var1;
		this.field_35494_b = var2;
		this.field_35493_d = var3;
	}

	public boolean func_35491_a(int var1) {
		return this.field_35493_d == 0 || this.field_35495_c < this.field_35493_d;
	}

	public boolean func_35492_a() {
		return this.field_35493_d == 0 || this.field_35495_c < this.field_35493_d;
	}
}
