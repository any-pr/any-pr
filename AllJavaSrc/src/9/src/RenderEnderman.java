package net.minecraft.src;

import java.util.Random;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

public class RenderEnderman extends RenderLiving {
	private ModelEnderman field_35444_c = (ModelEnderman)this.mainModel;
	private Random field_35445_h = new Random();

	public RenderEnderman() {
		super(new ModelEnderman(), 0.5F);
		this.setRenderPassModel(this.field_35444_c);
	}

	public void func_35442_a(EntityEnderman var1, double var2, double var4, double var6, float var8, float var9) {
		this.field_35444_c.field_35407_a = var1.func_35176_r() > 0;
		this.field_35444_c.field_35406_b = var1.field_35187_a;
		if(var1.field_35187_a) {
			double var10 = 0.02D;
			var2 += this.field_35445_h.nextGaussian() * var10;
			var6 += this.field_35445_h.nextGaussian() * var10;
		}

		super.doRenderLiving(var1, var2, var4, var6, var8, var9);
	}

	protected void func_35443_a(EntityEnderman var1, float var2) {
		super.renderEquippedItems(var1, var2);
		if(var1.func_35176_r() > 0) {
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glPushMatrix();
			float var3 = 0.5F;
			GL11.glTranslatef(0.0F, 11.0F / 16.0F, -(12.0F / 16.0F));
			var3 *= 1.0F;
			GL11.glRotatef(20.0F, 1.0F, 0.0F, 0.0F);
			GL11.glRotatef(45.0F, 0.0F, 1.0F, 0.0F);
			GL11.glScalef(var3, -var3, var3);
			int var4 = var1.func_35115_a(var2);
			int var5 = var4 % 65536;
			int var6 = var4 / 65536;
			GL13.glMultiTexCoord2f(ARBMultitexture.GL_TEXTURE1_ARB, (float)var5 / 1.0F, (float)var6 / 1.0F);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			this.loadTexture("/terrain.png");
			this.renderBlocks.renderBlockOnInventory(Block.blocksList[var1.func_35176_r()], var1.func_35180_s(), 1.0F);
			GL11.glPopMatrix();
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		}

	}

	protected boolean func_35441_a(EntityEnderman var1, int var2, float var3) {
		if(var2 != 0) {
			return false;
		} else {
			this.loadTexture("/mob/enderman_eyes.png");
			float var4 = 1.0F;
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
			GL11.glDisable(GL11.GL_LIGHTING);
			char var5 = '\uf0f0';
			int var6 = var5 % 65536;
			int var7 = var5 / 65536;
			GL13.glMultiTexCoord2f(ARBMultitexture.GL_TEXTURE1_ARB, (float)var6 / 1.0F, (float)var7 / 1.0F);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, var4);
			return true;
		}
	}

	protected boolean shouldRenderPass(EntityLiving var1, int var2, float var3) {
		return this.func_35441_a((EntityEnderman)var1, var2, var3);
	}

	protected void renderEquippedItems(EntityLiving var1, float var2) {
		this.func_35443_a((EntityEnderman)var1, var2);
	}

	public void doRenderLiving(EntityLiving var1, double var2, double var4, double var6, float var8, float var9) {
		this.func_35442_a((EntityEnderman)var1, var2, var4, var6, var8, var9);
	}

	public void doRender(Entity var1, double var2, double var4, double var6, float var8, float var9) {
		this.func_35442_a((EntityEnderman)var1, var2, var4, var6, var8, var9);
	}
}
