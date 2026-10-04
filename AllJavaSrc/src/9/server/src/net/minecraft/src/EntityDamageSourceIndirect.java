package net.minecraft.src;

public class EntityDamageSourceIndirect extends EntityDamageSource {
	private Entity field_35102_n;

	public EntityDamageSourceIndirect(String var1, Entity var2, Entity var3) {
		super(var1, var2);
		this.field_35102_n = var3;
	}

	public Entity func_35080_a() {
		return this.field_35102_n;
	}

	public String func_35075_a(EntityPlayer var1) {
		return StatCollector.translateToLocalFormatted("death." + this.field_35095_m, new Object[]{var1.username, this.field_35102_n.func_35150_Y()});
	}
}
