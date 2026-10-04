package net.minecraft.src;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class TileEntityChestRenderer extends TileEntitySpecialRenderer {
	private ModelChest field_35377_b = new ModelChest();
	private ModelChest field_35378_c = new ModelLargeChest();

	public void func_35376_a(TileEntityChest var1, double var2, double var4, double var6, float var8) {
		int var9;
		if(var1.worldObj == null) {
			var9 = 0;
		} else {
			Block var10 = var1.getBlockType();
			var9 = var1.getBlockMetadata();
			if(var10 != null && var9 == 0) {
				((BlockChest)var10).func_35306_h(var1.worldObj, var1.xCoord, var1.yCoord, var1.zCoord);
				var9 = var1.getBlockMetadata();
			}

			var1.func_35147_g();
		}

		if(var1.field_35152_b == null && var1.field_35150_d == null) {
			ModelChest var14;
			if(var1.field_35153_c == null && var1.field_35151_e == null) {
				var14 = this.field_35377_b;
				this.bindTextureByName("/item/chest.png");
			} else {
				var14 = this.field_35378_c;
				this.bindTextureByName("/item/largechest.png");
			}

			GL11.glPushMatrix();
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			GL11.glTranslatef((float)var2, (float)var4 + 1.0F, (float)var6 + 1.0F);
			GL11.glScalef(1.0F, -1.0F, -1.0F);
			GL11.glTranslatef(0.5F, 0.5F, 0.5F);
			short var11 = 0;
			if(var9 == 2) {
				var11 = 180;
			}

			if(var9 == 3) {
				var11 = 0;
			}

			if(var9 == 4) {
				var11 = 90;
			}

			if(var9 == 5) {
				var11 = -90;
			}

			if(var9 == 2 && var1.field_35153_c != null) {
				GL11.glTranslatef(1.0F, 0.0F, 0.0F);
			}

			if(var9 == 5 && var1.field_35151_e != null) {
				GL11.glTranslatef(0.0F, 0.0F, -1.0F);
			}

			GL11.glRotatef((float)var11, 0.0F, 1.0F, 0.0F);
			GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
			float var12 = var1.field_35149_g + (var1.field_35148_f - var1.field_35149_g) * var8;
			float var13;
			if(var1.field_35152_b != null) {
				var13 = var1.field_35152_b.field_35149_g + (var1.field_35152_b.field_35148_f - var1.field_35152_b.field_35149_g) * var8;
				if(var13 > var12) {
					var12 = var13;
				}
			}

			if(var1.field_35150_d != null) {
				var13 = var1.field_35150_d.field_35149_g + (var1.field_35150_d.field_35148_f - var1.field_35150_d.field_35149_g) * var8;
				if(var13 > var12) {
					var12 = var13;
				}
			}

			var12 = 1.0F - var12;
			var12 = 1.0F - var12 * var12 * var12;
			var14.field_35405_a.rotateAngleX = -(var12 * (float)Math.PI / 2.0F);
			var14.func_35402_a();
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
			GL11.glPopMatrix();
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		}
	}

	public void renderTileEntityAt(TileEntity var1, double var2, double var4, double var6, float var8) {
		this.func_35376_a((TileEntityChest)var1, var2, var4, var6, var8);
	}
}
