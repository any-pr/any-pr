package net.minecraft.src;

public class BiomeGenDesert extends BiomeGenBase {
	public BiomeGenDesert(int var1) {
		super(var1);
		this.spawnableCreatureList.clear();
		this.topBlock = (byte)Block.sand.blockID;
		this.fillerBlock = (byte)Block.sand.blockID;
		this.field_35523_u.field_35284_r = -999;
		this.field_35523_u.field_35281_u = 2;
		this.field_35523_u.field_35279_w = 50;
		this.field_35523_u.field_35289_x = 10;
	}
}
