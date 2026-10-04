package net.minecraft.src;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.ARBMultitexture;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.NVFogDistance;
import org.lwjgl.util.glu.GLU;

public class EntityRenderer {
	public static boolean anaglyphEnable = false;
	public static int anaglyphField;
	private Minecraft mc;
	private float farPlaneDistance = 0.0F;
	public ItemRenderer itemRenderer;
	private int rendererUpdateCount;
	private Entity pointedEntity = null;
	private MouseFilter mouseFilterXAxis = new MouseFilter();
	private MouseFilter mouseFilterYAxis = new MouseFilter();
	private MouseFilter mouseFilterDummy1 = new MouseFilter();
	private MouseFilter mouseFilterDummy2 = new MouseFilter();
	private MouseFilter mouseFilterDummy3 = new MouseFilter();
	private MouseFilter mouseFilterDummy4 = new MouseFilter();
	private float thirdPersonDistance = 4.0F;
	private float field_22227_s = 4.0F;
	private float debugCamYaw = 0.0F;
	private float prevDebugCamYaw = 0.0F;
	private float debugCamPitch = 0.0F;
	private float prevDebugCamPitch = 0.0F;
	private float debugCamFOV = 0.0F;
	private float prevDebugCamFOV = 0.0F;
	private float camRoll = 0.0F;
	private float prevCamRoll = 0.0F;
	public int field_35818_d;
	private int[] field_35811_L;
	private float field_35812_M;
	private float field_35813_N;
	private float field_35814_O;
	private boolean cloudFog = false;
	private double cameraZoom = 1.0D;
	private double cameraYaw = 0.0D;
	private double cameraPitch = 0.0D;
	private long prevFrameTime = System.currentTimeMillis();
	private long renderEndNanoTime = 0L;
	private boolean field_35815_V = false;
	float field_35819_e = 0.0F;
	float field_35816_f = 0.0F;
	float field_35817_g = 0.0F;
	float field_35821_h = 0.0F;
	private Random random = new Random();
	private int rainSoundCounter = 0;
	float[] field_35822_i;
	float[] field_35820_j;
	volatile int unusedVolatile0 = 0;
	volatile int unusedVolatile1 = 0;
	FloatBuffer fogColorBuffer = GLAllocation.createDirectFloatBuffer(16);
	float fogColorRed;
	float fogColorGreen;
	float fogColorBlue;
	private float fogColor2;
	private float fogColor1;
	public int field_35823_q;

	public EntityRenderer(Minecraft var1) {
		this.mc = var1;
		this.itemRenderer = new ItemRenderer(var1);
		this.field_35818_d = var1.renderEngine.allocateAndSetupTexture(new BufferedImage(16, 16, 1));
		this.field_35811_L = new int[256];
	}

	public void updateRenderer() {
		this.func_35809_c();
		this.func_35807_d();
		this.fogColor2 = this.fogColor1;
		this.field_22227_s = this.thirdPersonDistance;
		this.prevDebugCamYaw = this.debugCamYaw;
		this.prevDebugCamPitch = this.debugCamPitch;
		this.prevDebugCamFOV = this.debugCamFOV;
		this.prevCamRoll = this.camRoll;
		if(this.mc.renderViewEntity == null) {
			this.mc.renderViewEntity = this.mc.thePlayer;
		}

		float var1 = this.mc.theWorld.getLightBrightness(MathHelper.floor_double(this.mc.renderViewEntity.posX), MathHelper.floor_double(this.mc.renderViewEntity.posY), MathHelper.floor_double(this.mc.renderViewEntity.posZ));
		float var2 = (float)(3 - this.mc.gameSettings.renderDistance) / 3.0F;
		float var3 = var1 * (1.0F - var2) + var2;
		this.fogColor1 += (var3 - this.fogColor1) * 0.1F;
		++this.rendererUpdateCount;
		this.itemRenderer.updateEquippedItem();
		this.addRainParticles();
	}

	public void getMouseOver(float var1) {
		if(this.mc.renderViewEntity != null) {
			if(this.mc.theWorld != null) {
				double var2 = (double)this.mc.playerController.getBlockReachDistance();
				this.mc.objectMouseOver = this.mc.renderViewEntity.rayTrace(var2, var1);
				double var4 = var2;
				Vec3D var6 = this.mc.renderViewEntity.getPosition(var1);
				if(this.mc.objectMouseOver != null) {
					var4 = this.mc.objectMouseOver.hitVec.distanceTo(var6);
				}

				if(this.mc.playerController.func_35636_i()) {
					var2 = 32.0D;
				} else {
					if(var4 > 3.0D) {
						var4 = 3.0D;
					}

					var2 = var4;
				}

				Vec3D var7 = this.mc.renderViewEntity.getLook(var1);
				Vec3D var8 = var6.addVector(var7.xCoord * var2, var7.yCoord * var2, var7.zCoord * var2);
				this.pointedEntity = null;
				float var9 = 1.0F;
				List var10 = this.mc.theWorld.getEntitiesWithinAABBExcludingEntity(this.mc.renderViewEntity, this.mc.renderViewEntity.boundingBox.addCoord(var7.xCoord * var2, var7.yCoord * var2, var7.zCoord * var2).expand((double)var9, (double)var9, (double)var9));
				double var11 = 0.0D;

				for(int var13 = 0; var13 < var10.size(); ++var13) {
					Entity var14 = (Entity)var10.get(var13);
					if(var14.canBeCollidedWith()) {
						float var15 = var14.getCollisionBorderSize();
						AxisAlignedBB var16 = var14.boundingBox.expand((double)var15, (double)var15, (double)var15);
						MovingObjectPosition var17 = var16.func_1169_a(var6, var8);
						if(var16.isVecInside(var6)) {
							if(0.0D < var11 || var11 == 0.0D) {
								this.pointedEntity = var14;
								var11 = 0.0D;
							}
						} else if(var17 != null) {
							double var18 = var6.distanceTo(var17.hitVec);
							if(var18 < var11 || var11 == 0.0D) {
								this.pointedEntity = var14;
								var11 = var18;
							}
						}
					}
				}

				if(this.pointedEntity != null) {
					this.mc.objectMouseOver = new MovingObjectPosition(this.pointedEntity);
				}

			}
		}
	}

	private void func_35809_c() {
		EntityPlayerSP var1 = (EntityPlayerSP)this.mc.renderViewEntity;
		this.field_35814_O = var1.func_35220_u_();
		this.field_35813_N = this.field_35812_M;
		this.field_35812_M += (this.field_35814_O - this.field_35812_M) * 0.5F;
	}

	private float getFOVModifier(float var1, boolean var2) {
		if(this.field_35823_q > 0) {
			return 90.0F;
		} else {
			EntityPlayer var3 = (EntityPlayer)this.mc.renderViewEntity;
			float var4 = 70.0F;
			if(var2) {
				var4 += this.mc.gameSettings.field_35379_L * 40.0F;
				var4 *= this.field_35813_N + (this.field_35812_M - this.field_35813_N) * var1;
			}

			if(var3.health <= 0) {
				float var5 = (float)var3.deathTime + var1;
				var4 /= (1.0F - 500.0F / (var5 + 500.0F)) * 2.0F + 1.0F;
			}

			if(var3.isInsideOfMaterial(Material.water)) {
				var4 = var4 * 60.0F / 70.0F;
			}

			return var4 + this.prevDebugCamFOV + (this.debugCamFOV - this.prevDebugCamFOV) * var1;
		}
	}

