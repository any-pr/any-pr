package net.minecraft.src;

import java.util.Iterator;
import java.util.List;

public class EntityWolf extends EntityAnimal {
	private boolean looksWithInterest = false;
	private float field_25038_b;
	private float field_25044_c;
	private boolean isWet;
	private boolean field_25042_g;
	private float timeWolfIsShaking;
	private float prevTimeWolfIsShaking;

	public EntityWolf(World var1) {
		super(var1);
		this.texture = "/mob/wolf.png";
		this.setSize(0.8F, 0.8F);
		this.moveSpeed = 1.1F;
		this.health = 8;
	}

	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(16, Byte.valueOf((byte)0));
		this.dataWatcher.addObject(17, "");
		this.dataWatcher.addObject(18, new Integer(this.health));
	}

	protected boolean canTriggerWalking() {
		return false;
	}

	public void writeEntityToNBT(NBTTagCompound var1) {
		super.writeEntityToNBT(var1);
		var1.setBoolean("Angry", this.getIsAngry());
		var1.setBoolean("Sitting", this.getIsSitting());
		if(this.getOwner() == null) {
			var1.setString("Owner", "");
		} else {
			var1.setString("Owner", this.getOwner());
		}

	}

	public void readEntityFromNBT(NBTTagCompound var1) {
		super.readEntityFromNBT(var1);
		this.setIsAngry(var1.getBoolean("Angry"));
		this.setIsSitting(var1.getBoolean("Sitting"));
		String var2 = var1.getString("Owner");
		if(var2.length() > 0) {
			this.setOwner(var2);
			this.setIsTamed(true);
		}

	}

	protected boolean canDespawn() {
		return !this.isTamed();
	}

	protected String getLivingSound() {
		return this.getIsAngry() ? "mob.wolf.growl" : (this.rand.nextInt(3) == 0 ? (this.isTamed() && this.dataWatcher.getWatchableObjectInt(18) < 10 ? "mob.wolf.whine" : "mob.wolf.panting") : "mob.wolf.bark");
	}

	protected String getHurtSound() {
		return "mob.wolf.hurt";
	}

	protected String getDeathSound() {
		return "mob.wolf.death";
	}

	protected float getSoundVolume() {
		return 0.4F;
	}

	protected int getDropItemId() {
		return -1;
	}

	protected void updateEntityActionState() {
		super.updateEntityActionState();
		if(!this.hasAttacked && !this.hasPath() && this.isTamed() && this.ridingEntity == null) {
			EntityPlayer var3 = this.worldObj.getPlayerEntityByName(this.getOwner());
			if(var3 != null) {
				float var2 = var3.getDistanceToEntity(this);
				if(var2 > 5.0F) {
					this.setPathEntity(var3, var2);
				}
			} else if(!this.isInWater()) {
				this.setIsSitting(true);
			}
		} else if(this.entityToAttack == null && !this.hasPath() && !this.isTamed() && this.worldObj.rand.nextInt(100) == 0) {
			List var1 = this.worldObj.getEntitiesWithinAABB(EntitySheep.class, AxisAlignedBB.getBoundingBoxFromPool(this.posX, this.posY, this.posZ, this.posX + 1.0D, this.posY + 1.0D, this.posZ + 1.0D).expand(16.0D, 4.0D, 16.0D));
			if(!var1.isEmpty()) {
				this.setTarget((Entity)var1.get(this.worldObj.rand.nextInt(var1.size())));
			}
		}

		if(this.isInWater()) {
			this.setIsSitting(false);
		}

		if(!this.worldObj.singleplayerWorld) {
			this.dataWatcher.updateObject(18, Integer.valueOf(this.health));
		}

	}

	public void onLivingUpdate() {
		super.onLivingUpdate();
		this.looksWithInterest = false;
		if(this.hasCurrentTarget() && !this.hasPath() && !this.getIsAngry()) {
			Entity var1 = this.getCurrentTarget();
			if(var1 instanceof EntityPlayer) {
				EntityPlayer var2 = (EntityPlayer)var1;
				ItemStack var3 = var2.inventory.getCurrentItem();
				if(var3 != null) {
					if(!this.isTamed() && var3.itemID == Item.bone.shiftedIndex) {
						this.looksWithInterest = true;
					} else if(this.isTamed() && Item.itemsList[var3.itemID] instanceof ItemFood) {
						this.looksWithInterest = ((ItemFood)Item.itemsList[var3.itemID]).getIsWolfsFavoriteMeat();
					}
				}
			}
		}

		if(!this.isMultiplayerEntity && this.isWet && !this.field_25042_g && !this.hasPath() && this.onGround) {
			this.field_25042_g = true;
			this.timeWolfIsShaking = 0.0F;
			this.prevTimeWolfIsShaking = 0.0F;
			this.worldObj.sendTrackedEntityStatusUpdatePacket(this, (byte)8);
		}

	}

	public void onUpdate() {
		super.onUpdate();
		this.field_25044_c = this.field_25038_b;
		if(this.looksWithInterest) {
			this.field_25038_b += (1.0F - this.field_25038_b) * 0.4F;
		} else {
			this.field_25038_b += (0.0F - this.field_25038_b) * 0.4F;
		}

		if(this.looksWithInterest) {
			this.numTicksToChaseTarget = 10;
		}

		if(this.isWet()) {
			this.isWet = true;
			this.field_25042_g = false;
			this.timeWolfIsShaking = 0.0F;
			this.prevTimeWolfIsShaking = 0.0F;
		} else if((this.isWet || this.field_25042_g) && this.field_25042_g) {
			if(this.timeWolfIsShaking == 0.0F) {
				this.worldObj.playSoundAtEntity(this, "mob.wolf.shake", this.getSoundVolume(), (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F);
			}

			this.prevTimeWolfIsShaking = this.timeWolfIsShaking;
			this.timeWolfIsShaking += 0.05F;
			if(this.prevTimeWolfIsShaking >= 2.0F) {
				this.isWet = false;
				this.field_25042_g = false;
				this.prevTimeWolfIsShaking = 0.0F;
				this.timeWolfIsShaking = 0.0F;
			}

			if(this.timeWolfIsShaking > 0.4F) {
				float var1 = (float)this.boundingBox.minY;
				int var2 = (int)(MathHelper.sin((this.timeWolfIsShaking - 0.4F) * (float)Math.PI) * 7.0F);

				for(int var3 = 0; var3 < var2; ++var3) {
					float var4 = (this.rand.nextFloat() * 2.0F - 1.0F) * this.width * 0.5F;
					float var5 = (this.rand.nextFloat() * 2.0F - 1.0F) * this.width * 0.5F;
					this.worldObj.spawnParticle("splash", this.posX + (double)var4, (double)(var1 + 0.8F), this.posZ + (double)var5, this.motionX, this.motionY, this.motionZ);
				}
			}
		}

	}

	public float getEyeHeight() {
		return this.height * 0.8F;
	}

	protected int getVerticalFaceSpeed() {
		return this.getIsSitting() ? 20 : super.getVerticalFaceSpeed();
	}

	private void setPathEntity(Entity var1, float var2) {
		PathEntity var3 = this.worldObj.getPathToEntity(this, var1, 16.0F);
		if(var3 == null && var2 > 12.0F) {
			int var4 = MathHelper.floor_double(var1.posX) - 2;
			int var5 = MathHelper.floor_double(var1.posZ) - 2;
			int var6 = MathHelper.floor_double(var1.boundingBox.minY);

			for(int var7 = 0; var7 <= 4; ++var7) {
				for(int var8 = 0; var8 <= 4; ++var8) {
					if((var7 < 1 || var8 < 1 || var7 > 3 || var8 > 3) && this.worldObj.isBlockNormalCube(var4 + var7, var6 - 1, var5 + var8) && !this.worldObj.isBlockNormalCube(var4 + var7, var6, var5 + var8) && !this.worldObj.isBlockNormalCube(var4 + var7, var6 + 1, var5 + var8)) {
						this.setLocationAndAngles((double)((float)(var4 + var7) + 0.5F), (double)var6, (double)((float)(var5 + var8) + 0.5F), this.rotationYaw, this.rotationPitch);
						return;
					}
				}
			}
		} else {
			this.setPathToEntity(var3);
		}

	}

	protected boolean isMovementCeased() {
		return this.getIsSitting() || this.field_25042_g;
	}

	public boolean attackEntityFrom(DamageSource var1, int var2) {
		Entity var3 = var1.func_35080_a();
		this.setIsSitting(false);
		if(var3 != null && !(var3 instanceof EntityPlayer) && !(var3 instanceof EntityArrow)) {
			var2 = (var2 + 1) / 2;
		}

		if(!super.attackEntityFrom(var1, var2)) {
			return false;
		} else {
			if(!this.isTamed() && !this.getIsAngry()) {
				if(var3 instanceof EntityPlayer) {
					this.setIsAngry(true);
					this.entityToAttack = var3;
				}

				if(var3 instanceof EntityArrow && ((EntityArrow)var3).shootingEntity != null) {
					var3 = ((EntityArrow)var3).shootingEntity;
				}

				if(var3 instanceof EntityLiving) {
					List var4 = this.worldObj.getEntitiesWithinAABB(EntityWolf.class, AxisAlignedBB.getBoundingBoxFromPool(this.posX, this.posY, this.posZ, this.posX + 1.0D, this.posY + 1.0D, this.posZ + 1.0D).expand(16.0D, 4.0D, 16.0D));
					Iterator var5 = var4.iterator();

					while(var5.hasNext()) {
						Entity var6 = (Entity)var5.next();
						EntityWolf var7 = (EntityWolf)var6;
						if(!var7.isTamed() && var7.entityToAttack == null) {
							var7.entityToAttack = var3;
							if(var3 instanceof EntityPlayer) {
								var7.setIsAngry(true);
							}
						}
					}
				}
			} else if(var3 != this && var3 != null) {
				if(this.isTamed() && var3 instanceof EntityPlayer && ((EntityPlayer)var3).username.equalsIgnoreCase(this.getOwner())) {
					return true;
				}

				this.entityToAttack = var3;
			}

			return true;
		}
	}

	protected Entity findPlayerToAttack() {
		return this.getIsAngry() ? this.worldObj.getClosestPlayerToEntity(this, 16.0D) : null;
	}

	protected void attackEntity(Entity var1, float var2) {
		if(var2 > 2.0F && var2 < 6.0F && this.rand.nextInt(10) == 0) {
			if(this.onGround) {
				double var8 = var1.posX - this.posX;
				double var5 = var1.posZ - this.posZ;
				float var7 = MathHelper.sqrt_double(var8 * var8 + var5 * var5);
				this.motionX = var8 / (double)var7 * 0.5D * (double)0.8F + this.motionX * (double)0.2F;
				this.motionZ = var5 / (double)var7 * 0.5D * (double)0.8F + this.motionZ * (double)0.2F;
				this.motionY = (double)0.4F;
			}
		} else if((double)var2 < 1.5D && var1.boundingBox.maxY > this.boundingBox.minY && var1.boundingBox.minY < this.boundingBox.maxY) {
			this.attackTime = 20;
			byte var3 = 2;
			if(this.isTamed()) {
				var3 = 4;
			}

			var1.attackEntityFrom(DamageSource.func_35072_a(this), var3);
		}

	}

	public boolean interact(EntityPlayer var1) {
		ItemStack var2 = var1.inventory.getCurrentItem();
		if(!this.isTamed()) {
			if(var2 != null && var2.itemID == Item.bone.shiftedIndex && !this.getIsAngry()) {
				--var2.stackSize;
				if(var2.stackSize <= 0) {
					var1.inventory.setInventorySlotContents(var1.inventory.currentItem, (ItemStack)null);
				}

				if(!this.worldObj.singleplayerWorld) {
					if(this.rand.nextInt(3) == 0) {
						this.setIsTamed(true);
						this.setPathToEntity((PathEntity)null);
						this.setIsSitting(true);
						this.health = 20;
						this.setOwner(var1.username);
						this.isNowTamed(true);
						this.worldObj.sendTrackedEntityStatusUpdatePacket(this, (byte)7);
					} else {
						this.isNowTamed(false);
						this.worldObj.sendTrackedEntityStatusUpdatePacket(this, (byte)6);
					}
				}

				return true;
			}
		} else {
			if(var2 != null && Item.itemsList[var2.itemID] instanceof ItemFood) {
				ItemFood var3 = (ItemFood)Item.itemsList[var2.itemID];
				if(var3.getIsWolfsFavoriteMeat() && this.dataWatcher.getWatchableObjectInt(18) < 20) {
					--var2.stackSize;
					this.heal(var3.getHealAmount());
					if(var2.stackSize <= 0) {
						var1.inventory.setInventorySlotContents(var1.inventory.currentItem, (ItemStack)null);
					}

					return true;
				}
			}

			if(var1.username.equalsIgnoreCase(this.getOwner())) {
				if(!this.worldObj.singleplayerWorld) {
					this.setIsSitting(!this.getIsSitting());
					this.isJumping = false;
					this.setPathToEntity((PathEntity)null);
				}

				return true;
			}
		}

		return false;
	}

	void isNowTamed(boolean var1) {
		String var2 = "heart";
		if(!var1) {
			var2 = "smoke";
		}

		for(int var3 = 0; var3 < 7; ++var3) {
			double var4 = this.rand.nextGaussian() * 0.02D;
			double var6 = this.rand.nextGaussian() * 0.02D;
			double var8 = this.rand.nextGaussian() * 0.02D;
			this.worldObj.spawnParticle(var2, this.posX + (double)(this.rand.nextFloat() * this.width * 2.0F) - (double)this.width, this.posY + 0.5D + (double)(this.rand.nextFloat() * this.height), this.posZ + (double)(this.rand.nextFloat() * this.width * 2.0F) - (double)this.width, var4, var6, var8);
		}

	}

	public int getMaxSpawnedInChunk() {
		return 8;
	}

	public String getOwner() {
		return this.dataWatcher.getWatchableObjectString(17);
	}

	public void setOwner(String var1) {
		this.dataWatcher.updateObject(17, var1);
	}

	public boolean getIsSitting() {
		return (this.dataWatcher.getWatchableObjectByte(16) & 1) != 0;
	}

	public void setIsSitting(boolean var1) {
		byte var2 = this.dataWatcher.getWatchableObjectByte(16);
		if(var1) {
			this.dataWatcher.updateObject(16, Byte.valueOf((byte)(var2 | 1)));
		} else {
			this.dataWatcher.updateObject(16, Byte.valueOf((byte)(var2 & -2)));
		}

	}

	public boolean getIsAngry() {
		return (this.dataWatcher.getWatchableObjectByte(16) & 2) != 0;
	}

	public void setIsAngry(boolean var1) {
		byte var2 = this.dataWatcher.getWatchableObjectByte(16);
		if(var1) {
			this.dataWatcher.updateObject(16, Byte.valueOf((byte)(var2 | 2)));
		} else {
			this.dataWatcher.updateObject(16, Byte.valueOf((byte)(var2 & -3)));
		}

	}

	public boolean isTamed() {
		return (this.dataWatcher.getWatchableObjectByte(16) & 4) != 0;
	}

	public void setIsTamed(boolean var1) {
		byte var2 = this.dataWatcher.getWatchableObjectByte(16);
		if(var1) {
			this.dataWatcher.updateObject(16, Byte.valueOf((byte)(var2 | 4)));
		} else {
			this.dataWatcher.updateObject(16, Byte.valueOf((byte)(var2 & -5)));
		}

	}
}
