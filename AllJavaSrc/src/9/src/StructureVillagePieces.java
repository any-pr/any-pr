package net.minecraft.src;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class StructureVillagePieces {
	public static ArrayList func_35705_a(Random var0, int var1) {
		ArrayList var2 = new ArrayList();
		var2.add(new StructureVillagePieceWeight(ComponentVillageHouse4_Garden.class, 4, MathHelper.func_35598_a(var0, 2 + var1, 4 + var1 * 2)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageChurch.class, 20, MathHelper.func_35598_a(var0, 0 + var1, 1 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageHouse1.class, 20, MathHelper.func_35598_a(var0, 0 + var1, 2 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageWoodHut.class, 3, MathHelper.func_35598_a(var0, 2 + var1, 5 + var1 * 3)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageHall.class, 15, MathHelper.func_35598_a(var0, 0 + var1, 2 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageField.class, 3, MathHelper.func_35598_a(var0, 1 + var1, 4 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageField2.class, 3, MathHelper.func_35598_a(var0, 2 + var1, 4 + var1 * 2)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageHouse2.class, 15, MathHelper.func_35598_a(var0, 0, 1 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageHouse3.class, 8, MathHelper.func_35598_a(var0, 0 + var1, 3 + var1 * 2)));
		Iterator var3 = var2.iterator();

		while(var3.hasNext()) {
			if(((StructureVillagePieceWeight)var3.next()).field_35604_d == 0) {
				var3.remove();
			}
		}

		return var2;
	}

	private static int func_35703_a(ArrayList var0) {
		boolean var1 = false;
		int var2 = 0;

		StructureVillagePieceWeight var4;
		for(Iterator var3 = var0.iterator(); var3.hasNext(); var2 += var4.field_35605_b) {
			var4 = (StructureVillagePieceWeight)var3.next();
			if(var4.field_35604_d > 0 && var4.field_35606_c < var4.field_35604_d) {
				var1 = true;
			}
		}

		return var1 ? var2 : -1;
	}

	private static ComponentVillage func_35699_a(StructureVillagePieceWeight var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		Class var8 = var0.field_35607_a;
		Object var9 = null;
		if(var8 == ComponentVillageHouse4_Garden.class) {
			var9 = ComponentVillageHouse4_Garden.func_35082_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageChurch.class) {
			var9 = ComponentVillageChurch.func_35097_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageHouse1.class) {
			var9 = ComponentVillageHouse1.func_35095_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageWoodHut.class) {
			var9 = ComponentVillageWoodHut.func_35091_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageHall.class) {
			var9 = ComponentVillageHall.func_35078_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageField.class) {
			var9 = ComponentVillageField.func_35080_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageField2.class) {
			var9 = ComponentVillageField2.func_35089_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageHouse2.class) {
			var9 = ComponentVillageHouse2.func_35085_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageHouse3.class) {
			var9 = ComponentVillageHouse3.func_35101_a(var1, var2, var3, var4, var5, var6, var7);
		}

		return (ComponentVillage)var9;
	}

	private static ComponentVillage func_35700_c(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		int var8 = func_35703_a(var0.field_35107_d);
		if(var8 <= 0) {
			return null;
		} else {
			int var9 = 0;

			while(var9 < 5) {
				++var9;
				int var10 = var2.nextInt(var8);
				Iterator var11 = var0.field_35107_d.iterator();

				while(var11.hasNext()) {
					StructureVillagePieceWeight var12 = (StructureVillagePieceWeight)var11.next();
					var10 -= var12.field_35605_b;
					if(var10 < 0) {
						if(!var12.func_35602_a(var7) || var12 == var0.field_35110_c && var0.field_35107_d.size() > 1) {
							break;
						}

						ComponentVillage var13 = func_35699_a(var12, var1, var2, var3, var4, var5, var6, var7);
						if(var13 != null) {
							++var12.field_35606_c;
							var0.field_35110_c = var12;
							if(!var12.func_35603_a()) {
								var0.field_35107_d.remove(var12);
							}

							return var13;
						}
					}
				}
			}

			StructureBoundingBox var14 = ComponentVillageTorch.func_35099_a(var1, var2, var3, var4, var5, var6);
			if(var14 != null) {
				return new ComponentVillageTorch(var7, var2, var14, var6);
			} else {
				return null;
			}
		}
	}

	private static StructureComponent func_35702_d(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		if(var7 > 50) {
			return null;
		} else if(Math.abs(var3 - var0.func_35021_b().field_35753_a) <= 112 && Math.abs(var5 - var0.func_35021_b().field_35752_c) <= 112) {
			ComponentVillage var8 = func_35700_c(var0, var1, var2, var3, var4, var5, var6, var7 + 1);
			if(var8 != null) {
				int var9 = (var8.field_35024_g.field_35753_a + var8.field_35024_g.field_35749_d) / 2;
				int var10 = (var8.field_35024_g.field_35752_c + var8.field_35024_g.field_35748_f) / 2;
				int var11 = var8.field_35024_g.field_35749_d - var8.field_35024_g.field_35753_a;
				int var12 = var8.field_35024_g.field_35748_f - var8.field_35024_g.field_35752_c;
				int var13 = var11 > var12 ? var11 : var12;
				if(var0.func_35105_a().func_35562_a(var9, var10, var13 / 2 + 4, MapGenVillage.field_35635_a)) {
					var1.add(var8);
					var0.field_35108_e.add(var8);
					return var8;
				}
			}

			return null;
		} else {
			return null;
		}
	}

	private static StructureComponent func_35698_e(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		if(var7 > 3 + var0.field_35109_b) {
			return null;
		} else if(Math.abs(var3 - var0.func_35021_b().field_35753_a) <= 112 && Math.abs(var5 - var0.func_35021_b().field_35752_c) <= 112) {
			StructureBoundingBox var8 = ComponentVillagePathGen.func_35087_a(var0, var1, var2, var3, var4, var5, var6);
			if(var8 != null && var8.field_35751_b > 10) {
				ComponentVillagePathGen var9 = new ComponentVillagePathGen(var7, var2, var8, var6);
				int var10 = (var9.field_35024_g.field_35753_a + var9.field_35024_g.field_35749_d) / 2;
				int var11 = (var9.field_35024_g.field_35752_c + var9.field_35024_g.field_35748_f) / 2;
				int var12 = var9.field_35024_g.field_35749_d - var9.field_35024_g.field_35753_a;
				int var13 = var9.field_35024_g.field_35748_f - var9.field_35024_g.field_35752_c;
				int var14 = var12 > var13 ? var12 : var13;
				if(var0.func_35105_a().func_35562_a(var10, var11, var14 / 2 + 4, MapGenVillage.field_35635_a)) {
					var1.add(var9);
					var0.field_35106_f.add(var9);
					return var9;
				}
			}

			return null;
		} else {
			return null;
		}
	}

	static StructureComponent func_35704_a(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		return func_35702_d(var0, var1, var2, var3, var4, var5, var6, var7);
	}

	static StructureComponent func_35701_b(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		return func_35698_e(var0, var1, var2, var3, var4, var5, var6, var7);
	}
}
