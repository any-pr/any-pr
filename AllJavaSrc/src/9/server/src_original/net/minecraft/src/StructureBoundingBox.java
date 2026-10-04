package net.minecraft.src;

public class StructureBoundingBox {
	public int field_35678_a;
	public int field_35676_b;
	public int field_35677_c;
	public int field_35674_d;
	public int field_35675_e;
	public int field_35673_f;

	public StructureBoundingBox() {
	}

	public static StructureBoundingBox func_35672_a() {
		return new StructureBoundingBox(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
	}

	public static StructureBoundingBox func_35663_a(int var0, int var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8, int var9) {
		switch(var9) {
		case 0:
			return new StructureBoundingBox(var0 + var3, var1 + var4, var2 + var5, var0 + var6 - 1 + var3, var1 + var7 - 1 + var4, var2 + var8 - 1 + var5);
		case 1:
			return new StructureBoundingBox(var0 - var8 + 1 + var5, var1 + var4, var2 + var3, var0 + var5, var1 + var7 - 1 + var4, var2 + var6 - 1 + var3);
		case 2:
			return new StructureBoundingBox(var0 + var3, var1 + var4, var2 - var8 + 1 + var5, var0 + var6 - 1 + var3, var1 + var7 - 1 + var4, var2 + var5);
		case 3:
			return new StructureBoundingBox(var0 + var5, var1 + var4, var2 + var3, var0 + var8 - 1 + var5, var1 + var7 - 1 + var4, var2 + var6 - 1 + var3);
		default:
			return new StructureBoundingBox(var0 + var3, var1 + var4, var2 + var5, var0 + var6 - 1 + var3, var1 + var7 - 1 + var4, var2 + var8 - 1 + var5);
		}
	}

	public StructureBoundingBox(StructureBoundingBox var1) {
		this.field_35678_a = var1.field_35678_a;
		this.field_35676_b = var1.field_35676_b;
		this.field_35677_c = var1.field_35677_c;
		this.field_35674_d = var1.field_35674_d;
		this.field_35675_e = var1.field_35675_e;
		this.field_35673_f = var1.field_35673_f;
	}

	public StructureBoundingBox(int var1, int var2, int var3, int var4, int var5, int var6) {
		this.field_35678_a = var1;
		this.field_35676_b = var2;
		this.field_35677_c = var3;
		this.field_35674_d = var4;
		this.field_35675_e = var5;
		this.field_35673_f = var6;
	}

	public StructureBoundingBox(int var1, int var2, int var3, int var4) {
		this.field_35678_a = var1;
		this.field_35676_b = 0;
		this.field_35677_c = var2;
		this.field_35674_d = var3;
		this.field_35675_e = 65536;
		this.field_35673_f = var4;
	}

	public boolean func_35664_a(StructureBoundingBox var1) {
		return this.field_35674_d >= var1.field_35678_a && this.field_35678_a <= var1.field_35674_d && this.field_35673_f >= var1.field_35677_c && this.field_35677_c <= var1.field_35673_f && this.field_35675_e >= var1.field_35676_b && this.field_35676_b <= var1.field_35675_e;
	}

	public boolean func_35671_a(int var1, int var2, int var3, int var4) {
		return this.field_35674_d >= var1 && this.field_35678_a <= var3 && this.field_35673_f >= var2 && this.field_35677_c <= var4;
	}

	public void func_35666_b(StructureBoundingBox var1) {
		this.field_35678_a = Math.min(this.field_35678_a, var1.field_35678_a);
		this.field_35676_b = Math.min(this.field_35676_b, var1.field_35676_b);
		this.field_35677_c = Math.min(this.field_35677_c, var1.field_35677_c);
		this.field_35674_d = Math.max(this.field_35674_d, var1.field_35674_d);
		this.field_35675_e = Math.max(this.field_35675_e, var1.field_35675_e);
		this.field_35673_f = Math.max(this.field_35673_f, var1.field_35673_f);
	}

	public void func_35670_a(int var1, int var2, int var3) {
		this.field_35678_a += var1;
		this.field_35676_b += var2;
		this.field_35677_c += var3;
		this.field_35674_d += var1;
		this.field_35675_e += var2;
		this.field_35673_f += var3;
	}

	public boolean func_35667_b(int var1, int var2, int var3) {
		return var1 >= this.field_35678_a && var1 <= this.field_35674_d && var3 >= this.field_35677_c && var3 <= this.field_35673_f && var2 >= this.field_35676_b && var2 <= this.field_35675_e;
	}

	public int func_35669_b() {
		return this.field_35674_d - this.field_35678_a + 1;
	}

	public int func_35668_c() {
		return this.field_35675_e - this.field_35676_b + 1;
	}

	public int func_35665_d() {
		return this.field_35673_f - this.field_35677_c + 1;
	}

	public String toString() {
		return "(" + this.field_35678_a + ", " + this.field_35676_b + ", " + this.field_35677_c + "; " + this.field_35674_d + ", " + this.field_35675_e + ", " + this.field_35673_f + ")";
	}
}
