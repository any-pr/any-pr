package net.minecraft.src;

public class EntityEnderman extends EntityMob {
	private static boolean[] field_35234_b = new boolean[256];
	public boolean field_35235_a = false;
	private int field_35233_g = 0;
	private int field_35236_h = 0;

	public EntityEnderman(World var1) {
		super(var1);
		this.texture = "/mob/enderman.png";
		this.moveSpeed = 0.2F;
		this.attackStrength = 5;
		this.setSize(0.6F, 2.9F);
		this.stepHeight = 1.0F;
	}

	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(16, new Byte((byte)0));
		this.dataWatcher.addObject(17, new Byte((byte)0));
	}

	public void writeEntityToNBT(NBTTagCompound var1) {
		super.writeEntityToNBT(var1);
		var1.setShort("carried", (short)this.func_35225_x());
		var1.setShort("carriedData", (short)this.func_35231_y());
	}

	public void readEntityFromNBT(NBTTagCompound var1) {
		super.readEntityFromNBT(var1);
		this.func_35226_b(var1.getShort("carried"));
		this.func_35229_d(var1.getShort("carryingData"));
	}

	protected Entity findPlayerToAttack() {
		EntityPlayer var1 = this.worldObj.getClosestPlayerToEntity(this, 64.0D);
		if(var1 != null) {
			if(this.func_35232_c(var1)) {
				if(this.field_35236_h++ == 5) {
					this.field_35236_h = 0;
					return var1;
				}
			} else {
				this.field_35236_h = 0;
			}
		}

		return null;
	}

	public float getEntityBrightness(float var1) {
		return super.getEntityBrightness(var1);
	}

	private boolean func_35232_c(EntityPlayer var1) {
		ItemStack var2 = var1.inventory.armorInventory[3];
		if(var2 != null && var2.itemID == Block.pumpkin.blockID) {
			return false;
		} else {
			Vec3D var3 = var1.getLook(1.0F).normalize();
			Vec3D var4 = Vec3D.createVector(this.posX - var1.posX, this.boundingBox.minY + (double)(this.height / 2.0F) - var1.posY + (double)var1.getEyeHeight(), this.posZ - var1.posZ);
			double var5 = var4.lengthVector();
			var4 = var4.normalize();
			double var7 = var3.func_35570_a(var4);
			return var7 > 1.0D - 0.025D / var5 ? var1.canEntityBeSeen(this) : false;
		}
	}

	public void onLivingUpdate() {
		if(this.isWet()) {
			this.attackEntityFrom(DamageSource.field_35088_e, 1);
		}

		this.field_35235_a = this.entityToAttack != null;
		this.moveSpeed = this.entityToAttack != null ? 4.5F : 0.3F;
		int var1;
		if(!this.worldObj.singleplayerWorld) {
			int var2;
			int var3;
			int var4;
			if(this.func_35225_x() == 0) {
				if(this.rand.nextInt(20) == 0) {
					var1 = MathHelper.floor_double(this.posX - 2.0D + this.rand.nextDouble() * 4.0D);
					var2 = MathHelper.floor_double(this.posY + this.rand.nextDouble() * 3.0D);
					var3 = MathHelper.floor_double(this.posZ - 2.0D + this.rand.nextDouble() * 4.0D);
					var4 = this.worldObj.getBlockId(var1, var2, var3);
					if(field_35234_b[var4]) {
						this.func_35226_b(this.worldObj.getBlockId(var1, var2, var3));
						this.func_35229_d(this.worldObj.getBlockMetadata(var1, var2, var3));
						this.worldObj.setBlockWithNotify(var1, var2, var3, 0);
					}
				}
			} else if(this.rand.nextInt(2000) == 0) {
				var1 = MathHelper.floor_double(this.posX - 1.0D + this.rand.nextDouble() * 2.0D);
				var2 = MathHelper.floor_double(this.posY + this.rand.nextDouble() * 2.0D);
				var3 = MathHelper.floor_double(this.posZ - 1.0D + this.rand.nextDouble() * 2.0D);
				var4 = this.worldObj.getBlockId(var1, var2, var3);
				int var5 = this.worldObj.getBlockId(var1, var2 - 1, var3);
				if(var4 == 0 && var5 > 0 && Block.blocksList[var5].isACube()) {
					this.worldObj.setBlockAndMetadataWithNotify(var1, var2, var3, this.func_35225_x(), this.func_35231_y());
					this.func_35226_b(0);
				}
			}
		}

		for(var1 = 0; var1 < 2; ++var1) {
			this.worldObj.spawnParticle("portal", this.posX + (this.rand.nextDouble() - 0.5D) * (double)this.width, this.posY + this.rand.nextDouble() * (double)this.height - 0.25D, this.posZ + (this.rand.nextDouble() - 0.5D) * (double)this.width, (this.rand.nextDouble() - 0.5D) * 2.0D, -this.rand.nextDouble(), (this.rand.nextDouble() - 0.5D) * 2.0D);
		}

		if(this.worldObj.isDaytime() && !this.worldObj.singleplayerWorld) {
			float var6 = this.getEntityBrightness(1.0F);
			if(var6 > 0.5F && this.worldObj.canBlockSeeTheSky(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.posY), MathHelper.floor_double(this.posZ)) && this.rand.nextFloat() * 30.0F < (var6 - 0.4F) * 2.0F) {
				this.fire = 300;
			}
		}

		this.isJumping = false;
		if(this.entityToAttack != null) {
			this.faceEntity(this.entityToAttack, 100.0F, 100.0F);
		}

		if(!this.worldObj.singleplayerWorld) {
			if(this.entityToAttack != null) {
				if(this.entityToAttack instanceof EntityPlayer && this.func_35232_c((EntityPlayer)this.entityToAttack)) {
					this.moveStrafing = this.moveForward = 0.0F;
					this.moveSpeed = 0.0F;
					if(this.entityToAttack.getDistanceSqToEntity(this) < 16.0D) {
						this.func_35227_w();
					}

					this.field_35233_g = 0;
				} else if(this.entityToAttack.getDistanceSqToEntity(this) > 256.0D && this.field_35233_g++ >= 30 && this.func_35230_e(this.entityToAttack)) {
					this.field_35233_g = 0;
				}
			} else {
				this.field_35233_g = 0;
			}
		}

		super.onLivingUpdate();
	}

	protected boolean func_35227_w() {
		double var1 = this.posX + (this.rand.nextDouble() - 0.5D) * 64.0D;
		double var3 = this.posY + (double)(this.rand.nextInt(64) - 32);
		double var5 = this.posZ + (this.rand.nextDouble() - 0.5D) * 64.0D;
		return this.func_35228_a(var1, var3, var5);
	}

	protected boolean func_35230_e(Entity var1) {
		Vec3D var2 = Vec3D.createVector(this.posX - var1.posX, this.boundingBox.minY + (double)(this.height / 2.0F) - var1.posY + (double)var1.getEyeHeight(), this.posZ - var1.posZ);
		var2 = var2.normalize();
		double var3 = 16.0D;
		double var5 = this.posX + (this.rand.nextDouble() - 0.5D) * 8.0D - var2.xCoord * var3;
		double var7 = this.posY + (double)(this.rand.nextInt(16) - 8) - var2.yCoord * var3;
		double var9 = this.posZ + (this.rand.nextDouble() - 0.5D) * 8.0D - var2.zCoord * var3;
		return this.func_35228_a(var5, var7, var9);
	}

	protected boolean func_35228_a(double var1, double var3, double var5) {
		double var7 = this.posX;
		double var9 = this.posY;
		double var11 = this.posZ;
		this.posX = var1;
		this.posY = var3;
		this.posZ = var5;
		boolean var13 = false;
		int var14 = MathHelper.floor_double(this.posX);
		int var15 = MathHelper.floor_double(this.posY);
		int var16 = MathHelper.floor_double(this.posZ);
		int var18;
		if(this.worldObj.blockExists(var14, var15, var16)) {
			boolean var17 = false;

			while(true) {
				while(!var17 && var15 > 0) {
					var18 = this.worldObj.getBlockId(var14, var15 - 1, var16);
					if(var18 != 0 && Block.blocksList[var18].blockMaterial.getIsSolid()) {
						var17 = true;
					} else {
						--this.posY;
						--var15;
					}
				}

				if(var17) {
					this.setPosition(this.posX, this.posY, this.posZ);
					if(this.worldObj.getCollidingBoundingBoxes(this, this.boundingBox).size() == 0 && !this.worldObj.getIsAnyLiquid(this.boundingBox)) {
						var13 = true;
					}
				}
				break;
			}
		}

		if(!var13) {
			this.setPosition(var7, var9, var11);
			return false;
		} else {
			short var30 = 128;

			for(var18 = 0; var18 < var30; ++var18) {
				double var19 = (double)var18 / ((double)var30 - 1.0D);
				float var21 = (this.rand.nextFloat() - 0.5F) * 0.2F;
				float var22 = (this.rand.nextFloat() - 0.5F) * 0.2F;
				float var23 = (this.rand.nextFloat() - 0.5F) * 0.2F;
				double var24 = var7 + (this.posX - var7) * var19 + (this.rand.nextDouble() - 0.5D) * (double)this.width * 2.0D;
				double var26 = var9 + (this.posY - var9) * var19 + this.rand.nextDouble() * (double)this.height;
				double var28 = var11 + (this.posZ - var11) * var19 + (this.rand.nextDouble() - 0.5D) * (double)this.width * 2.0D;
				this.worldObj.spawnParticle("portal", var24, var26, var28, (double)var21, (double)var22, (double)var23);
			}

			return true;
		}
	}

	protected String getLivingSound() {
		return "mob.zombie";
	}

	protected String getHurtSound() {
		return "mob.zombiehurt";
	}

	protected String getDeathSound() {
		return "mob.zombiedeath";
	}

	protected int getDropItemId() {
		return Item.field_35411_bl.shiftedIndex;
	}

	protected void dropFewItems(boolean var1) {
		int var2 = this.getDropItemId();
		if(var2 > 0) {
			int var3 = this.rand.nextInt(2);

			for(int var4 = 0; var4 < var3; ++var4) {
				this.dropItem(var2, 1);
			}
		}

	}

	public void func_35226_b(int var1) {
		this.dataWatcher.updateObject(16, Byte.valueOf((byte)(var1 & 255)));
	}

	public int func_35225_x() {
		return this.dataWatcher.getWatchableObjectByte(16);
	}

	public void func_35229_d(int var1) {
		this.dataWatcher.updateObject(17, Byte.valueOf((byte)(var1 & 255)));
	}

	public int func_35231_y() {
		return this.dataWatcher.getWatchableObjectByte(17);
	}

	static {
		field_35234_b[Block.stone.blockID] = true;
		field_35234_b[Block.grass.blockID] = true;
		field_35234_b[Block.dirt.blockID] = true;
		field_35234_b[Block.cobblestone.blockID] = true;
		field_35234_b[Block.planks.blockID] = true;
		field_35234_b[Block.sand.blockID] = true;
		field_35234_b[Block.gravel.blockID] = true;
		field_35234_b[Block.oreGold.blockID] = true;
		field_35234_b[Block.oreIron.blockID] = true;
		field_35234_b[Block.oreCoal.blockID] = true;
		field_35234_b[Block.wood.blockID] = true;
		field_35234_b[Block.leaves.blockID] = true;
		field_35234_b[Block.sponge.blockID] = true;
		field_35234_b[Block.glass.blockID] = true;
		field_35234_b[Block.oreLapis.blockID] = true;
		field_35234_b[Block.blockLapis.blockID] = true;
		field_35234_b[Block.sandStone.blockID] = true;
		field_35234_b[Block.cloth.blockID] = true;
		field_35234_b[Block.plantYellow.blockID] = true;
		field_35234_b[Block.plantRed.blockID] = true;
		field_35234_b[Block.mushroomBrown.blockID] = true;
		field_35234_b[Block.mushroomRed.blockID] = true;
		field_35234_b[Block.blockGold.blockID] = true;
		field_35234_b[Block.blockSteel.blockID] = true;
		field_35234_b[Block.brick.blockID] = true;
		field_35234_b[Block.tnt.blockID] = true;
		field_35234_b[Block.bookShelf.blockID] = true;
		field_35234_b[Block.cobblestoneMossy.blockID] = true;
		field_35234_b[Block.oreDiamond.blockID] = true;
		field_35234_b[Block.blockDiamond.blockID] = true;
		field_35234_b[Block.workbench.blockID] = true;
		field_35234_b[Block.oreRedstone.blockID] = true;
		field_35234_b[Block.oreRedstoneGlowing.blockID] = true;
		field_35234_b[Block.ice.blockID] = true;
		field_35234_b[Block.cactus.blockID] = true;
		field_35234_b[Block.blockClay.blockID] = true;
		field_35234_b[Block.pumpkin.blockID] = true;
		field_35234_b[Block.netherrack.blockID] = true;
		field_35234_b[Block.slowSand.blockID] = true;
		field_35234_b[Block.glowStone.blockID] = true;
		field_35234_b[Block.pumpkinLantern.blockID] = true;
		field_35234_b[Block.field_35052_bn.blockID] = true;
		field_35234_b[Block.field_35053_bo.blockID] = true;
		field_35234_b[Block.field_35054_bp.blockID] = true;
		field_35234_b[Block.field_35048_bs.blockID] = true;
	}
}
