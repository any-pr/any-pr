package net.minecraft.src;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class StructureStrongholdPieces {
	private static final StructureStrongholdPieceWeight[] field_35628_b = new StructureStrongholdPieceWeight[]{new StructureStrongholdPieceWeight(ComponentStrongholdStraight.class, 40, 0), new StructureStrongholdPieceWeight(ComponentStrongholdPrison.class, 5, 5), new StructureStrongholdPieceWeight(ComponentStrongholdLeftTurn.class, 20, 0), new StructureStrongholdPieceWeight(ComponentStrongholdRightTurn.class, 20, 0), new StructureStrongholdPieceWeight(ComponentStrongholdRoomCrossing.class, 10, 6), new StructureStrongholdPieceWeight(ComponentStrongholdStairsStraight.class, 5, 10), new StructureStrongholdPieceWeight(ComponentStrongholdStairs.class, 5, 10), new StructureStrongholdPieceWeight(ComponentStrongholdCrossing.class, 5, 4), new StructureStrongholdPieceWeight2(ComponentStrongholdLibrary.class, 10, 1)};
	private static List field_35629_c;
	static int field_35630_a = 0;
	private static final StructureStrongholdStones field_35627_d = new StructureStrongholdStones((StructureStrongholdPieceWeight2)null);

	public static void func_35625_a() {
		field_35629_c = new ArrayList();
		StructureStrongholdPieceWeight[] var0 = field_35628_b;
		int var1 = var0.length;

		for(int var2 = 0; var2 < var1; ++var2) {
			StructureStrongholdPieceWeight var3 = var0[var2];
			var3.field_35579_c = 0;
			field_35629_c.add(var3);
		}

	}

	private static boolean func_35626_c() {
		boolean var0 = false;
		field_35630_a = 0;

		StructureStrongholdPieceWeight var2;
		for(Iterator var1 = field_35629_c.iterator(); var1.hasNext(); field_35630_a += var2.field_35578_b) {
			var2 = (StructureStrongholdPieceWeight)var1.next();
			if(var2.field_35577_d > 0 && var2.field_35579_c < var2.field_35577_d) {
				var0 = true;
			}
		}

		return var0;
	}

	private static ComponentStronghold func_35620_a(StructureStrongholdPieceWeight var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		Class var8 = var0.field_35580_a;
		Object var9 = null;
		if(var8 == ComponentStrongholdStraight.class) {
			var9 = ComponentStrongholdStraight.func_35338_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentStrongholdPrison.class) {
			var9 = ComponentStrongholdPrison.func_35332_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentStrongholdLeftTurn.class) {
			var9 = ComponentStrongholdLeftTurn.func_35330_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentStrongholdRightTurn.class) {
			var9 = ComponentStrongholdRightTurn.func_35330_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentStrongholdRoomCrossing.class) {
			var9 = ComponentStrongholdRoomCrossing.func_35346_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentStrongholdStairsStraight.class) {
			var9 = ComponentStrongholdStairsStraight.func_35344_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentStrongholdStairs.class) {
			var9 = ComponentStrongholdStairs.func_35325_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentStrongholdCrossing.class) {
			var9 = ComponentStrongholdCrossing.func_35350_a(var1, var2, var3, var4, var5, var6, var7);
		} else if(var8 == ComponentStrongholdLibrary.class) {
			var9 = ComponentStrongholdLibrary.func_35334_a(var1, var2, var3, var4, var5, var6, var7);
		}

		return (ComponentStronghold)var9;
	}

	private static ComponentStronghold func_35623_b(ComponentStrongholdStairs2 var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		if(!func_35626_c()) {
			return null;
		} else {
			int var8 = 0;

			while(var8 < 5) {
				++var8;
				int var9 = var2.nextInt(field_35630_a);
				Iterator var10 = field_35629_c.iterator();

				while(var10.hasNext()) {
					StructureStrongholdPieceWeight var11 = (StructureStrongholdPieceWeight)var10.next();
					var9 -= var11.field_35578_b;
					if(var9 < 0) {
						if(!var11.func_35575_a(var7) || var11 == var0.field_35329_a) {
							break;
						}

						ComponentStronghold var12 = func_35620_a(var11, var1, var2, var3, var4, var5, var6, var7);
						if(var12 != null) {
							++var11.field_35579_c;
							var0.field_35329_a = var11;
							if(!var11.func_35576_a()) {
								field_35629_c.remove(var11);
							}

							return var12;
						}
					}
				}
			}

			StructureBoundingBox var13 = ComponentStrongholdCorridor.func_35342_a(var1, var2, var3, var4, var5, var6);
			if(var13 != null && var13.field_35676_b > 1) {
				return new ComponentStrongholdCorridor(var7, var2, var13, var6);
			} else {
				return null;
			}
		}
	}

	private static StructureComponent func_35621_c(ComponentStrongholdStairs2 var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		if(var7 > 50) {
			return null;
		} else if(Math.abs(var3 - var0.func_35313_b().field_35678_a) <= 112 && Math.abs(var5 - var0.func_35313_b().field_35677_c) <= 112) {
			ComponentStronghold var8 = func_35623_b(var0, var1, var2, var3, var4, var5, var6, var7 + 1);
			if(var8 != null) {
				var1.add(var8);
				var0.field_35328_b.add(var8);
			}

			return var8;
		} else {
			return null;
		}
	}

	static StructureComponent func_35624_a(ComponentStrongholdStairs2 var0, List var1, Random var2, int var3, int var4, int var5, int var6, int var7) {
		return func_35621_c(var0, var1, var2, var3, var4, var5, var6, var7);
	}

	static StructureStrongholdStones func_35622_b() {
		return field_35627_d;
	}
}
