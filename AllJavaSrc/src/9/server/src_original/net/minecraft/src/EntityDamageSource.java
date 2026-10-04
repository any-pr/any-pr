package net.minecraft.src;

public class EntityDamageSource extends DamageSource {
	private Entity field_35101_n;

	public EntityDamageSource(String var1, Entity var2) {
		super(var1);
		this.field_35101_n = var2;
	}

	public Entity func_35080_a() {
		return this.field_35101_n;
	}

	public String func_35075_a(EntityPlayer var1) {
		return StatCollector.translateToLocalFormatted("death." + this.field_35095_m, new Object[]{var1.username, this.field_35101_n.func_35150_Y()});
	}
}
