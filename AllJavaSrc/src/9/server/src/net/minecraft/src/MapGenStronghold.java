package net.minecraft.src;

import java.util.ArrayList;

public class MapGenStronghold extends MapGenStructure {
	private BiomeGenBase[] field_35537_a = new BiomeGenBase[]{BiomeGenBase.desert, BiomeGenBase.forest, BiomeGenBase.field_35518_e, BiomeGenBase.swampland};
	private boolean field_35535_f;
	private ChunkCoordIntPair[] field_35536_g = new ChunkCoordIntPair[3];

	protected boolean func_35531_a(int var1, int var2) {
		int var5;
		if(!this.field_35535_f) {
			this.rand.setSeed(this.field_35530_d.getRandomSeed());
			double var3 = this.rand.nextDouble() * Math.PI * 2.0D;

			for(var5 = 0; var5 < this.field_35536_g.length; ++var5) {
				double var6 = (1.25D + this.rand.nextDouble()) * 32.0D;
				int var8 = (int)Math.round(Math.cos(var3) * var6);
				int var9 = (int)Math.round(Math.sin(var3) * var6);
				ArrayList var10 = new ArrayList();
				BiomeGenBase[] var11 = this.field_35537_a;
				int var12 = var11.length;

				for(int var13 = 0; var13 < var12; ++var13) {
					BiomeGenBase var14 = var11[var13];
					var10.add(var14);
				}

				ChunkPosition var17 = this.field_35530_d.getWorldChunkManager().func_35139_a((var8 << 4) + 8, (var9 << 4) + 8, 112, var10, this.rand);
				if(var17 != null) {
					var8 = var17.x >> 4;
					var9 = var17.z >> 4;
				} else {
					System.out.println("Placed stronghold in INVALID biome at (" + var8 + ", " + var9 + ")");
				}

				this.field_35536_g[var5] = new ChunkCoordIntPair(var8, var9);
				var3 += Math.PI * 2.0D / (double)this.field_35536_g.length;
			}

			this.field_35535_f = true;
		}

		ChunkCoordIntPair[] var15 = this.field_35536_g;
		int var4 = var15.length;

		for(var5 = 0; var5 < var4; ++var5) {
			ChunkCoordIntPair var16 = var15[var5];
			if(var1 == var16.chunkXPos && var2 == var16.chunkZPos) {
				return true;
			}
		}

		return false;
	}

	protected StructureStart func_35533_b(int var1, int var2) {
		return new StructureStrongholdStart(this.field_35530_d, this.rand, var1, var2);
	}
}