	private void hurtCameraEffect(float var1) {
		EntityLiving var2 = this.mc.renderViewEntity;
		float var3 = (float)var2.hurtTime - var1;
		float var4;
		if(var2.health <= 0) {
			var4 = (float)var2.deathTime + var1;
			GL11.glRotatef(40.0F - 8000.0F / (var4 + 200.0F), 0.0F, 0.0F, 1.0F);
		}

		if(var3 >= 0.0F) {
			var3 /= (float)var2.maxHurtTime;
			var3 = MathHelper.sin(var3 * var3 * var3 * var3 * (float)Math.PI);
			var4 = var2.attackedAtYaw;
			GL11.glRotatef(-var4, 0.0F, 1.0F, 0.0F);
			GL11.glRotatef(-var3 * 14.0F, 0.0F, 0.0F, 1.0F);
			GL11.glRotatef(var4, 0.0F, 1.0F, 0.0F);
		}
	}

	private void setupViewBobbing(float var1) {
		if(this.mc.renderViewEntity instanceof EntityPlayer) {
			EntityPlayer var2 = (EntityPlayer)this.mc.renderViewEntity;
			float var3 = var2.distanceWalkedModified - var2.prevDistanceWalkedModified;
			float var4 = -(var2.distanceWalkedModified + var3 * var1);
			float var5 = var2.prevCameraYaw + (var2.cameraYaw - var2.prevCameraYaw) * var1;
			float var6 = var2.prevCameraPitch + (var2.cameraPitch - var2.prevCameraPitch) * var1;
			GL11.glTranslatef(MathHelper.sin(var4 * (float)Math.PI) * var5 * 0.5F, -Math.abs(MathHelper.cos(var4 * (float)Math.PI) * var5), 0.0F);
			GL11.glRotatef(MathHelper.sin(var4 * (float)Math.PI) * var5 * 3.0F, 0.0F, 0.0F, 1.0F);
			GL11.glRotatef(Math.abs(MathHelper.cos(var4 * (float)Math.PI - 0.2F) * var5) * 5.0F, 1.0F, 0.0F, 0.0F);
			GL11.glRotatef(var6, 1.0F, 0.0F, 0.0F);
		}
	}

	private void orientCamera(float var1) {
		EntityLiving var2 = this.mc.renderViewEntity;
		float var3 = var2.yOffset - 1.62F;
		double var4 = var2.prevPosX + (var2.posX - var2.prevPosX) * (double)var1;
		double var6 = var2.prevPosY + (var2.posY - var2.prevPosY) * (double)var1 - (double)var3;
		double var8 = var2.prevPosZ + (var2.posZ - var2.prevPosZ) * (double)var1;
		GL11.glRotatef(this.prevCamRoll + (this.camRoll - this.prevCamRoll) * var1, 0.0F, 0.0F, 1.0F);
		if(var2.isPlayerSleeping()) {
			var3 = (float)((double)var3 + 1.0D);
			GL11.glTranslatef(0.0F, 0.3F, 0.0F);
			if(!this.mc.gameSettings.debugCamEnable) {
				int var10 = this.mc.theWorld.getBlockId(MathHelper.floor_double(var2.posX), MathHelper.floor_double(var2.posY), MathHelper.floor_double(var2.posZ));
				if(var10 == Block.bed.blockID) {
					int var11 = this.mc.theWorld.getBlockMetadata(MathHelper.floor_double(var2.posX), MathHelper.floor_double(var2.posY), MathHelper.floor_double(var2.posZ));
					int var12 = var11 & 3;
					GL11.glRotatef((float)(var12 * 90), 0.0F, 1.0F, 0.0F);
				}

				GL11.glRotatef(var2.prevRotationYaw + (var2.rotationYaw - var2.prevRotationYaw) * var1 + 180.0F, 0.0F, -1.0F, 0.0F);
				GL11.glRotatef(var2.prevRotationPitch + (var2.rotationPitch - var2.prevRotationPitch) * var1, -1.0F, 0.0F, 0.0F);
			}
		} else if(this.mc.gameSettings.thirdPersonView) {
			double var27 = (double)(this.field_22227_s + (this.thirdPersonDistance - this.field_22227_s) * var1);
			float var13;
			float var28;
			if(this.mc.gameSettings.debugCamEnable) {
				var28 = this.prevDebugCamYaw + (this.debugCamYaw - this.prevDebugCamYaw) * var1;
				var13 = this.prevDebugCamPitch + (this.debugCamPitch - this.prevDebugCamPitch) * var1;
				GL11.glTranslatef(0.0F, 0.0F, (float)(-var27));
				GL11.glRotatef(var13, 1.0F, 0.0F, 0.0F);
				GL11.glRotatef(var28, 0.0F, 1.0F, 0.0F);
			} else {
				var28 = var2.rotationYaw;
				var13 = var2.rotationPitch;
				double var14 = (double)(-MathHelper.sin(var28 / 180.0F * (float)Math.PI) * MathHelper.cos(var13 / 180.0F * (float)Math.PI)) * var27;
				double var16 = (double)(MathHelper.cos(var28 / 180.0F * (float)Math.PI) * MathHelper.cos(var13 / 180.0F * (float)Math.PI)) * var27;
				double var18 = (double)(-MathHelper.sin(var13 / 180.0F * (float)Math.PI)) * var27;

				for(int var20 = 0; var20 < 8; ++var20) {
					float var21 = (float)((var20 & 1) * 2 - 1);
					float var22 = (float)((var20 >> 1 & 1) * 2 - 1);
					float var23 = (float)((var20 >> 2 & 1) * 2 - 1);
					var21 *= 0.1F;
					var22 *= 0.1F;
					var23 *= 0.1F;
					MovingObjectPosition var24 = this.mc.theWorld.rayTraceBlocks(Vec3D.createVector(var4 + (double)var21, var6 + (double)var22, var8 + (double)var23), Vec3D.createVector(var4 - var14 + (double)var21 + (double)var23, var6 - var18 + (double)var22, var8 - var16 + (double)var23));
					if(var24 != null) {
						double var25 = var24.hitVec.distanceTo(Vec3D.createVector(var4, var6, var8));
						if(var25 < var27) {
							var27 = var25;
						}
					}
				}

				GL11.glRotatef(var2.rotationPitch - var13, 1.0F, 0.0F, 0.0F);
				GL11.glRotatef(var2.rotationYaw - var28, 0.0F, 1.0F, 0.0F);
				GL11.glTranslatef(0.0F, 0.0F, (float)(-var27));
				GL11.glRotatef(var28 - var2.rotationYaw, 0.0F, 1.0F, 0.0F);
				GL11.glRotatef(var13 - var2.rotationPitch, 1.0F, 0.0F, 0.0F);
			}
		} else {
			GL11.glTranslatef(0.0F, 0.0F, -0.1F);
		}

		if(!this.mc.gameSettings.debugCamEnable) {
			GL11.glRotatef(var2.prevRotationPitch + (var2.rotationPitch - var2.prevRotationPitch) * var1, 1.0F, 0.0F, 0.0F);
			GL11.glRotatef(var2.prevRotationYaw + (var2.rotationYaw - var2.prevRotationYaw) * var1 + 180.0F, 0.0F, 1.0F, 0.0F);
		}

		GL11.glTranslatef(0.0F, var3, 0.0F);
		var4 = var2.prevPosX + (var2.posX - var2.prevPosX) * (double)var1;
		var6 = var2.prevPosY + (var2.posY - var2.prevPosY) * (double)var1 - (double)var3;
		var8 = var2.prevPosZ + (var2.posZ - var2.prevPosZ) * (double)var1;
		this.cloudFog = this.mc.renderGlobal.func_27307_a(var4, var6, var8, var1);
	}

