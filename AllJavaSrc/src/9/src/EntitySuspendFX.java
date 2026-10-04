package net.minecraft.src;

public class EntitySuspendFX extends EntityFX {
	public EntitySuspendFX(World var1, double var2, double var4, double var6, double var8, double var10, double var12) {
		super(var1, var2, var4 - 0.125D, var6, var8, var10, var12);
		this.particleRed = 0.4F;
		this.particleGreen = 0.4F;
		this.particleBlue = 0.7F;
		this.particleTextureIndex = 0;
		this.setSize(0.01F, 0.01F);
		this.particleScale *= this.rand.nextFloat() * 0.6F + 0.2F;
		this.motionX = var8 * 0.0D;
		this.motionY = var10 * 0.0D;
		this.motionZ = var12 * 0.0D;
		this.particleMaxAge = (int)(16.0D / (Math.random() * 0.8D + 0.2D));
	}

	public void onUpdate() {
		this.prevPosX = this.posX;
		this.prevPosY = this.posY;
		this.prevPosZ = this.posZ;
		this.moveEntity(this.motionX, this.motionY, this.motionZ);
		if(this.worldObj.getBlockMaterial(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.posY), MathHelper.floor_double(this.posZ)) != Material.water) {
			this.setEntityDead();
		}

		if(this.particleMaxAge-- <= 0) {
			this.setEntityDead();
		}

	}
}
