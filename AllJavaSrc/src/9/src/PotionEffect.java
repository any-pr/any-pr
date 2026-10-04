package net.minecraft.src;

public class PotionEffect {
	private int field_35805_a;
	private int field_35803_b;
	private int field_35804_c;

	public PotionEffect(int var1, int var2, int var3) {
		this.field_35805_a = var1;
		this.field_35803_b = var2;
		this.field_35804_c = var3;
	}

	public void func_35796_a(PotionEffect var1) {
		if(this.field_35805_a != var1.field_35805_a) {
			System.err.println("This method should only be called for matching effects!");
		}

		if(var1.field_35804_c >= this.field_35804_c) {
			this.field_35804_c = var1.field_35804_c;
			this.field_35803_b = var1.field_35803_b;
		}

	}

	public int func_35799_a() {
		return this.field_35805_a;
	}

	public int func_35802_b() {
		return this.field_35803_b;
	}

	public int func_35801_c() {
		return this.field_35804_c;
	}

	public boolean func_35798_a(EntityLiving var1) {
		if(this.field_35803_b > 0) {
			if(Potion.field_35678_a[this.field_35805_a].func_35660_a(this.field_35803_b, this.field_35804_c)) {
				this.func_35800_b(var1);
			}

			this.func_35797_d();
		}

		return this.field_35803_b > 0;
	}

	private int func_35797_d() {
		return --this.field_35803_b;
	}

	public void func_35800_b(EntityLiving var1) {
		if(this.field_35803_b > 0) {
			Potion.field_35678_a[this.field_35805_a].func_35662_a(var1, this.field_35804_c);
		}

	}

	public int hashCode() {
		return this.field_35805_a;
	}
}
