package net.minecraft.src;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

public abstract class MapGenStructure extends MapGenBase {
	protected HashMap field_35631_e = new HashMap();

	public void generate(IChunkProvider var1, World var2, int var3, int var4, byte[] var5) {
		super.generate(var1, var2, var3, var4, var5);
	}

	protected void recursiveGenerate(World var1, int var2, int var3, int var4, int var5, byte[] var6) {
		if(!this.field_35631_e.containsKey(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(var2, var3)))) {
			this.rand.nextInt();
			if(this.func_35628_a(var2, var3)) {
				StructureStart var7 = this.func_35630_b(var2, var3);
				this.field_35631_e.put(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(var2, var3)), var7);
			}

		}
	}

	public boolean func_35629_a(World var1, Random var2, int var3, int var4) {
		int var5 = (var3 << 4) + 8;
		int var6 = (var4 << 4) + 8;
		boolean var7 = false;
		Iterator var8 = this.field_35631_e.values().iterator();

		while(var8.hasNext()) {
			StructureStart var9 = (StructureStart)var8.next();
			if(var9.func_35715_c() && var9.func_35712_a().func_35746_a(var5, var6, var5 + 15, var6 + 15)) {
				var9.func_35711_a(var1, var2, new StructureBoundingBox(var5, var6, var5 + 15, var6 + 15));
				var7 = true;
			}
		}

		return var7;
	}

	protected abstract boolean func_35628_a(int var1, int var2);

	protected abstract StructureStart func_35630_b(int var1, int var2);
}
