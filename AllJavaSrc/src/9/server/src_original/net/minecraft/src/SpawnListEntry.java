package net.minecraft.src;

public class SpawnListEntry extends WeightedRandomChoice {
	public Class entityClass;
	public int field_35484_b;
	public int field_35485_c;

	public SpawnListEntry(Class var1, int var2, int var3, int var4) {
		super(var2);
		this.entityClass = var1;
		this.field_35484_b = var3;
		this.field_35485_c = var4;
	}
}
