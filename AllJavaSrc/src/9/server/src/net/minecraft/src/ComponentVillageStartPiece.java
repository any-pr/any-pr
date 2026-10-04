package net.minecraft.src;

import java.util.ArrayList;
import java.util.Random;

public class ComponentVillageStartPiece extends ComponentVillageWell {
	public WorldChunkManager field_35392_a;
	public int field_35390_b;
	public StructureVillagePieceWeight field_35391_c;
	public ArrayList field_35388_d;
	public ArrayList field_35389_e = new ArrayList();
	public ArrayList field_35387_f = new ArrayList();

	public ComponentVillageStartPiece(WorldChunkManager var1, int var2, Random var3, int var4, int var5, ArrayList var6, int var7) {
		super(0, var3, var4, var5);
		this.field_35392_a = var1;
		this.field_35388_d = var6;
		this.field_35390_b = var7;
	}

	public WorldChunkManager func_35386_a() {
		return this.field_35392_a;
	}
}
