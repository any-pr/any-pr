package net.minecraft.src;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class GuiIngame extends Gui {
	private static RenderItem itemRenderer = new RenderItem();
	private List chatMessageList = new ArrayList();
	private Random rand = new Random();
	private Minecraft mc;
	public String field_933_a = null;
	private int updateCounter = 0;
	private String recordPlaying = "";
	private int recordPlayingUpFor = 0;
	private boolean recordIsPlaying = false;
	public float damageGuiPartialTime;
	float prevVignetteBrightness = 1.0F;

	public GuiIngame(Minecraft var1) {
		this.mc = var1;
	}

	public void renderGameOverlay(float var1, boolean var2, int var3, int var4) {
		ScaledResolution var5 = new ScaledResolution(this.mc.gameSettings, this.mc.displayWidth, this.mc.displayHeight);
		int var6 = var5.getScaledWidth();
		int var7 = var5.getScaledHeight();
		FontRenderer var8 = this.mc.fontRenderer;
		this.mc.entityRenderer.func_905_b();
		GL11.glEnable(GL11.GL_BLEND);
		if(Minecraft.isFancyGraphicsEnabled()) {
			this.renderVignette(this.mc.thePlayer.getEntityBrightness(var1), var6, var7);
		}

		ItemStack var9 = this.mc.thePlayer.inventory.armorItemInSlot(3);
		if(!this.mc.gameSettings.thirdPersonView && var9 != null && var9.itemID == Block.pumpkin.blockID) {
			this.renderPumpkinBlur(var6, var7);
		}

		float var10;
		if(!this.mc.thePlayer.func_35160_a(Potion.field_35684_k)) {
			var10 = this.mc.thePlayer.prevTimeInPortal + (this.mc.thePlayer.timeInPortal - this.mc.thePlayer.prevTimeInPortal) * var1;
			if(var10 > 0.0F) {
				this.renderPortalOverlay(var10, var6, var7);
			}
		}

		boolean var11;
		int var12;
		int var16;
		int var17;
		int var18;
		int var20;
		int var22;
		int var47;
		if(!this.mc.playerController.func_35643_e()) {
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/gui/gui.png"));
			InventoryPlayer var30 = this.mc.thePlayer.inventory;
			this.zLevel = -90.0F;
			this.drawTexturedModalRect(var6 / 2 - 91, var7 - 22, 0, 0, 182, 22);
			this.drawTexturedModalRect(var6 / 2 - 91 - 1 + var30.currentItem * 20, var7 - 22 - 1, 0, 22, 24, 22);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/gui/icons.png"));
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR);
			this.drawTexturedModalRect(var6 / 2 - 7, var7 / 2 - 7, 0, 0, 16, 16);
			GL11.glDisable(GL11.GL_BLEND);
			var11 = this.mc.thePlayer.heartsLife / 3 % 2 == 1;
			if(this.mc.thePlayer.heartsLife < 10) {
				var11 = false;
			}

			var12 = this.mc.thePlayer.health;
			int var13 = this.mc.thePlayer.prevHealth;
			this.rand.setSeed((long)(this.updateCounter * 312871));
			boolean var14 = false;
			FoodStats var15 = this.mc.thePlayer.func_35191_at();
			var16 = var15.func_35765_a();
			var17 = var15.func_35769_b();
			int var19;
			if(this.mc.playerController.shouldDrawHUD()) {
				var18 = var6 / 2 - 91;
				var19 = var6 / 2 + 91;
				var20 = this.mc.thePlayer.func_35193_as();
				int var23;
				if(var20 > 0) {
					short var21 = 182;
					var22 = this.mc.thePlayer.field_35211_aX * (var21 + 1) / this.mc.thePlayer.func_35193_as();
					var23 = var7 - 32 + 3;
					this.drawTexturedModalRect(var18, var23, 0, 64, var21, 5);
					if(var22 > 0) {
						this.drawTexturedModalRect(var18, var23, 0, 69, var22, 5);
					}
				}

				var47 = var7 - 39;
				var22 = var47 - 10;
				var23 = this.mc.thePlayer.getPlayerArmorValue();
				int var24 = -1;
				if(this.mc.thePlayer.func_35160_a(Potion.field_35681_l)) {
					var24 = this.updateCounter % 25;
				}

				int var25;
				int var26;
				int var29;
				for(var25 = 0; var25 < 10; ++var25) {
					if(var23 > 0) {
						var26 = var18 + var25 * 8;
						if(var25 * 2 + 1 < var23) {
							this.drawTexturedModalRect(var26, var22, 34, 9, 9, 9);
						}

						if(var25 * 2 + 1 == var23) {
							this.drawTexturedModalRect(var26, var22, 25, 9, 9, 9);
						}

						if(var25 * 2 + 1 > var23) {
							this.drawTexturedModalRect(var26, var22, 16, 9, 9, 9);
						}
					}

					var26 = 16;
					if(this.mc.thePlayer.func_35160_a(Potion.field_35689_u)) {
						var26 += 36;
					}

					byte var27 = 0;
					if(var11) {
						var27 = 1;
					}

					int var28 = var18 + var25 * 8;
					var29 = var47;
					if(var12 <= 4) {
						var29 = var47 + this.rand.nextInt(2);
					}

					if(var25 == var24) {
						var29 -= 2;
					}

					this.drawTexturedModalRect(var28, var29, 16 + var27 * 9, 0, 9, 9);
					if(var11) {
						if(var25 * 2 + 1 < var13) {
							this.drawTexturedModalRect(var28, var29, var26 + 54, 0, 9, 9);
						}

						if(var25 * 2 + 1 == var13) {
							this.drawTexturedModalRect(var28, var29, var26 + 63, 0, 9, 9);
						}
					}

					if(var25 * 2 + 1 < var12) {
						this.drawTexturedModalRect(var28, var29, var26 + 36, 0, 9, 9);
					}

					if(var25 * 2 + 1 == var12) {
						this.drawTexturedModalRect(var28, var29, var26 + 45, 0, 9, 9);
					}
				}

				int var53;
				for(var25 = 0; var25 < 10; ++var25) {
					var26 = var47;
					var53 = 16;
					byte var54 = 0;
					if(this.mc.thePlayer.func_35160_a(Potion.field_35691_s)) {
						var53 += 36;
						var54 = 13;
					}

					if(this.mc.thePlayer.func_35191_at().func_35760_d() <= 0.0F && this.updateCounter % (var16 * 3 + 1) == 0) {
						var26 = var47 + (this.rand.nextInt(3) - 1);
					}

					if(var14) {
						var54 = 1;
					}

					var29 = var19 - var25 * 8 - 9;
					this.drawTexturedModalRect(var29, var26, 16 + var54 * 9, 27, 9, 9);
					if(var14) {
						if(var25 * 2 + 1 < var17) {
							this.drawTexturedModalRect(var29, var26, var53 + 54, 27, 9, 9);
						}

						if(var25 * 2 + 1 == var17) {
							this.drawTexturedModalRect(var29, var26, var53 + 63, 27, 9, 9);
						}
					}

					if(var25 * 2 + 1 < var16) {
						this.drawTexturedModalRect(var29, var26, var53 + 36, 27, 9, 9);
					}

					if(var25 * 2 + 1 == var16) {
						this.drawTexturedModalRect(var29, var26, var53 + 45, 27, 9, 9);
					}
				}

				if(this.mc.thePlayer.isInsideOfMaterial(Material.water)) {
					var25 = (int)Math.ceil((double)(this.mc.thePlayer.air - 2) * 10.0D / 300.0D);
					var26 = (int)Math.ceil((double)this.mc.thePlayer.air * 10.0D / 300.0D) - var25;

					for(var53 = 0; var53 < var25 + var26; ++var53) {
						if(var53 < var25) {
							this.drawTexturedModalRect(var19 - var53 * 8 - 9, var22, 16, 18, 9, 9);
						} else {
							this.drawTexturedModalRect(var19 - var53 * 8 - 9, var22, 25, 18, 9, 9);
						}
					}
				}
			}

			GL11.glDisable(GL11.GL_BLEND);
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);
			GL11.glPushMatrix();
			GL11.glRotatef(120.0F, 1.0F, 0.0F, 0.0F);
			RenderHelper.enableStandardItemLighting();
			GL11.glPopMatrix();

			for(var18 = 0; var18 < 9; ++var18) {
				var19 = var6 / 2 - 90 + var18 * 20 + 2;
				var20 = var7 - 16 - 3;
				this.renderInventorySlot(var18, var19, var20, var1);
			}

			RenderHelper.disableStandardItemLighting();
			GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		}

		if(this.mc.thePlayer.func_22060_M() > 0) {
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			int var31 = this.mc.thePlayer.func_22060_M();
			float var34 = (float)var31 / 100.0F;
			if(var34 > 1.0F) {
				var34 = 1.0F - (float)(var31 - 100) / 10.0F;
			}

			var12 = (int)(220.0F * var34) << 24 | 1052704;
			this.drawRect(0, 0, var6, var7, var12);
			GL11.glEnable(GL11.GL_ALPHA_TEST);
			GL11.glEnable(GL11.GL_DEPTH_TEST);
		}

		if(this.mc.playerController.func_35642_f() && this.mc.thePlayer.field_35210_aY > 0) {
		}

		String var45;
		if(this.mc.gameSettings.showDebugInfo) {
			GL11.glPushMatrix();
			if(Minecraft.hasPaidCheckTime > 0L) {
				GL11.glTranslatef(0.0F, 32.0F, 0.0F);
			}

			var8.drawStringWithShadow("Minecraft Beta 1.8.1 (" + this.mc.debug + ")", 2, 2, 16777215);
			var8.drawStringWithShadow(this.mc.debugInfoRenders(), 2, 12, 16777215);
			var8.drawStringWithShadow(this.mc.func_6262_n(), 2, 22, 16777215);
			var8.drawStringWithShadow(this.mc.debugInfoEntities(), 2, 32, 16777215);
			var8.drawStringWithShadow(this.mc.func_21002_o(), 2, 42, 16777215);
			long var32 = Runtime.getRuntime().maxMemory();
			long var35 = Runtime.getRuntime().totalMemory();
			long var38 = Runtime.getRuntime().freeMemory();
			long var43 = var35 - var38;
			var45 = "Used memory: " + var43 * 100L / var32 + "% (" + var43 / 1024L / 1024L + "MB) of " + var32 / 1024L / 1024L + "MB";
			this.drawString(var8, var45, var6 - var8.getStringWidth(var45) - 2, 2, 14737632);
			var45 = "Allocated memory: " + var35 * 100L / var32 + "% (" + var35 / 1024L / 1024L + "MB)";
			this.drawString(var8, var45, var6 - var8.getStringWidth(var45) - 2, 12, 14737632);
			this.drawString(var8, "x: " + this.mc.thePlayer.posX, 2, 64, 14737632);
			this.drawString(var8, "y: " + this.mc.thePlayer.posY, 2, 72, 14737632);
			this.drawString(var8, "z: " + this.mc.thePlayer.posZ, 2, 80, 14737632);
			this.drawString(var8, "f: " + (MathHelper.floor_double((double)(this.mc.thePlayer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3), 2, 88, 14737632);
			this.drawString(var8, "Seed: " + this.mc.theWorld.getRandomSeed(), 2, 104, 14737632);
			GL11.glPopMatrix();
		} else {
			var8.drawStringWithShadow("Minecraft Beta 1.8.1", 2, 2, 16777215);
		}

		if(this.recordPlayingUpFor > 0) {
			var10 = (float)this.recordPlayingUpFor - var1;
			int var36 = (int)(var10 * 256.0F / 20.0F);
			if(var36 > 255) {
				var36 = 255;
			}

			if(var36 > 0) {
				GL11.glPushMatrix();
				GL11.glTranslatef((float)(var6 / 2), (float)(var7 - 48), 0.0F);
				GL11.glEnable(GL11.GL_BLEND);
				GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				var12 = 16777215;
				if(this.recordIsPlaying) {
					var12 = Color.HSBtoRGB(var10 / 50.0F, 0.7F, 0.6F) & 16777215;
				}

				var8.drawString(this.recordPlaying, -var8.getStringWidth(this.recordPlaying) / 2, -4, var12 + (var36 << 24));
				GL11.glDisable(GL11.GL_BLEND);
				GL11.glPopMatrix();
			}
		}

		byte var33 = 10;
		var11 = false;
		if(this.mc.currentScreen instanceof GuiChat) {
			var33 = 20;
			var11 = true;
		}

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glPushMatrix();
		GL11.glTranslatef(0.0F, (float)(var7 - 48), 0.0F);

		int var41;
		for(var12 = 0; var12 < this.chatMessageList.size() && var12 < var33; ++var12) {
			if(((ChatLine)this.chatMessageList.get(var12)).updateCounter < 200 || var11) {
				double var37 = (double)((ChatLine)this.chatMessageList.get(var12)).updateCounter / 200.0D;
				var37 = 1.0D - var37;
				var37 *= 10.0D;
				if(var37 < 0.0D) {
					var37 = 0.0D;
				}

				if(var37 > 1.0D) {
					var37 = 1.0D;
				}

				var37 *= var37;
				var41 = (int)(255.0D * var37);
				if(var11) {
					var41 = 255;
				}

				if(var41 > 0) {
					byte var44 = 2;
					var17 = -var12 * 9;
					var45 = ((ChatLine)this.chatMessageList.get(var12)).message;
					this.drawRect(var44, var17 - 1, var44 + 320, var17 + 8, var41 / 2 << 24);
					GL11.glEnable(GL11.GL_BLEND);
					var8.drawStringWithShadow(var45, var44, var17, 16777215 + (var41 << 24));
				}
			}
		}

		GL11.glPopMatrix();
		if(this.mc.thePlayer instanceof EntityClientPlayerMP && this.mc.gameSettings.field_35384_x.field_35965_e) {
			NetClientHandler var39 = ((EntityClientPlayerMP)this.mc.thePlayer).sendQueue;
			List var42 = var39.field_35786_c;
			int var40 = var39.field_35785_d;
			var41 = var40;

			for(var16 = 1; var41 > 20; var41 = (var40 + var16 - 1) / var16) {
				++var16;
			}

			var17 = 300 / var16;
			if(var17 > 150) {
				var17 = 150;
			}

			var18 = (var6 - var16 * var17) / 2;
			byte var46 = 10;
			this.drawRect(var18 - 1, var46 - 1, var18 + var17 * var16, var46 + 9 * var41, Integer.MIN_VALUE);

			for(var20 = 0; var20 < var40; ++var20) {
				var47 = var18 + var20 % var16 * var17;
				var22 = var46 + var20 / var16 * 9;
				this.drawRect(var47, var22, var47 + var17 - 1, var22 + 8, 553648127);
				GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
				GL11.glEnable(GL11.GL_ALPHA_TEST);
				if(var20 < var42.size()) {
					GuiSavingLevelString var48 = (GuiSavingLevelString)var42.get(var20);
					var8.drawStringWithShadow(var48.field_35624_a, var47, var22, 16777215);
					this.mc.renderEngine.bindTexture(this.mc.renderEngine.getTexture("/gui/icons.png"));
					boolean var49 = false;
					boolean var51 = false;
					byte var50 = 0;
					var51 = false;
					byte var52;
					if(var48.field_35623_b < 0) {
						var52 = 5;
					} else if(var48.field_35623_b < 150) {
						var52 = 0;
					} else if(var48.field_35623_b < 300) {
						var52 = 1;
					} else if(var48.field_35623_b < 600) {
						var52 = 2;
					} else if(var48.field_35623_b < 1000) {
						var52 = 3;
					} else {
						var52 = 4;
					}

					this.zLevel += 100.0F;
					this.drawTexturedModalRect(var47 + var17 - 12, var22, 0 + var50 * 10, 176 + var52 * 8, 10, 8);
					this.zLevel -= 100.0F;
				}
			}
		}

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
	}

	private void renderPumpkinBlur(int var1, int var2) {
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("%blur%/misc/pumpkinblur.png"));
		Tessellator var3 = Tessellator.instance;
		var3.startDrawingQuads();
		var3.addVertexWithUV(0.0D, (double)var2, -90.0D, 0.0D, 1.0D);
		var3.addVertexWithUV((double)var1, (double)var2, -90.0D, 1.0D, 1.0D);
		var3.addVertexWithUV((double)var1, 0.0D, -90.0D, 1.0D, 0.0D);
		var3.addVertexWithUV(0.0D, 0.0D, -90.0D, 0.0D, 0.0D);
		var3.draw();
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private void renderVignette(float var1, int var2, int var3) {
		var1 = 1.0F - var1;
		if(var1 < 0.0F) {
			var1 = 0.0F;
		}

		if(var1 > 1.0F) {
			var1 = 1.0F;
		}

		this.prevVignetteBrightness = (float)((double)this.prevVignetteBrightness + (double)(var1 - this.prevVignetteBrightness) * 0.01D);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glBlendFunc(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR);
		GL11.glColor4f(this.prevVignetteBrightness, this.prevVignetteBrightness, this.prevVignetteBrightness, 1.0F);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("%blur%/misc/vignette.png"));
		Tessellator var4 = Tessellator.instance;
		var4.startDrawingQuads();
		var4.addVertexWithUV(0.0D, (double)var3, -90.0D, 0.0D, 1.0D);
		var4.addVertexWithUV((double)var2, (double)var3, -90.0D, 1.0D, 1.0D);
		var4.addVertexWithUV((double)var2, 0.0D, -90.0D, 1.0D, 0.0D);
		var4.addVertexWithUV(0.0D, 0.0D, -90.0D, 0.0D, 0.0D);
		var4.draw();
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	private void renderPortalOverlay(float var1, int var2, int var3) {
		if(var1 < 1.0F) {
			var1 *= var1;
			var1 *= var1;
			var1 = var1 * 0.8F + 0.2F;
		}

		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, var1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/terrain.png"));
		float var4 = (float)(Block.portal.blockIndexInTexture % 16) / 16.0F;
		float var5 = (float)(Block.portal.blockIndexInTexture / 16) / 16.0F;
		float var6 = (float)(Block.portal.blockIndexInTexture % 16 + 1) / 16.0F;
		float var7 = (float)(Block.portal.blockIndexInTexture / 16 + 1) / 16.0F;
		Tessellator var8 = Tessellator.instance;
		var8.startDrawingQuads();
		var8.addVertexWithUV(0.0D, (double)var3, -90.0D, (double)var4, (double)var7);
		var8.addVertexWithUV((double)var2, (double)var3, -90.0D, (double)var6, (double)var7);
		var8.addVertexWithUV((double)var2, 0.0D, -90.0D, (double)var6, (double)var5);
		var8.addVertexWithUV(0.0D, 0.0D, -90.0D, (double)var4, (double)var5);
		var8.draw();
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private void renderInventorySlot(int var1, int var2, int var3, float var4) {
		ItemStack var5 = this.mc.thePlayer.inventory.mainInventory[var1];
		if(var5 != null) {
			float var6 = (float)var5.animationsToGo - var4;
			if(var6 > 0.0F) {
				GL11.glPushMatrix();
				float var7 = 1.0F + var6 / 5.0F;
				GL11.glTranslatef((float)(var2 + 8), (float)(var3 + 12), 0.0F);
				GL11.glScalef(1.0F / var7, (var7 + 1.0F) / 2.0F, 1.0F);
				GL11.glTranslatef((float)(-(var2 + 8)), (float)(-(var3 + 12)), 0.0F);
			}

			itemRenderer.renderItemIntoGUI(this.mc.fontRenderer, this.mc.renderEngine, var5, var2, var3);
			if(var6 > 0.0F) {
				GL11.glPopMatrix();
			}

			itemRenderer.renderItemOverlayIntoGUI(this.mc.fontRenderer, this.mc.renderEngine, var5, var2, var3);
		}
	}

	public void updateTick() {
		if(this.recordPlayingUpFor > 0) {
			--this.recordPlayingUpFor;
		}

		++this.updateCounter;

		for(int var1 = 0; var1 < this.chatMessageList.size(); ++var1) {
			++((ChatLine)this.chatMessageList.get(var1)).updateCounter;
		}

	}

	public void clearChatMessages() {
		this.chatMessageList.clear();
	}

	public void addChatMessage(String var1) {
		while(this.mc.fontRenderer.getStringWidth(var1) > 320) {
			int var2;
			for(var2 = 1; var2 < var1.length() && this.mc.fontRenderer.getStringWidth(var1.substring(0, var2 + 1)) <= 320; ++var2) {
			}

			this.addChatMessage(var1.substring(0, var2));
			var1 = var1.substring(var2);
		}

		this.chatMessageList.add(0, new ChatLine(var1));

		while(this.chatMessageList.size() > 50) {
			this.chatMessageList.remove(this.chatMessageList.size() - 1);
		}

	}

	public void setRecordPlayingMessage(String var1) {
		this.recordPlaying = "Now playing: " + var1;
		this.recordPlayingUpFor = 60;
		this.recordIsPlaying = true;
	}

	public void addChatMessageTranslate(String var1) {
		StringTranslate var2 = StringTranslate.getInstance();
		String var3 = var2.translateKey(var1);
		this.addChatMessage(var3);
	}
}
