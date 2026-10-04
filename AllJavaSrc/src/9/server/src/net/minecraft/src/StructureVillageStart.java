package net.minecraft.src;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

class StructureVillageStart extends StructureStart {
	private boolean field_35548_c = false;

	public StructureVillageStart(World var1, Random var2, int var3, int var4) {
		byte var5 = 0;
		ArrayList var6 = StructureVillagePieces.func_35646_a(var2, var5);
		ComponentVillageStartPiece var7 = new ComponentVillageStartPiece(var1.getWorldChunkManager(), 0, var2, (var3 << 4) + 2, (var4 << 4) + 2, var6, var5);
		this.field_35547_a.add(var7);
		var7.func_35308_a(var7, this.field_35547_a, var2);
		ArrayList var8 = var7.field_35387_f;
		ArrayList var9 = var7.field_35389_e;

		int var10;
		while(!var8.isEmpty() || !var9.isEmpty()) {
			StructureComponent var11;
			if(!var8.isEmpty()) {
				var10 = var2.nextInt(var8.size());
				var11 = (StructureComponent)var8.remove(var10);
				var11.func_35308_a(var7, this.field_35547_a, var2);
			} else {
				var10 = var2.nextInt(var9.size());
				var11 = (StructureComponent)var9.remove(var10);
				var11.func_35308_a(var7, this.field_35547_a, var2);
			}
		}

		this.func_35544_c();
		var10 = 0;
		Iterator var13 = this.field_35547_a.iterator();

		while(var13.hasNext()) {
			StructureComponent var12 = (StructureComponent)var13.next();
			if(!(var12 instanceof ComponentVillageRoadPiece)) {
				++var10;
			}
		}

		this.field_35548_c = var10 > 2;
	}

	public boolean func_35542_a() {
		return this.field_35548_c;
	}
}
