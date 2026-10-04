package net.minecraft.src;

public class EntityCritFX extends EntityFX {
	private boolean field_35136_ay;
	float field_35137_a;

	public EntityCritFX(World var1, double var2, double var4, double var6, double var8, double var10, double var12) {
		this(var1, var2, var4, var6, var8, var10, var12, 1.0F);
	}

	public EntityCritFX(World var1, double var2, double var4, double var6, double var8, double var10, double var12, float var14) {
		super(var1, var2, var4, var6, 0.0D, 0.0D, 0.0D);
		this.field_35136_ay = true;
		this.motionX *= (double)0.1F;
		this.motionY *= (double)0.1F;
		this.motionZ *= (double)0.1F;
		this.motionX += var8 * 0.4D;
		this.motionY += var10 * 0.4D;
		this.motionZ += var12 * 0.4D;
		this.particleRed = this.particleGreen = this.particleBlue = (float)(Math.random() * (double)0.3F + (double)0.6F);
		this.particleScale *= 12.0F / 16.0F;
		this.particleScale *= var14;
		this.field_35137_a = this.particleScale;
		this.particleMaxAge = (int)(6.0D / (Math.random() * 0.8D + 0.6D));
		this.particleMaxAge = (int)((float)this.particleMaxAge * var14);
		this.noClip = false;
		this.particleTextureIndex = 65;
		this.onUpdate();
	}

	public void renderParticle(Tessellator var1, float var2, float var3, float var4, float var5, float var6, float var7) {
		if(this.field_35136_ay) {
			float var8 = ((float)this.particleAge + var2) / (float)this.particleMaxAge * 32.0F;
			if(var8 < 0.0F) {
				var8 = 0.0F;
			}

			if(var8 > 1.0F) {
				var8 = 1.0F;
			}

			this.particleScale = this.field_35137_a * var8;
			super.renderParticle(var1, var2, var3, var4, var5, var6, var7);
		}
	}

	public void onUpdate() {
		this.prevPosX = this.posX;
		this.prevPosY = this.posY;
		this.prevPosZ = this.posZ;
		if(this.particleAge++ >= this.particleMaxAge) {
			this.setEntityDead();
		}

		this.moveEntity(this.motionX, this.motionY, this.motionZ);
		this.particleGreen = (float)((double)this.particleGreen * 0.96D);
		this.particleBlue = (float)((double)this.particleBlue * 0.9D);
		this.motionX *= (double)0.7F;
		this.motionY *= (double)0.7F;
		this.motionZ *= (double)0.7F;
		this.motionY -= (double)0.02F;
		if(this.onGround) {
			this.motionX *= (double)0.7F;
			this.motionZ *= (double)0.7F;
		}

	}
}
