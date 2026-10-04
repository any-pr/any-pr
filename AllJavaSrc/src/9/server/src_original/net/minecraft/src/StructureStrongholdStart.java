package net.minecraft.src;

import java.util.ArrayList;
import java.util.Random;

class StructureStrongholdStart extends StructureStart {
	public StructureStrongholdStart(World var1, Random var2, int var3, int var4) {
		StructureStrongholdPieces.func_35625_a();
		ComponentStrongholdStairs2 var5 = new ComponentStrongholdStairs2(0, var2, (var3 << 4) + 2, (var4 << 4) + 2);
		this.field_35547_a.add(var5);
		var5.func_35308_a(var5, this.field_35547_a, var2);
		ArrayList var6 = var5.field_35328_b;

		while(!var6.isEmpty()) {
			int var7 = var2.nextInt(var6.size());
			StructureComponent var8 = (StructureComponent)var6.remove(var7);
			var8.func_35308_a(var5, this.field_35547_a, var2);
		}

		this.func_35544_c();
		this.func_35545_a(var1, var2, 10);
	}
}
