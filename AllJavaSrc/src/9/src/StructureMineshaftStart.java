package net.minecraft.src;

import java.util.Random;

public class StructureMineshaftStart extends StructureStart {
	public StructureMineshaftStart(World var1, Random var2, int var3, int var4) {
		ComponentMineshaftRoom var5 = new ComponentMineshaftRoom(0, var2, (var3 << 4) + 2, (var4 << 4) + 2);
		this.field_35717_a.add(var5);
		var5.func_35004_a(var5, this.field_35717_a, var2);
		this.func_35714_b();
		this.func_35713_a(var1, var2, 10);
	}
}
