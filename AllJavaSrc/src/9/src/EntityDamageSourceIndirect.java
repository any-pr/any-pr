package net.minecraft.src;

public class EntityDamageSourceIndirect extends EntityDamageSource {
	private Entity field_35553_n;

	public EntityDamageSourceIndirect(String var1, Entity var2, Entity var3) {
		super(var1, var2);
		this.field_35553_n = var3;
	}

	public Entity func_35532_a() {
		return this.field_35553_n;
	}
}