	private void setupCameraTransform(float var1, int var2) {
		this.farPlaneDistance = (float)(256 >> this.mc.gameSettings.renderDistance);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		float var3 = 0.07F;
		if(this.mc.gameSettings.anaglyph) {
			GL11.glTranslatef((float)(-(var2 * 2 - 1)) * var3, 0.0F, 0.0F);
		}

		if(this.cameraZoom != 1.0D) {
			GL11.glTranslatef((float)this.cameraYaw, (float)(-this.cameraPitch), 0.0F);
			GL11.glScaled(this.cameraZoom, this.cameraZoom, 1.0D);
			GLU.gluPerspective(this.getFOVModifier(var1, true), (float)this.mc.displayWidth / (float)this.mc.displayHeight, 0.05F, this.farPlaneDistance * 2.0F);
		} else {
			GLU.gluPerspective(this.getFOVModifier(var1, true), (float)this.mc.displayWidth / (float)this.mc.displayHeight, 0.05F, this.farPlaneDistance * 2.0F);
		}

		float var4;
		if(this.mc.playerController.func_35643_e()) {
			var4 = 2.0F / 3.0F;
			GL11.glScalef(1.0F, var4, 1.0F);
		}

		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
		if(this.mc.gameSettings.anaglyph) {
			GL11.glTranslatef((float)(var2 * 2 - 1) * 0.1F, 0.0F, 0.0F);
		}

		this.hurtCameraEffect(var1);
		if(this.mc.gameSettings.viewBobbing) {
			this.setupViewBobbing(var1);
		}

		var4 = this.mc.thePlayer.prevTimeInPortal + (this.mc.thePlayer.timeInPortal - this.mc.thePlayer.prevTimeInPortal) * var1;
		if(var4 > 0.0F) {
			byte var5 = 20;
			if(this.mc.thePlayer.func_35160_a(Potion.field_35684_k)) {
				var5 = 7;
			}

			float var6 = 5.0F / (var4 * var4 + 5.0F) - var4 * 0.04F;
			var6 *= var6;
			GL11.glRotatef(((float)this.rendererUpdateCount + var1) * (float)var5, 0.0F, 1.0F, 1.0F);
			GL11.glScalef(1.0F / var6, 1.0F, 1.0F);
			GL11.glRotatef(-((float)this.rendererUpdateCount + var1) * (float)var5, 0.0F, 1.0F, 1.0F);
		}

		this.orientCamera(var1);
		if(this.field_35823_q > 0) {
			int var7 = this.field_35823_q - 1;
			if(var7 == 1) {
				GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
			}

			if(var7 == 2) {
				GL11.glRotatef(180.0F, 0.0F, 1.0F, 0.0F);
			}

			if(var7 == 3) {
				GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
			}

			if(var7 == 4) {
				GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
			}

			if(var7 == 5) {
				GL11.glRotatef(-90.0F, 1.0F, 0.0F, 0.0F);
			}
		}

	}

	private void func_4135_b(float var1, int var2) {
		if(this.field_35823_q <= 0) {
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			float var3 = 0.07F;
			if(this.mc.gameSettings.anaglyph) {
				GL11.glTranslatef((float)(-(var2 * 2 - 1)) * var3, 0.0F, 0.0F);
			}

			if(this.cameraZoom != 1.0D) {
				GL11.glTranslatef((float)this.cameraYaw, (float)(-this.cameraPitch), 0.0F);
				GL11.glScaled(this.cameraZoom, this.cameraZoom, 1.0D);
				GLU.gluPerspective(this.getFOVModifier(var1, false), (float)this.mc.displayWidth / (float)this.mc.displayHeight, 0.05F, this.farPlaneDistance * 2.0F);
			} else {
				GLU.gluPerspective(this.getFOVModifier(var1, false), (float)this.mc.displayWidth / (float)this.mc.displayHeight, 0.05F, this.farPlaneDistance * 2.0F);
			}

			if(this.mc.playerController.func_35643_e()) {
				float var4 = 2.0F / 3.0F;
				GL11.glScalef(1.0F, var4, 1.0F);
			}

			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glLoadIdentity();
			if(this.mc.gameSettings.anaglyph) {
				GL11.glTranslatef((float)(var2 * 2 - 1) * 0.1F, 0.0F, 0.0F);
			}

			GL11.glPushMatrix();
			this.hurtCameraEffect(var1);
			if(this.mc.gameSettings.viewBobbing) {
				this.setupViewBobbing(var1);
			}

			if(!this.mc.gameSettings.thirdPersonView && !this.mc.renderViewEntity.isPlayerSleeping() && !this.mc.gameSettings.hideGUI && !this.mc.playerController.func_35643_e()) {
				this.func_35806_b((double)var1);
				this.itemRenderer.renderItemInFirstPerson(var1);
				this.func_35810_a((double)var1);
			}

			GL11.glPopMatrix();
			if(!this.mc.gameSettings.thirdPersonView && !this.mc.renderViewEntity.isPlayerSleeping()) {
				this.itemRenderer.renderOverlays(var1);
				this.hurtCameraEffect(var1);
			}

			if(this.mc.gameSettings.viewBobbing) {
				this.setupViewBobbing(var1);
			}

		}
	}

	public void func_35810_a(double var1) {
		GL13.glClientActiveTexture(ARBMultitexture.GL_TEXTURE1_ARB);
		GL13.glActiveTexture(ARBMultitexture.GL_TEXTURE1_ARB);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL13.glClientActiveTexture(ARBMultitexture.GL_TEXTURE0_ARB);
		GL13.glActiveTexture(ARBMultitexture.GL_TEXTURE0_ARB);
	}

