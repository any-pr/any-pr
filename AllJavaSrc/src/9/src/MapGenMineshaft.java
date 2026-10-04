package net.minecraft.src;

public class MapGenMineshaft extends MapGenStructure {
	protected boolean func_35628_a(int var1, int var2) {
		return this.rand.nextInt(80) == 0 && this.rand.nextInt(80) < Math.max(Math.abs(var1), Math.abs(var2));
	}

	protected StructureStart func_35630_b(int var1, int var2) {
		return new StructureMineshaftStart(this.field_35625_d, this.rand, var1, var2);
	}
}
