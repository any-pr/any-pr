package net.minecraft.src;

public class FoodStats {
	private int field_35776_a = 20;
	private float field_35774_b = 5.0F;
	private float field_35775_c;
	private int field_35772_d = 0;
	private int field_35773_e = 20;

	public void func_35771_a(int var1, float var2) {
		this.field_35776_a = Math.min(var1 + this.field_35776_a, 20);
		this.field_35774_b = Math.min(this.field_35774_b + (float)var1 * var2 * 2.0F, (float)this.field_35776_a);
	}

	public void func_35761_a(ItemFood var1) {
		this.func_35771_a(var1.getHealAmount(), var1.func_35426_m());
	}

	public void func_35768_a(EntityPlayer var1) {
		int var2 = var1.worldObj.difficultySetting;
		this.field_35773_e = this.field_35776_a;
		if(this.field_35775_c > 4.0F) {
			this.field_35775_c -= 4.0F;
			if(this.field_35774_b > 0.0F) {
				this.field_35774_b = Math.max(this.field_35774_b - 1.0F, 0.0F);
			} else if(var2 > 0) {
				this.field_35776_a = Math.max(this.field_35776_a - 1, 0);
			}
		}

		if(this.field_35776_a >= 18 && var1.func_35194_au()) {
			++this.field_35772_d;
			if(this.field_35772_d >= 80) {
				var1.heal(1);
				this.field_35772_d = 0;
			}
		} else if(this.field_35776_a <= 0) {
			++this.field_35772_d;
			if(this.field_35772_d >= 80) {
				if(var1.health > 10 || var2 >= 3 || var1.health > 1 && var2 >= 2) {
					var1.attackEntityFrom(DamageSource.field_35536_f, 1);
				}

				this.field_35772_d = 0;
			}
		} else {
			this.field_35772_d = 0;
		}

	}

	public void func_35766_a(NBTTagCompound var1) {
		if(var1.hasKey("foodLevel")) {
			this.field_35776_a = var1.getInteger("foodLevel");
			this.field_35772_d = var1.getInteger("foodTickTimer");
			this.field_35774_b = var1.getFloat("foodSaturationLevel");
			this.field_35775_c = var1.getFloat("foodExhaustionLevel");
		}

	}

	public void func_35763_b(NBTTagCompound var1) {
		var1.setInteger("foodLevel", this.field_35776_a);
		var1.setInteger("foodTickTimer", this.field_35772_d);
		var1.setFloat("foodSaturationLevel", this.field_35774_b);
		var1.setFloat("foodExhaustionLevel", this.field_35775_c);
	}

	public int func_35765_a() {
		return this.field_35776_a;
	}

	public int func_35769_b() {
		return this.field_35773_e;
	}

	public boolean func_35770_c() {
		return this.field_35776_a < 20;
	}

	public void func_35762_a(float var1) {
		this.field_35775_c = Math.min(this.field_35775_c + var1, 40.0F);
	}

	public float func_35760_d() {
		return this.field_35774_b;
	}

	public void func_35764_a(int var1) {
		this.field_35776_a = var1;
	}

	public void func_35767_b(float var1) {
		this.field_35774_b = var1;
	}
}
