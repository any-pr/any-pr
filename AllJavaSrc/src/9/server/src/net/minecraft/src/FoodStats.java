package net.minecraft.src;

public class FoodStats {
	private int field_35595_a = 20;
	private float field_35593_b = 5.0F;
	private float field_35594_c;
	private int field_35591_d = 0;
	private int field_35592_e = 20;

	public void func_35590_a(int var1, float var2) {
		this.field_35595_a = Math.min(var1 + this.field_35595_a, 20);
		this.field_35593_b = Math.min(this.field_35593_b + (float)var1 * var2 * 2.0F, (float)this.field_35595_a);
	}

	public void func_35582_a(ItemFood var1) {
		this.func_35590_a(var1.getHealAmount(), var1.func_35424_l());
	}

	public void func_35584_a(EntityPlayer var1) {
		int var2 = var1.worldObj.difficultySetting;
		this.field_35592_e = this.field_35595_a;
		if(this.field_35594_c > 4.0F) {
			this.field_35594_c -= 4.0F;
			if(this.field_35593_b > 0.0F) {
				this.field_35593_b = Math.max(this.field_35593_b - 1.0F, 0.0F);
			} else if(var2 > 0) {
				this.field_35595_a = Math.max(this.field_35595_a - 1, 0);
			}
		}

		if(this.field_35595_a >= 18 && var1.func_35206_W()) {
			++this.field_35591_d;
			if(this.field_35591_d >= 80) {
				var1.heal(1);
				this.field_35591_d = 0;
			}
		} else if(this.field_35595_a <= 0) {
			++this.field_35591_d;
			if(this.field_35591_d >= 80) {
				if(var1.health > 10 || var2 >= 3 || var1.health > 1 && var2 >= 2) {
					var1.attackEntityFrom(DamageSource.field_35085_f, 1);
				}

				this.field_35591_d = 0;
			}
		} else {
			this.field_35591_d = 0;
		}

	}

	public void func_35589_a(NBTTagCompound var1) {
		if(var1.hasKey("foodLevel")) {
			this.field_35595_a = var1.getInteger("foodLevel");
			this.field_35591_d = var1.getInteger("foodTickTimer");
			this.field_35593_b = var1.getFloat("foodSaturationLevel");
			this.field_35594_c = var1.getFloat("foodExhaustionLevel");
		}

	}

	public void func_35588_b(NBTTagCompound var1) {
		var1.setInteger("foodLevel", this.field_35595_a);
		var1.setInteger("foodTickTimer", this.field_35591_d);
		var1.setFloat("foodSaturationLevel", this.field_35593_b);
		var1.setFloat("foodExhaustionLevel", this.field_35594_c);
	}

	public int func_35585_a() {
		return this.field_35595_a;
	}

	public boolean func_35587_b() {
		return this.field_35595_a < 20;
	}

	public void func_35583_a(float var1) {
		this.field_35594_c = Math.min(this.field_35594_c + var1, 40.0F);
	}

	public float func_35586_c() {
		return this.field_35593_b;
	}
}
