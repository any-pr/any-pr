package net.minecraft.src;

public class DamageSource {
	public static DamageSource field_35542_a = new DamageSource("inFire");
	public static DamageSource field_35540_b = (new DamageSource("onFire")).func_35528_f();
	public static DamageSource field_35541_c = new DamageSource("lava");
	public static DamageSource field_35538_d = (new DamageSource("inWall")).func_35528_f();
	public static DamageSource field_35539_e = (new DamageSource("drown")).func_35528_f();
	public static DamageSource field_35536_f = (new DamageSource("starve")).func_35528_f();
	public static DamageSource field_35537_g = new DamageSource("cactus");
	public static DamageSource field_35549_h = new DamageSource("fall");
	public static DamageSource field_35550_i = (new DamageSource("outOfWorld")).func_35528_f().func_35531_g();
	public static DamageSource field_35547_j = (new DamageSource("generic")).func_35528_f();
	public static DamageSource field_35548_k = new DamageSource("explosion");
	public static DamageSource field_35545_l = (new DamageSource("magic")).func_35528_f();
	private boolean field_35543_n = false;
	private boolean field_35544_o = false;
	private float field_35551_p = 0.3F;
	public String field_35546_m;

	public static DamageSource func_35525_a(EntityLiving var0) {
		return new EntityDamageSource("mob", var0);
	}

	public static DamageSource func_35527_a(EntityPlayer var0) {
		return new EntityDamageSource("player", var0);
	}

	public static DamageSource func_35535_a(EntityArrow var0, Entity var1) {
		return new EntityDamageSourceIndirect("arrow", var0, var1);
	}

	public static DamageSource func_35530_a(EntityFireball var0, Entity var1) {
		return new EntityDamageSourceIndirect("fireball", var0, var1);
	}

	public static DamageSource func_35524_a(Entity var0, Entity var1) {
		return new EntityDamageSourceIndirect("thrown", var0, var1);
	}

	public boolean func_35534_b() {
		return this.field_35543_n;
	}

	public float func_35533_c() {
		return this.field_35551_p;
	}

	public boolean func_35529_d() {
		return this.field_35544_o;
	}

	protected DamageSource(String var1) {
		this.field_35546_m = var1;
	}

	public Entity func_35526_e() {
		return this.func_35532_a();
	}

	public Entity func_35532_a() {
		return null;
	}

	private DamageSource func_35528_f() {
		this.field_35543_n = true;
		this.field_35551_p = 0.0F;
		return this;
	}

	private DamageSource func_35531_g() {
		this.field_35544_o = true;
		return this;
	}
}