	public void func_35806_b(double var1) {
		GL13.glClientActiveTexture(ARBMultitexture.GL_TEXTURE1_ARB);
		GL13.glActiveTexture(ARBMultitexture.GL_TEXTURE1_ARB);
		GL11.glMatrixMode(GL11.GL_TEXTURE);
		GL11.glLoadIdentity();
		float var3 = 0.00390625F;
		GL11.glScalef(var3, var3, var3);
		GL11.glTranslatef(8.0F, 8.0F, 8.0F);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		this.mc.renderEngine.bindTexture(this.field_35818_d);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL13.glClientActiveTexture(ARBMultitexture.GL_TEXTURE0_ARB);
		GL13.glActiveTexture(ARBMultitexture.GL_TEXTURE0_ARB);
	}

	private void func_35807_d() {
		this.field_35816_f = (float)((double)this.field_35816_f + (Math.random() - Math.random()) * Math.random() * Math.random());
		this.field_35821_h = (float)((double)this.field_35821_h + (Math.random() - Math.random()) * Math.random() * Math.random());
		this.field_35816_f = (float)((double)this.field_35816_f * 0.9D);
		this.field_35821_h = (float)((double)this.field_35821_h * 0.9D);
		this.field_35819_e += (this.field_35816_f - this.field_35819_e) * 1.0F;
		this.field_35817_g += (this.field_35821_h - this.field_35817_g) * 1.0F;
		this.field_35815_V = true;
	}

	private void func_35808_e() {
		World var1 = this.mc.theWorld;
		if(var1 != null) {
			for(int var2 = 0; var2 < 256; ++var2) {
				float var3 = var1.func_35464_b(1.0F) * 0.95F + 0.05F;
				float var4 = var1.worldProvider.lightBrightnessTable[var2 / 16] * var3;
				float var5 = var1.worldProvider.lightBrightnessTable[var2 % 16] * (this.field_35819_e * 0.1F + 1.5F);
				if(var1.field_27172_i > 0) {
					var4 = var1.worldProvider.lightBrightnessTable[var2 / 16];
				}

				float var6 = var4 * (var1.func_35464_b(1.0F) * 0.65F + 0.35F);
				float var7 = var4 * (var1.func_35464_b(1.0F) * 0.65F + 0.35F);
				float var10 = var5 * ((var5 * 0.6F + 0.4F) * 0.6F + 0.4F);
				float var11 = var5 * (var5 * var5 * 0.6F + 0.4F);
				float var12 = var6 + var5;
				float var13 = var7 + var10;
				float var14 = var4 + var11;
				var12 = var12 * 0.96F + 0.03F;
				var13 = var13 * 0.96F + 0.03F;
				var14 = var14 * 0.96F + 0.03F;
				float var15 = this.mc.gameSettings.field_35380_M;
				if(var12 > 1.0F) {
					var12 = 1.0F;
				}

				if(var13 > 1.0F) {
					var13 = 1.0F;
				}

				if(var14 > 1.0F) {
					var14 = 1.0F;
				}

				float var16 = 1.0F - var12;
				float var17 = 1.0F - var13;
				float var18 = 1.0F - var14;
				var16 = 1.0F - var16 * var16 * var16 * var16;
				var17 = 1.0F - var17 * var17 * var17 * var17;
				var18 = 1.0F - var18 * var18 * var18 * var18;
				var12 = var12 * (1.0F - var15) + var16 * var15;
				var13 = var13 * (1.0F - var15) + var17 * var15;
				var14 = var14 * (1.0F - var15) + var18 * var15;
				var12 = var12 * 0.96F + 0.03F;
				var13 = var13 * 0.96F + 0.03F;
				var14 = var14 * 0.96F + 0.03F;
				if(var12 > 1.0F) {
					var12 = 1.0F;
				}

				if(var13 > 1.0F) {
					var13 = 1.0F;
				}

				if(var14 > 1.0F) {
					var14 = 1.0F;
				}

				if(var12 < 0.0F) {
					var12 = 0.0F;
				}

				if(var13 < 0.0F) {
					var13 = 0.0F;
				}

				if(var14 < 0.0F) {
					var14 = 0.0F;
				}

				short var19 = 255;
				int var20 = (int)(var12 * 255.0F);
				int var21 = (int)(var13 * 255.0F);
				int var22 = (int)(var14 * 255.0F);
				this.field_35811_L[var2] = var19 << 24 | var20 << 16 | var21 << 8 | var22;
			}

			this.mc.renderEngine.createTextureFromBytes(this.field_35811_L, 16, 16, this.field_35818_d);
		}
	}

	public void updateCameraAndRender(float var1) {
		if(this.field_35815_V) {
			this.func_35808_e();
		}

		if(!Display.isActive()) {
			if(System.currentTimeMillis() - this.prevFrameTime > 500L) {
				this.mc.displayInGameMenu();
			}
		} else {
			this.prevFrameTime = System.currentTimeMillis();
		}

		if(this.mc.inGameHasFocus) {
			this.mc.mouseHelper.mouseXYChange();
			float var2 = this.mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
			float var3 = var2 * var2 * var2 * 8.0F;
			float var4 = (float)this.mc.mouseHelper.deltaX * var3;
			float var5 = (float)this.mc.mouseHelper.deltaY * var3;
			byte var6 = 1;
			if(this.mc.gameSettings.invertMouse) {
				var6 = -1;
			}

			if(this.mc.gameSettings.smoothCamera) {
				var4 = this.mouseFilterXAxis.func_22386_a(var4, 0.05F * var3);
				var5 = this.mouseFilterYAxis.func_22386_a(var5, 0.05F * var3);
			}

			this.mc.thePlayer.setAngles(var4, var5 * (float)var6);
		}

		if(!this.mc.skipRenderWorld) {
			anaglyphEnable = this.mc.gameSettings.anaglyph;
			ScaledResolution var13 = new ScaledResolution(this.mc.gameSettings, this.mc.displayWidth, this.mc.displayHeight);
			int var14 = var13.getScaledWidth();
			int var15 = var13.getScaledHeight();
			int var16 = Mouse.getX() * var14 / this.mc.displayWidth;
			int var17 = var15 - Mouse.getY() * var15 / this.mc.displayHeight - 1;
			short var7 = 200;
			if(this.mc.gameSettings.limitFramerate == 1) {
				var7 = 120;
			}

			if(this.mc.gameSettings.limitFramerate == 2) {
				var7 = 40;
			}

			long var8;
			if(this.mc.theWorld != null) {
				if(this.mc.gameSettings.limitFramerate == 0) {
					this.renderWorld(var1, 0L);
				} else {
					this.renderWorld(var1, this.renderEndNanoTime + (long)(1000000000 / var7));
				}

				if(this.mc.gameSettings.limitFramerate == 2) {
					var8 = (this.renderEndNanoTime + (long)(1000000000 / var7) - System.nanoTime()) / 1000000L;
					if(var8 > 0L && var8 < 500L) {
						try {
							Thread.sleep(var8);
						} catch (InterruptedException var12) {
							var12.printStackTrace();
						}
					}
				}

				this.renderEndNanoTime = System.nanoTime();
				if(!this.mc.gameSettings.hideGUI || this.mc.currentScreen != null) {
					this.mc.ingameGUI.renderGameOverlay(var1, this.mc.currentScreen != null, var16, var17);
				}
			} else {
				GL11.glViewport(0, 0, this.mc.displayWidth, this.mc.displayHeight);
				GL11.glMatrixMode(GL11.GL_PROJECTION);
				GL11.glLoadIdentity();
				GL11.glMatrixMode(GL11.GL_MODELVIEW);
				GL11.glLoadIdentity();
				this.func_905_b();
				if(this.mc.gameSettings.limitFramerate == 2) {
					var8 = (this.renderEndNanoTime + (long)(1000000000 / var7) - System.nanoTime()) / 1000000L;
					if(var8 < 0L) {
						var8 += 10L;
					}

					if(var8 > 0L && var8 < 500L) {
						try {
							Thread.sleep(var8);
						} catch (InterruptedException var11) {
							var11.printStackTrace();
						}
					}
				}

				this.renderEndNanoTime = System.nanoTime();
			}

			if(this.mc.currentScreen != null) {
				GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
				this.mc.currentScreen.drawScreen(var16, var17, var1);
				if(this.mc.currentScreen != null && this.mc.currentScreen.guiParticles != null) {
					this.mc.currentScreen.guiParticles.draw(var1);
				}
			}

		}
	}

