package net.minecraft.src;

public class BiomeGenDesert extends BiomeGenBase {
	public BiomeGenDesert(int var1) {
		super(var1);
		this.spawnableCreatureList.clear();
		this.topBlock = (byte)Block.sand.blockID;
		this.fillerBlock = (byte)Block.sand.blockID;
		this.field_35488_u.field_35911_r = -999;
		this.field_35488_u.field_35908_u = 2;
		this.field_35488_u.field_35906_w = 50;
		this.field_35488_u.field_35916_x = 10;
	}
}
