package net.minecraft.src;

public class EntityDamageSource extends DamageSource {
	private Entity field_35552_n;

	public EntityDamageSource(String var1, Entity var2) {
		super(var1);
		this.field_35552_n = var2;
	}

	public Entity func_35532_a() {
		return this.field_35552_n;
	}
}
