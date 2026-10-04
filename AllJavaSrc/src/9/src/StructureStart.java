package net.minecraft.src;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

public abstract class StructureStart {
	protected LinkedList field_35717_a = new LinkedList();
	protected StructureBoundingBox field_35716_b;

	public StructureBoundingBox func_35712_a() {
		return this.field_35716_b;
	}

	public void func_35711_a(World var1, Random var2, StructureBoundingBox var3) {
		Iterator var4 = this.field_35717_a.iterator();

		while(var4.hasNext()) {
			StructureComponent var5 = (StructureComponent)var4.next();
			if(var5.func_35021_b().func_35740_a(var3) && !var5.func_35023_a(var1, var2, var3)) {
				var4.remove();
			}
		}

	}

	protected void func_35714_b() {
		this.field_35716_b = StructureBoundingBox.func_35741_a();
		Iterator var1 = this.field_35717_a.iterator();

		while(var1.hasNext()) {
			StructureComponent var2 = (StructureComponent)var1.next();
			this.field_35716_b.func_35738_b(var2.func_35021_b());
		}

	}

	protected void func_35713_a(World var1, Random var2, int var3) {
		var1.getClass();
		int var4 = 63 - var3;
		int var5 = this.field_35716_b.func_35743_c() + 1;
		if(var5 < var4) {
			var5 += var2.nextInt(var4 - var5);
		}

		int var6 = var5 - this.field_35716_b.field_35750_e;
		this.field_35716_b.func_35745_a(0, var6, 0);
		Iterator var7 = this.field_35717_a.iterator();

		while(var7.hasNext()) {
			StructureComponent var8 = (StructureComponent)var7.next();
			var8.func_35021_b().func_35745_a(0, var6, 0);
		}

	}

	public boolean func_35715_c() {
		return true;
	}
}
