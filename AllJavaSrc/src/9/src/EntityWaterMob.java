package net.minecraft.src;

public abstract class EntityWaterMob extends EntityCreature {
	public EntityWaterMob(World var1) {
		super(var1);
	}

	public boolean canBreatheUnderwater() {
		return true;
	}

	public void writeEntityToNBT(NBTTagCompound var1) {
		super.writeEntityToNBT(var1);
	}

	public void readEntityFromNBT(NBTTagCompound var1) {
		super.readEntityFromNBT(var1);
	}

	public boolean getCanSpawnHere() {
		return this.worldObj.checkIfAABBIsClear(this.boundingBox);
	}

	public int getTalkInterval() {
		return 120;
	}

	protected int a(EntityPlayer var1) {
		return 1 + this.worldObj.rand.nextInt(3);
	}
}
