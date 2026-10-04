package net.minecraft.src;

public class Potion {
	public static final Potion[] field_35678_a = new Potion[32];
	public static final Potion field_35676_b = null;
	public static final Potion field_35677_c = (new Potion(1)).func_35661_a("potion.moveSpeed");
	public static final Potion field_35674_d = (new Potion(2)).func_35661_a("potion.moveSlowdown");
	public static final Potion field_35675_e = (new Potion(3)).func_35661_a("potion.digSpeed");
	public static final Potion field_35672_f = (new Potion(4)).func_35661_a("potion.digSlowDown");
	public static final Potion field_35673_g = (new Potion(5)).func_35661_a("potion.damageBoost");
	public static final Potion field_35685_h = (new PotionHealth(6)).func_35661_a("potion.heal");
	public static final Potion field_35686_i = (new PotionHealth(7)).func_35661_a("potion.harm");
	public static final Potion field_35683_j = (new Potion(8)).func_35661_a("potion.jump");
	public static final Potion field_35684_k = (new Potion(9)).func_35661_a("potion.confusion");
	public static final Potion field_35681_l = (new Potion(10)).func_35661_a("potion.regeneration");
	public static final Potion field_35682_m = (new Potion(11)).func_35661_a("potion.resistance");
	public static final Potion field_35679_n = (new Potion(12)).func_35661_a("potion.fireResistance");
	public static final Potion field_35680_o = (new Potion(13)).func_35661_a("potion.waterBreathing");
	public static final Potion field_35694_p = (new Potion(14)).func_35661_a("potion.invisibility");
	public static final Potion field_35693_q = (new Potion(15)).func_35661_a("potion.blindness");
	public static final Potion field_35692_r = (new Potion(16)).func_35661_a("potion.nightVision");
	public static final Potion field_35691_s = (new Potion(17)).func_35661_a("potion.hunger");
	public static final Potion field_35690_t = (new Potion(18)).func_35661_a("potion.weakness");
	public static final Potion field_35689_u = (new Potion(19)).func_35661_a("potion.poison");
	public static final Potion field_35688_v = null;
	public static final Potion field_35687_w = null;
	public static final Potion field_35697_x = null;
	public static final Potion field_35696_y = null;
	public static final Potion field_35695_z = null;
	public static final Potion field_35667_A = null;
	public static final Potion field_35668_B = null;
	public static final Potion field_35669_C = null;
	public static final Potion field_35663_D = null;
	public static final Potion field_35664_E = null;
	public static final Potion field_35665_F = null;
	public static final Potion field_35666_G = null;
	public final int field_35670_H;
	private String field_35671_I = "";

	protected Potion(int var1) {
		this.field_35670_H = var1;
		field_35678_a[var1] = this;
	}

	public void func_35662_a(EntityLiving var1, int var2) {
		if(this.field_35670_H == field_35681_l.field_35670_H) {
			if(var1.health < 20) {
				var1.heal(1);
			}
		} else if(this.field_35670_H == field_35689_u.field_35670_H) {
			if(var1.health > 1) {
				var1.attackEntityFrom(DamageSource.field_35545_l, 1);
			}
		} else if(this.field_35670_H == field_35691_s.field_35670_H && var1 instanceof EntityPlayer) {
			((EntityPlayer)var1).func_35198_d(0.025F * (float)(var2 + 1));
		} else if(this.field_35670_H == field_35685_h.field_35670_H) {
			var1.heal(4 << var2);
		} else if(this.field_35670_H == field_35686_i.field_35670_H) {
			var1.attackEntityFrom(DamageSource.field_35545_l, 4 << var2);
		}

	}

	public boolean func_35660_a(int var1, int var2) {
		if(this.field_35670_H != field_35681_l.field_35670_H && this.field_35670_H != field_35689_u.field_35670_H) {
			return this.field_35670_H == field_35691_s.field_35670_H;
		} else {
			int var3 = 25 >> var2;
			return var3 > 0 ? var1 % var3 == 0 : true;
		}
	}

	public Potion func_35661_a(String var1) {
		this.field_35671_I = var1;
		return this;
	}
}
