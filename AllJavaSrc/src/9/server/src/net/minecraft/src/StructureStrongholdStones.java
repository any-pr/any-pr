package net.minecraft.src;

import java.util.Random;

class StructureStrongholdStones extends StructurePieceBlockSelector {
	private StructureStrongholdStones() {
	}

	public void func_35565_a(Random var1, int var2, int var3, int var4, boolean var5) {
		if(!var5) {
			this.field_35569_a = 0;
			this.field_35568_b = 0;
		} else {
			this.field_35569_a = Block.field_35052_bn.blockID;
			float var6 = var1.nextFloat();
			if(var6 < 0.2F) {
				this.field_35568_b = 2;
			} else if(var6 < 0.5F) {
				this.field_35568_b = 1;
			} else if(var6 < 0.55F) {
				this.field_35569_a = Block.field_35056_bm.blockID;
				this.field_35568_b = 2;
			} else {
				this.field_35568_b = 0;
			}
		}

	}

	StructureStrongholdStones(StructureStrongholdPieceWeight2 var1) {
		this();
	}
}
