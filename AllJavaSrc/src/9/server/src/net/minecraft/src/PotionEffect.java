package net.minecraft.src;

public class PotionEffect {
	private int field_35656_a;
	private int field_35654_b;
	private int field_35655_c;

	public PotionEffect(int var1, int var2, int var3) {
		this.field_35656_a = var1;
		this.field_35654_b = var2;
		this.field_35655_c = var3;
	}

	public void func_35650_a(PotionEffect var1) {
		if(this.field_35656_a != var1.field_35656_a) {
			System.err.println("This method should only be called for matching effects!");
		}

		if(var1.field_35655_c >= this.field_35655_c) {
			this.field_35655_c = var1.field_35655_c;
			this.field_35654_b = var1.field_35654_b;
		}

	}

	public int func_35649_a() {
		return this.field_35656_a;
	}

	public int func_35653_b() {
		return this.field_35654_b;
	}

	public int func_35652_c() {
		return this.field_35655_c;
	}

	public boolean func_35648_a(EntityLiving var1) {
		if(this.field_35654_b > 0) {
			if(Potion.field_35455_a[this.field_35656_a].func_35437_a(this.field_35654_b, this.field_35655_c)) {
				this.func_35651_b(var1);
			}

			this.func_35647_d();
		}

		return this.field_35654_b > 0;
	}

	private int func_35647_d() {
		return --this.field_35654_b;
	}

	public void func_35651_b(EntityLiving var1) {
		if(this.field_35654_b > 0) {
			Potion.field_35455_a[this.field_35656_a].func_35438_a(var1, this.field_35655_c);
		}

	}

	public int hashCode() {
		return this.field_35656_a;
	}
}
