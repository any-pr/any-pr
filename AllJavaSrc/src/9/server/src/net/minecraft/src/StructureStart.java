package net.minecraft.src;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

public abstract class StructureStart {
	protected LinkedList field_35547_a = new LinkedList();
	protected StructureBoundingBox field_35546_b;

	public StructureBoundingBox func_35543_b() {
		return this.field_35546_b;
	}

	public void func_35541_a(World var1, Random var2, StructureBoundingBox var3) {
		Iterator var4 = this.field_35547_a.iterator();

		while(var4.hasNext()) {
			StructureComponent var5 = (StructureComponent)var4.next();
			if(var5.func_35313_b().func_35664_a(var3) && !var5.func_35310_a(var1, var2, var3)) {
				var4.remove();
			}
		}

	}

	protected void func_35544_c() {
		this.field_35546_b = StructureBoundingBox.func_35672_a();
		Iterator var1 = this.field_35547_a.iterator();

		while(var1.hasNext()) {
			StructureComponent var2 = (StructureComponent)var1.next();
			this.field_35546_b.func_35666_b(var2.func_35313_b());
		}

	}

	protected void func_35545_a(World var1, Random var2, int var3) {
		var1.getClass();
		int var4 = 63 - var3;
		int var5 = this.field_35546_b.func_35668_c() + 1;
		if(var5 < var4) {
			var5 += var2.nextInt(var4 - var5);
		}

		int var6 = var5 - this.field_35546_b.field_35675_e;
		this.field_35546_b.func_35670_a(0, var6, 0);
		Iterator var7 = this.field_35547_a.iterator();

		while(var7.hasNext()) {
			StructureComponent var8 = (StructureComponent)var7.next();
			var8.func_35313_b().func_35670_a(0, var6, 0);
		}

	}

	public boolean func_35542_a() {
		return true;
	}
}
