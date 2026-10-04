package net.minecraft.src;

public abstract class GenLayer {
	private long field_35502_b;
	protected GenLayer field_35504_a;
	private long field_35503_c;
	private long field_35501_d;

	public static GenLayer[] func_35497_a(long var0) {
		LayerIsland var2 = new LayerIsland(1L);
		GenLayerZoomFuzzy var9 = new GenLayerZoomFuzzy(2000L, var2);
		GenLayerIsland var10 = new GenLayerIsland(1L, var9);
		GenLayerZoom var11 = new GenLayerZoom(2001L, var10);
		var10 = new GenLayerIsland(2L, var11);
		var11 = new GenLayerZoom(2002L, var10);
		var10 = new GenLayerIsland(3L, var11);
		var11 = new GenLayerZoom(2003L, var10);
		var10 = new GenLayerIsland(3L, var11);
		var11 = new GenLayerZoom(2004L, var10);
		var10 = new GenLayerIsland(3L, var11);
		byte var3 = 4;
		GenLayer var4 = GenLayerZoom.func_35515_a(1000L, var10, 0);
		GenLayerRiverInit var12 = new GenLayerRiverInit(100L, var4);
		var4 = GenLayerZoom.func_35515_a(1000L, var12, var3 + 2);
		GenLayerRiver var13 = new GenLayerRiver(1L, var4);
		GenLayerSmooth var14 = new GenLayerSmooth(1000L, var13);
		GenLayer var5 = GenLayerZoom.func_35515_a(1000L, var10, 0);
		GenLayerVillageLandscape var15 = new GenLayerVillageLandscape(200L, var5);
		Object var16 = GenLayerZoom.func_35515_a(1000L, var15, 2);
		Object var6 = new GenLayerTemperature((GenLayer)var16);
		Object var7 = new GenLayerDownfall((GenLayer)var16);

		for(int var8 = 0; var8 < var3; ++var8) {
			var16 = new GenLayerZoom((long)(1000 + var8), (GenLayer)var16);
			if(var8 == 0) {
				var16 = new GenLayerIsland(3L, (GenLayer)var16);
			}

			GenLayerSmoothZoom var17 = new GenLayerSmoothZoom((long)(1000 + var8), (GenLayer)var6);
			var6 = new GenLayerTemperatureMix(var17, (GenLayer)var16, var8);
			GenLayerSmoothZoom var21 = new GenLayerSmoothZoom((long)(1000 + var8), (GenLayer)var7);
			var7 = new GenLayerDownfallMix(var21, (GenLayer)var16, var8);
		}

		GenLayerSmooth var18 = new GenLayerSmooth(1000L, (GenLayer)var16);
		GenLayerRiverMix var20 = new GenLayerRiverMix(100L, var18, var14);
		GenLayer var19 = GenLayerSmoothZoom.func_35517_a(1000L, (GenLayer)var6, 2);
		GenLayer var22 = GenLayerSmoothZoom.func_35517_a(1000L, (GenLayer)var7, 2);
		GenLayerZoomVoronoi var23 = new GenLayerZoomVoronoi(10L, var20);
		var20.func_35496_b(var0);
		var19.func_35496_b(var0);
		var22.func_35496_b(var0);
		var23.func_35496_b(var0);
		return new GenLayer[]{var20, var23, var19, var22};
	}

	public GenLayer(long var1) {
		this.field_35501_d = var1;
		this.field_35501_d *= this.field_35501_d * 6364136223846793005L + 1442695040888963407L;
		this.field_35501_d += var1;
		this.field_35501_d *= this.field_35501_d * 6364136223846793005L + 1442695040888963407L;
		this.field_35501_d += var1;
		this.field_35501_d *= this.field_35501_d * 6364136223846793005L + 1442695040888963407L;
		this.field_35501_d += var1;
	}

	public void func_35496_b(long var1) {
		this.field_35502_b = var1;
		if(this.field_35504_a != null) {
			this.field_35504_a.func_35496_b(var1);
		}

		this.field_35502_b *= this.field_35502_b * 6364136223846793005L + 1442695040888963407L;
		this.field_35502_b += this.field_35501_d;
		this.field_35502_b *= this.field_35502_b * 6364136223846793005L + 1442695040888963407L;
		this.field_35502_b += this.field_35501_d;
		this.field_35502_b *= this.field_35502_b * 6364136223846793005L + 1442695040888963407L;
		this.field_35502_b += this.field_35501_d;
	}

	public void func_35499_a(long var1, long var3) {
		this.field_35503_c = this.field_35502_b;
		this.field_35503_c *= this.field_35503_c * 6364136223846793005L + 1442695040888963407L;
		this.field_35503_c += var1;
		this.field_35503_c *= this.field_35503_c * 6364136223846793005L + 1442695040888963407L;
		this.field_35503_c += var3;
		this.field_35503_c *= this.field_35503_c * 6364136223846793005L + 1442695040888963407L;
		this.field_35503_c += var1;
		this.field_35503_c *= this.field_35503_c * 6364136223846793005L + 1442695040888963407L;
		this.field_35503_c += var3;
	}

	protected int func_35498_a(int var1) {
		int var2 = (int)((this.field_35503_c >> 24) % (long)var1);
		if(var2 < 0) {
			var2 += var1;
		}

		this.field_35503_c *= this.field_35503_c * 6364136223846793005L + 1442695040888963407L;
		this.field_35503_c += this.field_35502_b;
		return var2;
	}

	public abstract int[] func_35500_a(int var1, int var2, int var3, int var4);
}
