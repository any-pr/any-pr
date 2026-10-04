package net.minecraft.src;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class StructureVillagePieces {
	public static ArrayList func_35646_a(Random var0, int var1) {
		ArrayList var2 = new ArrayList();
		var2.add(new StructureVillagePieceWeight(ComponentVillageHouse4_Garden.class, 4, MathHelper.func_35476_a(var0, 2 + var1, 4 + var1 * 2)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageChurch.class, 20, MathHelper.func_35476_a(var0, 0 + var1, 1 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageHouse1.class, 20, MathHelper.func_35476_a(var0, 0 + var1, 2 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageWoodHut.class, 3, MathHelper.func_35476_a(var0, 2 + var1, 5 + var1 * 3)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageHall.class, 15, MathHelper.func_35476_a(var0, 0 + var1, 2 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageField.class, 3, MathHelper.func_35476_a(var0, 1 + var1, 4 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageField2.class, 3, MathHelper.func_35476_a(var0, 2 + var1, 4 + var1 * 2)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageHouse2.class, 15, MathHelper.func_35476_a(var0, 0, 1 + var1)));
		var2.add(new StructureVillagePieceWeight(ComponentVillageHouse3.class, 8, MathHelper.func_35476_a(var0, 0 + var1, 3 + var1 * 2)));
		Iterator var3 = var2.iterator();

		while(var3.hasNext()) {
			if(((StructureVillagePieceWeight)var3.next()).field_35493_d == 0) {
				var3.remove();
			}
		}

		return var2;
	}

	private static int func_35645_a(ArrayList var0) {
		boolean var1 = false;
		int var2 = 0;

		StructureVillagePieceWeight var4;
		for(Iterator var3 = var0.iterator(); var3.hasNext(); var2 += var4.field_35494_b) {
			var4 = (StructureVillagePieceWeight)var3.next();
			if(var4.field_35493_d > 0 && var4.field_35495_c < var4.field_35493_d) {
				var1 = true;
			}
		}

		return var1 ? var2 : -1;
	}

	private static ComponentVillage func_35639_a(StructureVillagePieceWeight var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		Class var8 = var0.field_35496_a;
		Object var9 = null;
		if(var8 == ComponentVillageHouse4_Garden.class) {
			var9 = ComponentVillageHouse4_Garden.func_35401_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageChurch.class) {
			var9 = ComponentVillageChurch.func_35380_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageHouse1.class) {
			var9 = ComponentVillageHouse1.func_35397_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageWoodHut.class) {
			var9 = ComponentVillageWoodHut.func_35393_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageHall.class) {
			var9 = ComponentVillageHall.func_35374_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageField.class) {
			var9 = ComponentVillageField.func_35370_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageField2.class) {
			var9 = ComponentVillageField2.func_35399_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageHouse2.class) {
			var9 = ComponentVillageHouse2.func_35376_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentVillageHouse3.class) {
			var9 = ComponentVillageHouse3.func_35372_a(var1, var2, var3, var4, var5, var6, var7);
		}

		return (ComponentVillage)var9;
	}

	private static ComponentVillage func_35643_c(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		int var8 = func_35645_a(var0.field_35388_d);
		if(var8 <= 0) {
			return null;
		} else {
			int var9 = 0;

			while(var9 < 5) {
				++var9;
				int var10 = var2.nextInt(var8);
				Iterator var11 = var0.field_35388_d.iterator();

				while(var11.hasNext()) {
					StructureVillagePieceWeight var12 = (StructureVillagePieceWeight)var11.next();
					var10 -= var12.field_35494_b;
					if(var10 < 0) {
						if(!var12.func_35491_a(var7) || var12 == var0.field_35391_c && var0.field_35388_d.size() > 1) {
							break;
						}

						ComponentVillage var13 = func_35639_a(var12, var1, var2, var3, var4, var5, var6, var7);
						if(var13 != null) {
							++var12.field_35495_c;
							var0.field_35391_c = var12;
							if(!var12.func_35492_a()) {
								var0.field_35388_d.remove(var12);
							}

							return var13;
						}
					}
				}
			}

			StructureBoundingBox var14 = ComponentVillageTorch.func_35382_a(var1, var2, var3, var4, var5, var6);
			if(var14 != null) {
				return new ComponentVillageTorch(var7, var2, var14, var6);
			} else {
				return null;
			}
		}
	}

	private static StructureComponent func_35641_d(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		if(var7 > 50) {
			return null;
		} else if(Math.abs(var3 - var0.func_35313_b().field_35678_a) <= 112 && Math.abs(var5 - var0.func_35313_b().field_35677_c) <= 112) {
			ComponentVillage var8 = func_35643_c(var0, var1, var2, var3, var4, var5, var6, var7 + 1);
			if(var8 != null) {
				int var9 = (var8.field_35316_g.field_35678_a + var8.field_35316_g.field_35674_d) / 2;
				int var10 = (var8.field_35316_g.field_35677_c + var8.field_35316_g.field_35673_f) / 2;
				int var11 = var8.field_35316_g.field_35674_d - var8.field_35316_g.field_35678_a;
				int var12 = var8.field_35316_g.field_35673_f - var8.field_35316_g.field_35677_c;
				int var13 = var11 > var12 ? var11 : var12;
				if(var0.func_35386_a().func_35141_a(var9, var10, var13 / 2 + 4, MapGenVillage.field_35538_a)) {
					var1.add(var8);
					var0.field_35389_e.add(var8);
					return var8;
				}
			}

			return null;
		} else {
			return null;
		}
	}

	private static StructureComponent func_35644_e(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		if(var7 > 3 + var0.field_35390_b) {
			return null;
		} else if(Math.abs(var3 - var0.func_35313_b().field_35678_a) <= 112 && Math.abs(var5 - var0.func_35313_b().field_35677_c) <= 112) {
			StructureBoundingBox var8 = ComponentVillagePathGen.func_35378_a(var0, var1, var2, var3, var4, var5, var6);
			if(var8 != null && var8.field_35676_b > 10) {
				ComponentVillagePathGen var9 = new ComponentVillagePathGen(var7, var2, var8, var6);
				int var10 = (var9.field_35316_g.field_35678_a + var9.field_35316_g.field_35674_d) / 2;
				int var11 = (var9.field_35316_g.field_35677_c + var9.field_35316_g.field_35673_f) / 2;
				int var12 = var9.field_35316_g.field_35674_d - var9.field_35316_g.field_35678_a;
				int var13 = var9.field_35316_g.field_35673_f - var9.field_35316_g.field_35677_c;
				int var14 = var12 > var13 ? var12 : var13;
				if(var0.func_35386_a().func_35141_a(var10, var11, var14 / 2 + 4, MapGenVillage.field_35538_a)) {
					var1.add(var9);
					var0.field_35387_f.add(var9);
					return var9;
				}
			}

			return null;
		} else {
			return null;
		}
	}

	static StructureComponent func_35640_a(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		return func_35641_d(var0, var1, var2, var3, var4, var5, var6, var7);
	}

	static StructureComponent func_35642_b(ComponentVillageStartPiece var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		return func_35644_e(var0, var1, var2, var3, var4, var5, var6, var7);
	}
}