	public void renderWorld(float var1, long var2) {
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		if(this.mc.renderViewEntity == null) {
			this.mc.renderViewEntity = this.mc.thePlayer;
		}

		this.getMouseOver(var1);
		EntityLiving var4 = this.mc.renderViewEntity;
		RenderGlobal var5 = this.mc.renderGlobal;
		EffectRenderer var6 = this.mc.effectRenderer;
		double var7 = var4.lastTickPosX + (var4.posX - var4.lastTickPosX) * (double)var1;
		double var9 = var4.lastTickPosY + (var4.posY - var4.lastTickPosY) * (double)var1;
		double var11 = var4.lastTickPosZ + (var4.posZ - var4.lastTickPosZ) * (double)var1;
		IChunkProvider var13 = this.mc.theWorld.getIChunkProvider();
		int var16;
		if(var13 instanceof ChunkProviderLoadOrGenerate) {
			ChunkProviderLoadOrGenerate var14 = (ChunkProviderLoadOrGenerate)var13;
			int var15 = MathHelper.floor_float((float)((int)var7)) >> 4;
			var16 = MathHelper.floor_float((float)((int)var11)) >> 4;
			var14.setCurrentChunkOver(var15, var16);
		}

		for(int var18 = 0; var18 < 2; ++var18) {
			if(this.mc.gameSettings.anaglyph) {
				anaglyphField = var18;
				if(anaglyphField == 0) {
					GL11.glColorMask(false, true, true, false);
				} else {
					GL11.glColorMask(true, false, false, false);
				}
			}

			GL11.glViewport(0, 0, this.mc.displayWidth, this.mc.displayHeight);
			this.updateFogColor(var1);
			GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);
			GL11.glEnable(GL11.GL_CULL_FACE);
			this.setupCameraTransform(var1, var18);
			ClippingHelperImpl.getInstance();
			if(this.mc.gameSettings.renderDistance < 2) {
				this.setupFog(-1, var1);
				var5.renderSky(var1);
			}

			GL11.glEnable(GL11.GL_FOG);
			this.setupFog(1, var1);
			if(this.mc.gameSettings.ambientOcclusion) {
				GL11.glShadeModel(GL11.GL_SMOOTH);
			}

			Frustrum var19 = new Frustrum();
			var19.setPosition(var7, var9, var11);
			this.mc.renderGlobal.clipRenderersByFrustrum(var19, var1);
			if(var18 == 0) {
				while(!this.mc.renderGlobal.updateRenderers(var4, false) && var2 != 0L) {
					long var20 = var2 - System.nanoTime();
					if(var20 < 0L || var20 > 1000000000L) {
						break;
					}
				}
			}

			this.setupFog(0, var1);
			GL11.glEnable(GL11.GL_FOG);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/terrain.png"));
			RenderHelper.disableStandardItemLighting();
			var5.sortAndRender(var4, 0, (double)var1);
			GL11.glShadeModel(GL11.GL_FLAT);
			EntityPlayer var21;
			if(this.field_35823_q == 0) {
				RenderHelper.enableStandardItemLighting();
				var5.renderEntities(var4.getPosition(var1), var19, var1);
				this.func_35806_b((double)var1);
				var6.func_1187_b(var4, var1);
				RenderHelper.disableStandardItemLighting();
				this.setupFog(0, var1);
				var6.renderParticles(var4, var1);
				this.func_35810_a((double)var1);
				if(this.mc.objectMouseOver != null && var4.isInsideOfMaterial(Material.water) && var4 instanceof EntityPlayer) {
					var21 = (EntityPlayer)var4;
					GL11.glDisable(GL11.GL_ALPHA_TEST);
					var5.drawBlockBreaking(var21, this.mc.objectMouseOver, 0, var21.inventory.getCurrentItem(), var1);
					var5.drawSelectionBox(var21, this.mc.objectMouseOver, 0, var21.inventory.getCurrentItem(), var1);
					GL11.glEnable(GL11.GL_ALPHA_TEST);
				}
			}

			GL11.glDisable(GL11.GL_BLEND);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glDepthMask(true);
			this.setupFog(0, var1);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/terrain.png"));
			if(this.mc.gameSettings.fancyGraphics) {
				if(this.mc.gameSettings.ambientOcclusion) {
					GL11.glShadeModel(GL11.GL_SMOOTH);
				}

				GL11.glColorMask(false, false, false, false);
				var16 = var5.sortAndRender(var4, 1, (double)var1);
				if(this.mc.gameSettings.anaglyph) {
					if(anaglyphField == 0) {
						GL11.glColorMask(false, true, true, true);
					} else {
						GL11.glColorMask(true, false, false, true);
					}
				} else {
					GL11.glColorMask(true, true, true, true);
				}

				if(var16 > 0) {
					var5.renderAllRenderLists(1, (double)var1);
				}

				GL11.glShadeModel(GL11.GL_FLAT);
			} else {
				var5.sortAndRender(var4, 1, (double)var1);
			}

			GL11.glDepthMask(true);
			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_BLEND);
			if(this.cameraZoom == 1.0D && var4 instanceof EntityPlayer && this.mc.objectMouseOver != null && !var4.isInsideOfMaterial(Material.water)) {
				var21 = (EntityPlayer)var4;
				GL11.glDisable(GL11.GL_ALPHA_TEST);
				var5.drawBlockBreaking(var21, this.mc.objectMouseOver, 0, var21.inventory.getCurrentItem(), var1);
				var5.drawSelectionBox(var21, this.mc.objectMouseOver, 0, var21.inventory.getCurrentItem(), var1);
				GL11.glEnable(GL11.GL_ALPHA_TEST);
			}

			this.renderRainSnow(var1);
			GL11.glDisable(GL11.GL_FOG);
			if(this.pointedEntity != null) {
			}

