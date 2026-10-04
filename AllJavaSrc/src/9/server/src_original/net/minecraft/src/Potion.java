package net.minecraft.src;

public class Potion {
	public static final Potion[] field_35455_a = new Potion[32];
	public static final Potion field_35453_b = null;
	public static final Potion field_35454_c = (new Potion(1)).func_35439_a("potion.moveSpeed");
	public static final Potion field_35451_d = (new Potion(2)).func_35439_a("potion.moveSlowdown");
	public static final Potion field_35452_e = (new Potion(3)).func_35439_a("potion.digSpeed");
	public static final Potion field_35449_f = (new Potion(4)).func_35439_a("potion.digSlowDown");
	public static final Potion field_35450_g = (new Potion(5)).func_35439_a("potion.damageBoost");
	public static final Potion field_35462_h = (new PotionHealth(6)).func_35439_a("potion.heal");
	public static final Potion field_35463_i = (new PotionHealth(7)).func_35439_a("potion.harm");
	public static final Potion field_35460_j = (new Potion(8)).func_35439_a("potion.jump");
	public static final Potion field_35461_k = (new Potion(9)).func_35439_a("potion.confusion");
	public static final Potion field_35458_l = (new Potion(10)).func_35439_a("potion.regeneration");
	public static final Potion field_35459_m = (new Potion(11)).func_35439_a("potion.resistance");
	public static final Potion field_35456_n = (new Potion(12)).func_35439_a("potion.fireResistance");
	public static final Potion field_35457_o = (new Potion(13)).func_35439_a("potion.waterBreathing");
	public static final Potion field_35471_p = (new Potion(14)).func_35439_a("potion.invisibility");
	public static final Potion field_35470_q = (new Potion(15)).func_35439_a("potion.blindness");
	public static final Potion field_35469_r = (new Potion(16)).func_35439_a("potion.nightVision");
	public static final Potion field_35468_s = (new Potion(17)).func_35439_a("potion.hunger");
	public static final Potion field_35467_t = (new Potion(18)).func_35439_a("potion.weakness");
	public static final Potion field_35466_u = (new Potion(19)).func_35439_a("potion.poison");
	public static final Potion field_35465_v = null;
	public static final Potion field_35464_w = null;
	public static final Potion field_35474_x = null;
	public static final Potion field_35473_y = null;
	public static final Potion field_35472_z = null;
	public static final Potion field_35444_A = null;
	public static final Potion field_35445_B = null;
	public static final Potion field_35446_C = null;
	public static final Potion field_35440_D = null;
	public static final Potion field_35441_E = null;
	public static final Potion field_35442_F = null;
	public static final Potion field_35443_G = null;
	public final int field_35447_H;
	private String field_35448_I = "";

	protected Potion(int var1) {
		this.field_35447_H = var1;
		field_35455_a[var1] = this;
	}

	public void func_35438_a(EntityLiving var1, int var2) {
		if(this.field_35447_H == field_35458_l.field_35447_H) {
			if(var1.health < 20) {
				var1.heal(1);
			}
		} else if(this.field_35447_H == field_35466_u.field_35447_H) {
			if(var1.health > 1) {
				var1.attackEntityFrom(DamageSource.field_35094_l, 1);
			}
		} else if(this.field_35447_H == field_35468_s.field_35447_H && var1 instanceof EntityPlayer) {
			((EntityPlayer)var1).func_35198_b(0.025F * (float)(var2 + 1));
		} else if(this.field_35447_H == field_35462_h.field_35447_H) {
			var1.heal(4 << var2);
		} else if(this.field_35447_H == field_35463_i.field_35447_H) {
			var1.attackEntityFrom(DamageSource.field_35094_l, 4 << var2);
		}

	}

	public boolean func_35437_a(int var1, int var2) {
		if(this.field_35447_H != field_35458_l.field_35447_H && this.field_35447_H != field_35466_u.field_35447_H) {
			return this.field_35447_H == field_35468_s.field_35447_H;
		} else {
			int var3 = 25 >> var2;
			return var3 > 0 ? var1 % var3 == 0 : true;
		}
	}

	public Potion func_35439_a(String var1) {
		this.field_35448_I = var1;
		return this;
	}
}
