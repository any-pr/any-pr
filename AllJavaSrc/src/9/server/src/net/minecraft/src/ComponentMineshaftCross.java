package net.minecraft.src;

import java.util.List;
import java.util.Random;

public class ComponentMineshaftCross extends StructureComponent {
	private final int field_35364_a;
	private final boolean field_35363_b;

	public ComponentMineshaftCross(int var1, Random var2, StructureBoundingBox var3, int var4) {
		super(var1);
		this.field_35364_a = var4;
		this.field_35316_g = var3;
		this.field_35363_b = var3.func_35668_c() > 3;
	}

	public static StructureBoundingBox func_35362_a(List var0, Random var1, int var2, int var3, int var4, int var5) {
		StructureBoundingBox var6 = new StructureBoundingBox(var2, var3, var4, var2, var3 + 2, var4);
		if(var1.nextInt(4) == 0) {
			var6.field_35675_e += 4;
		}

		switch(var5) {
		case 0:
			var6.field_35678_a = var2 - 1;
			var6.field_35674_d = var2 + 3;
			var6.field_35673_f = var4 + 4;
			break;
		case 1:
			var6.field_35678_a = var2 - 4;
			var6.field_35677_c = var4 - 1;
			var6.field_35673_f = var4 + 3;
			break;
		case 2:
			var6.field_35678_a = var2 - 1;
			var6.field_35674_d = var2 + 3;
			var6.field_35677_c = var4 - 4;
			break;
		case 3:
			var6.field_35674_d = var2 + 4;
			var6.field_35677_c = var4 - 1;
			var6.field_35673_f = var4 + 3;
		}

		return StructureComponent.func_35312_a(var0, var6) != null ? null : var6;
	}

	public void func_35308_a(StructureComponent var1, List var2, Random var3) {
		int var4 = this.func_35305_c();
		switch(this.field_35364_a) {
		case 0:
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f + 1, 0, var4);
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, 1, var4);
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, 3, var4);
			break;
		case 1:
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c - 1, 2, var4);
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f + 1, 0, var4);
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, 1, var4);
			break;
		case 2:
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c - 1, 2, var4);
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, 1, var4);
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, 3, var4);
			break;
		case 3:
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c - 1, 2, var4);
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f + 1, 0, var4);
			StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, 3, var4);
		}

		if(this.field_35363_b) {
			if(var3.nextBoolean()) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b + 3 + 1, this.field_35316_g.field_35677_c - 1, 2, var4);
			}

			if(var3.nextBoolean()) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a - 1, this.field_35316_g.field_35676_b + 3 + 1, this.field_35316_g.field_35677_c + 1, 1, var4);
			}

			if(var3.nextBoolean()) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35674_d + 1, this.field_35316_g.field_35676_b + 3 + 1, this.field_35316_g.field_35677_c + 1, 3, var4);
			}

			if(var3.nextBoolean()) {
				StructureMineshaftPieces.func_35433_a(var1, var2, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b + 3 + 1, this.field_35316_g.field_35673_f + 1, 0, var4);
			}
		}

	}

	public boolean func_35310_a(World var1, Random var2, StructureBoundingBox var3) {
		if(this.func_35295_a(var1, var3)) {
			return false;
		} else {
			if(this.field_35363_b) {
				this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c, this.field_35316_g.field_35674_d - 1, this.field_35316_g.field_35676_b + 3 - 1, this.field_35316_g.field_35673_f, 0, 0, false);
				this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, this.field_35316_g.field_35674_d, this.field_35316_g.field_35676_b + 3 - 1, this.field_35316_g.field_35673_f - 1, 0, 0, false);
				this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35675_e - 2, this.field_35316_g.field_35677_c, this.field_35316_g.field_35674_d - 1, this.field_35316_g.field_35675_e, this.field_35316_g.field_35673_f, 0, 0, false);
				this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35675_e - 2, this.field_35316_g.field_35677_c + 1, this.field_35316_g.field_35674_d, this.field_35316_g.field_35675_e, this.field_35316_g.field_35673_f - 1, 0, 0, false);
				this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b + 3, this.field_35316_g.field_35677_c + 1, this.field_35316_g.field_35674_d - 1, this.field_35316_g.field_35676_b + 3, this.field_35316_g.field_35673_f - 1, 0, 0, false);
			} else {
				this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c, this.field_35316_g.field_35674_d - 1, this.field_35316_g.field_35675_e, this.field_35316_g.field_35673_f, 0, 0, false);
				this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, this.field_35316_g.field_35674_d, this.field_35316_g.field_35675_e, this.field_35316_g.field_35673_f - 1, 0, 0, false);
			}

			this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35675_e, this.field_35316_g.field_35677_c + 1, Block.planks.blockID, 0, false);
			this.func_35294_a(var1, var3, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f - 1, this.field_35316_g.field_35678_a + 1, this.field_35316_g.field_35675_e, this.field_35316_g.field_35673_f - 1, Block.planks.blockID, 0, false);
			this.func_35294_a(var1, var3, this.field_35316_g.field_35674_d - 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35677_c + 1, this.field_35316_g.field_35674_d - 1, this.field_35316_g.field_35675_e, this.field_35316_g.field_35677_c + 1, Block.planks.blockID, 0, false);
			this.func_35294_a(var1, var3, this.field_35316_g.field_35674_d - 1, this.field_35316_g.field_35676_b, this.field_35316_g.field_35673_f - 1, this.field_35316_g.field_35674_d - 1, this.field_35316_g.field_35675_e, this.field_35316_g.field_35673_f - 1, Block.planks.blockID, 0, false);
			return true;
		}
	}
}