			GL11.glPushMatrix();
			this.setupFog(0, var1);
			GL11.glEnable(GL11.GL_FOG);
			var5.renderClouds(var1);
			GL11.glDisable(GL11.GL_FOG);
			this.setupFog(1, var1);
			GL11.glPopMatrix();
			if(this.cameraZoom == 1.0D) {
				GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
				this.func_4135_b(var1, var18);
			}

			if(!this.mc.gameSettings.anaglyph) {
				return;
			}
		}

		GL11.glColorMask(true, true, true, false);
	}

	private void addRainParticles() {
		float var1 = this.mc.theWorld.getRainStrength(1.0F);
		if(!this.mc.gameSettings.fancyGraphics) {
			var1 /= 2.0F;
		}

		if(var1 != 0.0F) {
			this.random.setSeed((long)this.rendererUpdateCount * 312987231L);
			EntityLiving var2 = this.mc.renderViewEntity;
			World var3 = this.mc.theWorld;
			int var4 = MathHelper.floor_double(var2.posX);
			int var5 = MathHelper.floor_double(var2.posY);
			int var6 = MathHelper.floor_double(var2.posZ);
			byte var7 = 10;
			double var8 = 0.0D;
			double var10 = 0.0D;
			double var12 = 0.0D;
			int var14 = 0;

			for(int var15 = 0; var15 < (int)(100.0F * var1 * var1); ++var15) {
				int var16 = var4 + this.random.nextInt(var7) - this.random.nextInt(var7);
				int var17 = var6 + this.random.nextInt(var7) - this.random.nextInt(var7);
				int var18 = var3.func_35461_e(var16, var17);
				int var19 = var3.getBlockId(var16, var18 - 1, var17);
				if(var18 <= var5 + var7 && var18 >= var5 - var7 && var3.getWorldChunkManager().getBiomeGenAt(var16, var17).canSpawnLightningBolt()) {
					float var20 = this.random.nextFloat();
					float var21 = this.random.nextFloat();
					if(var19 > 0) {
						if(Block.blocksList[var19].blockMaterial == Material.lava) {
							this.mc.effectRenderer.addEffect(new EntitySmokeFX(var3, (double)((float)var16 + var20), (double)((float)var18 + 0.1F) - Block.blocksList[var19].minY, (double)((float)var17 + var21), 0.0D, 0.0D, 0.0D));
						} else {
							++var14;
							if(this.random.nextInt(var14) == 0) {
								var8 = (double)((float)var16 + var20);
								var10 = (double)((float)var18 + 0.1F) - Block.blocksList[var19].minY;
								var12 = (double)((float)var17 + var21);
							}

							this.mc.effectRenderer.addEffect(new EntityRainFX(var3, (double)((float)var16 + var20), (double)((float)var18 + 0.1F) - Block.blocksList[var19].minY, (double)((float)var17 + var21)));
						}
					}
				}
			}

			if(var14 > 0 && this.random.nextInt(3) < this.rainSoundCounter++) {
				this.rainSoundCounter = 0;
				if(var10 > var2.posY + 1.0D && var3.func_35461_e(MathHelper.floor_double(var2.posX), MathHelper.floor_double(var2.posZ)) > MathHelper.floor_double(var2.posY)) {
					this.mc.theWorld.playSoundEffect(var8, var10, var12, "ambient.weather.rain", 0.1F, 0.5F);
				} else {
					this.mc.theWorld.playSoundEffect(var8, var10, var12, "ambient.weather.rain", 0.2F, 1.0F);
				}
			}

		}
	}

	protected void renderRainSnow(float var1) {
		float var2 = this.mc.theWorld.getRainStrength(var1);
		if(var2 > 0.0F) {
			this.func_35806_b((double)var1);
			if(this.field_35822_i == null) {
				this.field_35822_i = new float[1024];
				this.field_35820_j = new float[1024];

				for(int var3 = 0; var3 < 32; ++var3) {
					for(int var4 = 0; var4 < 32; ++var4) {
						float var5 = (float)(var4 - 16);
						float var6 = (float)(var3 - 16);
						float var7 = MathHelper.sqrt_float(var5 * var5 + var6 * var6);
						this.field_35822_i[var3 << 5 | var4] = -var6 / var7;
						this.field_35820_j[var3 << 5 | var4] = var5 / var7;
					}
				}
			}

			EntityLiving var41 = this.mc.renderViewEntity;
			World var42 = this.mc.theWorld;
			int var43 = MathHelper.floor_double(var41.posX);
			int var44 = MathHelper.floor_double(var41.posY);
			int var45 = MathHelper.floor_double(var41.posZ);
			Tessellator var8 = Tessellator.instance;
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glNormal3f(0.0F, 1.0F, 0.0F);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glAlphaFunc(GL11.GL_GREATER, 0.01F);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/environment/snow.png"));
			double var9 = var41.lastTickPosX + (var41.posX - var41.lastTickPosX) * (double)var1;
			double var11 = var41.lastTickPosY + (var41.posY - var41.lastTickPosY) * (double)var1;
			double var13 = var41.lastTickPosZ + (var41.posZ - var41.lastTickPosZ) * (double)var1;
			int var15 = MathHelper.floor_double(var11);
			byte var16 = 5;
			if(this.mc.gameSettings.fancyGraphics) {
				var16 = 10;
			}

			BiomeGenBase[] var17 = var42.getWorldChunkManager().func_4069_a(var43 - var16, var45 - var16, var16 * 2 + 1, var16 * 2 + 1);
			boolean var18 = false;
			byte var19 = -1;
			float var20 = (float)this.rendererUpdateCount + var1;
			if(this.mc.gameSettings.fancyGraphics) {
				var16 = 10;
			}

			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			int var46 = 0;

			for(int var21 = var43 - var16; var21 <= var43 + var16; ++var21) {
				for(int var22 = var45 - var16; var22 <= var45 + var16; ++var22) {
					int var23 = (var22 - var45 + 16) * 32 + var21 - var43 + 16;
					float var24 = this.field_35822_i[var23] * 0.5F;
					float var25 = this.field_35820_j[var23] * 0.5F;
					BiomeGenBase var26 = var17[var46++];
					if(var26.canSpawnLightningBolt() || var26.getEnableSnow()) {
						int var27 = var42.func_35461_e(var21, var22);
						int var28 = var44 - var16;
						int var29 = var44 + var16;
						if(var28 < var27) {
							var28 = var27;
						}

						if(var29 < var27) {
							var29 = var27;
						}

						float var30 = 1.0F;
						int var31 = var27;
						if(var27 < var15) {
							var31 = var15;
						}

						if(var28 != var29) {
							this.random.setSeed((long)(var21 * var21 * 3121 + var21 * 45238971 ^ var22 * var22 * 418711 + var22 * 13761));
							float var32;
							double var35;
							if(var26.canSpawnLightningBolt()) {
								if(var19 != 0) {
									if(var19 >= 0) {
										var8.draw();
									}

									var19 = 0;
									GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/environment/rain.png"));
									var8.startDrawingQuads();
								}

								var32 = ((float)(this.rendererUpdateCount + var21 * var21 * 3121 + var21 * 45238971 + var22 * var22 * 418711 + var22 * 13761 & 31) + var1) / 32.0F * (3.0F + this.random.nextFloat());
								double var33 = (double)((float)var21 + 0.5F) - var41.posX;
								var35 = (double)((float)var22 + 0.5F) - var41.posZ;
								float var37 = MathHelper.sqrt_double(var33 * var33 + var35 * var35) / (float)var16;
								float var38 = 1.0F;
								var8.func_35835_b(var42.func_35451_b(var21, var31, var22, 0));
								var8.setColorRGBA_F(var38, var38, var38, ((1.0F - var37 * var37) * 0.5F + 0.5F) * var2);
								var8.setTranslationD(-var9 * 1.0D, -var11 * 1.0D, -var13 * 1.0D);
								var8.addVertexWithUV((double)((float)var21 - var24) + 0.5D, (double)var28, (double)((float)var22 - var25) + 0.5D, (double)(0.0F * var30), (double)((float)var28 * var30 / 4.0F + var32 * var30));
								var8.addVertexWithUV((double)((float)var21 + var24) + 0.5D, (double)var28, (double)((float)var22 + var25) + 0.5D, (double)(1.0F * var30), (double)((float)var28 * var30 / 4.0F + var32 * var30));
								var8.addVertexWithUV((double)((float)var21 + var24) + 0.5D, (double)var29, (double)((float)var22 + var25) + 0.5D, (double)(1.0F * var30), (double)((float)var29 * var30 / 4.0F + var32 * var30));
								var8.addVertexWithUV((double)((float)var21 - var24) + 0.5D, (double)var29, (double)((float)var22 - var25) + 0.5D, (double)(0.0F * var30), (double)((float)var29 * var30 / 4.0F + var32 * var30));
								var8.setTranslationD(0.0D, 0.0D, 0.0D);
							} else {
								if(var19 != 1) {
									if(var19 >= 0) {
										var8.draw();
									}

									var19 = 1;
									GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.mc.renderEngine.getTexture("/environment/snow.png"));
									var8.startDrawingQuads();
								}

								var32 = ((float)(this.rendererUpdateCount & 511) + var1) / 512.0F;
								float var47 = this.random.nextFloat() + var20 * 0.01F * (float)this.random.nextGaussian();
								float var34 = this.random.nextFloat() + var20 * (float)this.random.nextGaussian() * 0.001F;
								var35 = (double)((float)var21 + 0.5F) - var41.posX;
								double var48 = (double)((float)var22 + 0.5F) - var41.posZ;
								float var39 = MathHelper.sqrt_double(var35 * var35 + var48 * var48) / (float)var16;
								float var40 = 1.0F;
								var8.func_35835_b((var42.func_35451_b(var21, var31, var22, 0) * 3 + 15728880) / 4);
								var8.setColorRGBA_F(var40, var40, var40, ((1.0F - var39 * var39) * 0.3F + 0.5F) * var2);
								var8.setTranslationD(-var9 * 1.0D, -var11 * 1.0D, -var13 * 1.0D);
								var8.addVertexWithUV((double)((float)var21 - var24) + 0.5D, (double)var28, (double)((float)var22 - var25) + 0.5D, (double)(0.0F * var30 + var47), (double)((float)var28 * var30 / 4.0F + var32 * var30 + var34));
								var8.addVertexWithUV((double)((float)var21 + var24) + 0.5D, (double)var28, (double)((float)var22 + var25) + 0.5D, (double)(1.0F * var30 + var47), (double)((float)var28 * var30 / 4.0F + var32 * var30 + var34));
								var8.addVertexWithUV((double)((float)var21 + var24) + 0.5D, (double)var29, (double)((float)var22 + var25) + 0.5D, (double)(1.0F * var30 + var47), (double)((float)var29 * var30 / 4.0F + var32 * var30 + var34));
								var8.addVertexWithUV((double)((float)var21 - var24) + 0.5D, (double)var29, (double)((float)var22 - var25) + 0.5D, (double)(0.0F * var30 + var47), (double)((float)var29 * var30 / 4.0F + var32 * var30 + var34));
								var8.setTranslationD(0.0D, 0.0D, 0.0D);
							}
						}
					}
				}
			}

			if(var19 >= 0) {
				var8.draw();
			}

			GL11.glEnable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
			this.func_35810_a((double)var1);
		}
	}

	public void func_905_b() {
		ScaledResolution var1 = new ScaledResolution(this.mc.gameSettings, this.mc.displayWidth, this.mc.displayHeight);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glLoadIdentity();
		GL11.glOrtho(0.0D, var1.scaledWidthD, var1.scaledHeightD, 0.0D, 1000.0D, 3000.0D);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glLoadIdentity();
		GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
	}

	private void updateFogColor(float var1) {
		World var2 = this.mc.theWorld;
		EntityLiving var3 = this.mc.renderViewEntity;
		float var4 = 1.0F / (float)(4 - this.mc.gameSettings.renderDistance);
		var4 = 1.0F - (float)Math.pow((double)var4, 0.25D);
		Vec3D var5 = var2.getSkyColor(this.mc.renderViewEntity, var1);
		float var6 = (float)var5.xCoord;
		float var7 = (float)var5.yCoord;
		float var8 = (float)var5.zCoord;
		Vec3D var9 = var2.getFogColor(var1);
		this.fogColorRed = (float)var9.xCoord;
		this.fogColorGreen = (float)var9.yCoord;
		this.fogColorBlue = (float)var9.zCoord;
		float var11;
		if(this.mc.gameSettings.renderDistance < 2) {
			Vec3D var10 = MathHelper.sin(var2.func_35456_d(var1)) > 0.0F ? Vec3D.createVector(0.0D, 0.0D, 1.0D) : Vec3D.createVector(0.0D, 0.0D, -1.0D);
			var11 = (float)var3.getLook(var1).func_35612_b(var10);
			if(var11 < 0.0F) {
				var11 = 0.0F;
			}

			if(var11 > 0.0F) {
				float[] var12 = var2.worldProvider.calcSunriseSunsetColors(var2.getCelestialAngle(var1), var1);
				if(var12 != null) {
					var11 *= var12[3];
					this.fogColorRed = this.fogColorRed * (1.0F - var11) + var12[0] * var11;
					this.fogColorGreen = this.fogColorGreen * (1.0F - var11) + var12[1] * var11;
					this.fogColorBlue = this.fogColorBlue * (1.0F - var11) + var12[2] * var11;
				}
			}
		}

		this.fogColorRed += (var6 - this.fogColorRed) * var4;
		this.fogColorGreen += (var7 - this.fogColorGreen) * var4;
		this.fogColorBlue += (var8 - this.fogColorBlue) * var4;
		float var18 = var2.getRainStrength(var1);
		float var19;
		if(var18 > 0.0F) {
			var11 = 1.0F - var18 * 0.5F;
			var19 = 1.0F - var18 * 0.4F;
			this.fogColorRed *= var11;
			this.fogColorGreen *= var11;
			this.fogColorBlue *= var19;
		}

		var11 = var2.getWeightedThunderStrength(var1);
		if(var11 > 0.0F) {
			var19 = 1.0F - var11 * 0.5F;
			this.fogColorRed *= var19;
			this.fogColorGreen *= var19;
			this.fogColorBlue *= var19;
		}

		if(this.cloudFog) {
			Vec3D var20 = var2.drawClouds(var1);
			this.fogColorRed = (float)var20.xCoord;
			this.fogColorGreen = (float)var20.yCoord;
			this.fogColorBlue = (float)var20.zCoord;
		} else if(var3.isInsideOfMaterial(Material.water)) {
			this.fogColorRed = 0.02F;
			this.fogColorGreen = 0.02F;
			this.fogColorBlue = 0.2F;
		} else if(var3.isInsideOfMaterial(Material.lava)) {
			this.fogColorRed = 0.6F;
			this.fogColorGreen = 0.1F;
			this.fogColorBlue = 0.0F;
		}

		var19 = this.fogColor2 + (this.fogColor1 - this.fogColor2) * var1;
		this.fogColorRed *= var19;
		this.fogColorGreen *= var19;
		this.fogColorBlue *= var19;
		double var13 = (var3.lastTickPosY + (var3.posY - var3.lastTickPosY) * (double)var1) / 32.0D;
		if(var13 < 1.0D) {
			if(var13 < 0.0D) {
				var13 = 0.0D;
			}

			var13 *= var13;
			this.fogColorRed = (float)((double)this.fogColorRed * var13);
			this.fogColorGreen = (float)((double)this.fogColorGreen * var13);
			this.fogColorBlue = (float)((double)this.fogColorBlue * var13);
		}

		if(this.mc.gameSettings.anaglyph) {
			float var15 = (this.fogColorRed * 30.0F + this.fogColorGreen * 59.0F + this.fogColorBlue * 11.0F) / 100.0F;
			float var16 = (this.fogColorRed * 30.0F + this.fogColorGreen * 70.0F) / 100.0F;
			float var17 = (this.fogColorRed * 30.0F + this.fogColorBlue * 70.0F) / 100.0F;
			this.fogColorRed = var15;
			this.fogColorGreen = var16;
			this.fogColorBlue = var17;
		}

		GL11.glClearColor(this.fogColorRed, this.fogColorGreen, this.fogColorBlue, 0.0F);
	}

	private void setupFog(int var1, float var2) {
		EntityLiving var3 = this.mc.renderViewEntity;
		if(var1 == 999) {
			GL11.glFog(GL11.GL_FOG_COLOR, this.func_908_a(0.0F, 0.0F, 0.0F, 1.0F));
			GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
			GL11.glFogf(GL11.GL_FOG_START, 0.0F);
			GL11.glFogf(GL11.GL_FOG_END, 8.0F);
			if(GLContext.getCapabilities().GL_NV_fog_distance) {
				GL11.glFogi(NVFogDistance.GL_FOG_DISTANCE_MODE_NV, NVFogDistance.GL_EYE_RADIAL_NV);
			}

			GL11.glFogf(GL11.GL_FOG_START, 0.0F);
		} else {
			GL11.glFog(GL11.GL_FOG_COLOR, this.func_908_a(this.fogColorRed, this.fogColorGreen, this.fogColorBlue, 1.0F));
			GL11.glNormal3f(0.0F, -1.0F, 0.0F);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			float var4;
			float var5;
			float var6;
			float var7;
			float var8;
			float var9;
			if(this.cloudFog) {
				GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
				GL11.glFogf(GL11.GL_FOG_DENSITY, 0.1F);
				var4 = 1.0F;
				var5 = 1.0F;
				var6 = 1.0F;
				if(this.mc.gameSettings.anaglyph) {
					var7 = (var4 * 30.0F + var5 * 59.0F + var6 * 11.0F) / 100.0F;
					var8 = (var4 * 30.0F + var5 * 70.0F) / 100.0F;
					var9 = (var4 * 30.0F + var6 * 70.0F) / 100.0F;
				}
			} else if(var3.isInsideOfMaterial(Material.water)) {
				GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
				GL11.glFogf(GL11.GL_FOG_DENSITY, 0.1F);
				var4 = 0.4F;
				var5 = 0.4F;
				var6 = 0.9F;
				if(this.mc.gameSettings.anaglyph) {
					var7 = (var4 * 30.0F + var5 * 59.0F + var6 * 11.0F) / 100.0F;
					var8 = (var4 * 30.0F + var5 * 70.0F) / 100.0F;
					var9 = (var4 * 30.0F + var6 * 70.0F) / 100.0F;
				}
			} else if(var3.isInsideOfMaterial(Material.lava)) {
				GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
				GL11.glFogf(GL11.GL_FOG_DENSITY, 2.0F);
				var4 = 0.4F;
				var5 = 0.3F;
				var6 = 0.3F;
				if(this.mc.gameSettings.anaglyph) {
					var7 = (var4 * 30.0F + var5 * 59.0F + var6 * 11.0F) / 100.0F;
					var8 = (var4 * 30.0F + var5 * 70.0F) / 100.0F;
					var9 = (var4 * 30.0F + var6 * 70.0F) / 100.0F;
				}
			} else {
				var4 = this.farPlaneDistance;
				double var10 = (double)((var3.func_35115_a(var2) & 15728640) >> 20) / 16.0D + (var3.lastTickPosY + (var3.posY - var3.lastTickPosY) * (double)var2 + 4.0D) / 32.0D;
				if(var10 < 1.0D) {
					if(var10 < 0.0D) {
						var10 = 0.0D;
					}

					var10 *= var10;
					var7 = 100.0F * (float)var10;
					if(var7 < 5.0F) {
						var7 = 5.0F;
					}

					if(var4 > var7) {
						var4 = var7;
					}
				}

				GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
				GL11.glFogf(GL11.GL_FOG_START, var4 * 0.25F);
				GL11.glFogf(GL11.GL_FOG_END, var4);
				if(var1 < 0) {
					GL11.glFogf(GL11.GL_FOG_START, 0.0F);
					GL11.glFogf(GL11.GL_FOG_END, var4 * 0.8F);
				}

				if(GLContext.getCapabilities().GL_NV_fog_distance) {
					GL11.glFogi(NVFogDistance.GL_FOG_DISTANCE_MODE_NV, NVFogDistance.GL_EYE_RADIAL_NV);
				}

				if(this.mc.theWorld.worldProvider.isNether) {
					GL11.glFogf(GL11.GL_FOG_START, 0.0F);
				}
			}

			GL11.glEnable(GL11.GL_COLOR_MATERIAL);
			GL11.glColorMaterial(GL11.GL_FRONT, GL11.GL_AMBIENT);
		}
	}

	private FloatBuffer func_908_a(float var1, float var2, float var3, float var4) {
		this.fogColorBuffer.clear();
		this.fogColorBuffer.put(var1).put(var2).put(var3).put(var4);
		this.fogColorBuffer.flip();
		return this.fogColorBuffer;
	}
}
