package net.minecraft.src;

import java.util.Iterator;
import java.util.List;

public abstract class EntityPlayer extends EntityLiving {
	public InventoryPlayer inventory = new InventoryPlayer(this);
	public Container personalCraftingInventory;
	public Container currentCraftingInventory;
	protected FoodStats field_35217_m = new FoodStats();
	protected int field_35216_n = 0;
	public byte field_9152_am = 0;
	public int score = 0;
	public float prevCameraYaw;
	public float cameraYaw;
	public boolean isSwinging = false;
	public int swingProgressInt = 0;
	public String username;
	public int dimension;
	public int field_35218_w = 0;
	public double field_20047_ay;
	public double field_20046_az;
	public double field_20051_aA;
	public double field_20050_aB;
	public double field_20049_aC;
	public double field_20048_aD;
	protected boolean sleeping;
	public ChunkCoordinates playerLocation;
	private int sleepTimer;
	public float field_22066_z;
	public float field_22067_A;
	private ChunkCoordinates spawnChunk;
	private ChunkCoordinates startMinecartRidingCoordinate;
	public int timeUntilPortal = 20;
	protected boolean inPortal = false;
	public float timeInPortal;
	public PlayerCapabilities field_35214_K = new PlayerCapabilities();
	public int field_35210_L;
	public int field_35211_M;
	public int field_35212_N;
	private ItemStack field_34908_d;
	private int field_34909_e;
	protected float field_35213_O = 0.1F;
	protected float field_35215_P = 0.02F;
	private int damageRemainder = 0;
	public EntityFish fishEntity = null;

	public EntityPlayer(World var1) {
		super(var1);
		this.personalCraftingInventory = new ContainerPlayer(this.inventory, !var1.singleplayerWorld);
		this.currentCraftingInventory = this.personalCraftingInventory;
		this.yOffset = 1.62F;
		ChunkCoordinates var2 = var1.getSpawnPoint();
		this.setLocationAndAngles((double)var2.posX + 0.5D, (double)(var2.posY + 1), (double)var2.posZ + 0.5D, 0.0F, 0.0F);
		this.health = 20;
		this.entityType = "humanoid";
		this.field_9117_aI = 180.0F;
		this.fireResistance = 20;
		this.texture = "/mob/char.png";
	}

	protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(16, Byte.valueOf((byte)0));
		this.dataWatcher.addObject(17, Byte.valueOf((byte)0));
	}

	public boolean func_35209_o_() {
		return this.field_34908_d != null;
	}

	public void func_35196_E() {
		if(this.field_34908_d != null) {
			this.field_34908_d.func_35613_a(this.worldObj, this, this.field_34909_e);
		}

		this.func_35205_F();
	}

	public void func_35205_F() {
		this.field_34908_d = null;
		this.field_34909_e = 0;
		if(!this.worldObj.singleplayerWorld) {
			this.func_35148_h(false);
		}

	}

	public boolean func_35180_G() {
		return this.func_35209_o_() && Item.itemsList[this.field_34908_d.itemID].func_35406_b(this.field_34908_d) == EnumAction.block;
	}

	public void onUpdate() {
		if(this.field_34908_d != null) {
			ItemStack var1 = this.inventory.getCurrentItem();
			if(var1 != this.field_34908_d) {
				this.func_35205_F();
			} else {
				if(this.field_34909_e <= 25 && this.field_34909_e % 4 == 0) {
					this.func_35208_b(var1, 5);
				}

				if(--this.field_34909_e == 0 && !this.worldObj.singleplayerWorld) {
					this.func_35199_C();
				}
			}
		}

		if(this.field_35218_w > 0) {
			--this.field_35218_w;
		}

		if(this.isPlayerSleeping()) {
			++this.sleepTimer;
			if(this.sleepTimer > 100) {
				this.sleepTimer = 100;
			}

			if(!this.worldObj.singleplayerWorld) {
				if(!this.isInBed()) {
					this.wakeUpPlayer(true, true, false);
				} else if(this.worldObj.isDaytime()) {
					this.wakeUpPlayer(false, true, true);
				}
			}
		} else if(this.sleepTimer > 0) {
			++this.sleepTimer;
			if(this.sleepTimer >= 110) {
				this.sleepTimer = 0;
			}
		}

		super.onUpdate();
		if(!this.worldObj.singleplayerWorld && this.currentCraftingInventory != null && !this.currentCraftingInventory.canInteractWith(this)) {
			this.usePersonalCraftingInventory();
			this.currentCraftingInventory = this.personalCraftingInventory;
		}

		if(this.field_35214_K.field_35658_b) {
			for(int var9 = 0; var9 < 8; ++var9) {
			}
		}

		if(this.fire > 0 && this.field_35214_K.field_35660_a) {
			this.fire = 0;
		}

		this.field_20047_ay = this.field_20050_aB;
		this.field_20046_az = this.field_20049_aC;
		this.field_20051_aA = this.field_20048_aD;
		double var10 = this.posX - this.field_20050_aB;
		double var3 = this.posY - this.field_20049_aC;
		double var5 = this.posZ - this.field_20048_aD;
		double var7 = 10.0D;
		if(var10 > var7) {
			this.field_20047_ay = this.field_20050_aB = this.posX;
		}

		if(var5 > var7) {
			this.field_20051_aA = this.field_20048_aD = this.posZ;
		}

		if(var3 > var7) {
			this.field_20046_az = this.field_20049_aC = this.posY;
		}

		if(var10 < -var7) {
			this.field_20047_ay = this.field_20050_aB = this.posX;
		}

		if(var5 < -var7) {
			this.field_20051_aA = this.field_20048_aD = this.posZ;
		}

		if(var3 < -var7) {
			this.field_20046_az = this.field_20049_aC = this.posY;
		}

		this.field_20050_aB += var10 * 0.25D;
		this.field_20048_aD += var5 * 0.25D;
		this.field_20049_aC += var3 * 0.25D;
		this.addStat(StatList.minutesPlayedStat, 1);
		if(this.ridingEntity == null) {
			this.startMinecartRidingCoordinate = null;
		}

		if(!this.worldObj.singleplayerWorld) {
			this.field_35217_m.func_35584_a(this);
		}

	}

	protected void func_35208_b(ItemStack var1, int var2) {
		if(var1.func_35615_m() == EnumAction.eat) {
			for(int var3 = 0; var3 < var2; ++var3) {
				Vec3D var4 = Vec3D.createVector(((double)this.rand.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D);
				var4.func_35571_a(-this.rotationPitch * (float)Math.PI / 180.0F);
				var4.func_35572_b(-this.rotationYaw * (float)Math.PI / 180.0F);
				Vec3D var5 = Vec3D.createVector(((double)this.rand.nextFloat() - 0.5D) * 0.3D, (double)(-this.rand.nextFloat()) * 0.6D - 0.3D, 0.6D);
				var5.func_35571_a(-this.rotationPitch * (float)Math.PI / 180.0F);
				var5.func_35572_b(-this.rotationYaw * (float)Math.PI / 180.0F);
				var5 = var5.addVector(this.posX, this.posY + (double)this.getEyeHeight(), this.posZ);
				this.worldObj.spawnParticle("iconcrack_" + var1.getItem().shiftedIndex, var5.xCoord, var5.yCoord, var5.zCoord, var4.xCoord, var4.yCoord + 0.05D, var4.zCoord);
			}

			this.worldObj.playSoundAtEntity(this, "mob.eat", 0.5F + 0.5F * (float)this.rand.nextInt(2), (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F);
		}

	}

	protected void func_35199_C() {
		if(this.field_34908_d != null) {
			this.func_35208_b(this.field_34908_d, 16);
			int var1 = this.field_34908_d.stackSize;
			ItemStack var2 = this.field_34908_d.func_35617_b(this.worldObj, this);
			if(var2 != this.field_34908_d || var2 != null && var2.stackSize != var1) {
				this.inventory.mainInventory[this.inventory.currentItem] = var2;
				if(var2.stackSize == 0) {
					this.inventory.mainInventory[this.inventory.currentItem] = null;
				}
			}

			this.func_35205_F();
		}

	}

	protected boolean isMovementBlocked() {
		return this.health <= 0 || this.isPlayerSleeping();
	}

	protected void usePersonalCraftingInventory() {
		this.currentCraftingInventory = this.personalCraftingInventory;
	}

	public void updateRidden() {
		double var1 = this.posX;
		double var3 = this.posY;
		double var5 = this.posZ;
		super.updateRidden();
		this.prevCameraYaw = this.cameraYaw;
		this.cameraYaw = 0.0F;
		this.addMountedMovementStat(this.posX - var1, this.posY - var3, this.posZ - var5);
	}

	private int func_35204_o() {
		return this.func_35184_a(Potion.field_35452_e) ? 6 - (1 + this.func_35187_b(Potion.field_35452_e).func_35652_c()) * 1 : (this.func_35184_a(Potion.field_35449_f) ? 6 + (1 + this.func_35187_b(Potion.field_35449_f).func_35652_c()) * 2 : 6);
	}

	protected void updateEntityActionState() {
		int var1 = this.func_35204_o();
		if(this.isSwinging) {
			++this.swingProgressInt;
			if(this.swingProgressInt >= var1) {
				this.swingProgressInt = 0;
				this.isSwinging = false;
			}
		} else {
			this.swingProgressInt = 0;
		}

		this.swingProgress = (float)this.swingProgressInt / (float)var1;
	}

	public void onLivingUpdate() {
		if(this.field_35216_n > 0) {
			--this.field_35216_n;
		}

		if(this.worldObj.difficultySetting == 0 && this.health < 20 && this.ticksExisted % 20 * 12 == 0) {
			this.heal(1);
		}

		this.inventory.decrementAnimations();
		this.prevCameraYaw = this.cameraYaw;
		super.onLivingUpdate();
		this.field_35194_aj = this.field_35213_O;
		this.field_35193_ak = this.field_35215_P;
		if(this.func_35149_at()) {
			this.field_35194_aj = (float)((double)this.field_35194_aj + (double)this.field_35213_O * 0.3D);
			this.field_35193_ak = (float)((double)this.field_35193_ak + (double)this.field_35215_P * 0.3D);
		}

		float var1 = MathHelper.sqrt_double(this.motionX * this.motionX + this.motionZ * this.motionZ);
		float var2 = (float)Math.atan(-this.motionY * (double)0.2F) * 15.0F;
		if(var1 > 0.1F) {
			var1 = 0.1F;
		}

		if(!this.onGround || this.health <= 0) {
			var1 = 0.0F;
		}

		if(this.onGround || this.health <= 0) {
			var2 = 0.0F;
		}

		this.cameraYaw += (var1 - this.cameraYaw) * 0.4F;
		this.cameraPitch += (var2 - this.cameraPitch) * 0.8F;
		if(this.health > 0) {
			List var3 = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, this.boundingBox.expand(1.0D, 0.0D, 1.0D));
			if(var3 != null) {
				for(int var4 = 0; var4 < var3.size(); ++var4) {
					Entity var5 = (Entity)var3.get(var4);
					if(!var5.isDead) {
						this.collideWithPlayer(var5);
					}
				}
			}
		}

	}

	private void collideWithPlayer(Entity var1) {
		var1.onCollideWithPlayer(this);
	}

	public void onDeath(DamageSource var1) {
		super.onDeath(var1);
		this.setSize(0.2F, 0.2F);
		this.setPosition(this.posX, this.posY, this.posZ);
		this.motionY = (double)0.1F;
		if(this.username.equals("Notch")) {
			this.dropPlayerItemWithRandomChoice(new ItemStack(Item.appleRed, 1), true);
		}

		this.inventory.dropAllItems();
		if(var1 != null) {
			this.motionX = (double)(-MathHelper.cos((this.attackedAtYaw + this.rotationYaw) * (float)Math.PI / 180.0F) * 0.1F);
			this.motionZ = (double)(-MathHelper.sin((this.attackedAtYaw + this.rotationYaw) * (float)Math.PI / 180.0F) * 0.1F);
		} else {
			this.motionX = this.motionZ = 0.0D;
		}

		this.yOffset = 0.1F;
		this.addStat(StatList.deathsStat, 1);
	}

	public void addToPlayerScore(Entity var1, int var2) {
		this.score += var2;
		if(var1 instanceof EntityPlayer) {
			this.addStat(StatList.playerKillsStat, 1);
		} else {
			this.addStat(StatList.mobKillsStat, 1);
		}

	}

	public void dropCurrentItem() {
		this.dropPlayerItemWithRandomChoice(this.inventory.decrStackSize(this.inventory.currentItem, 1), false);
	}

	public void dropPlayerItem(ItemStack var1) {
		this.dropPlayerItemWithRandomChoice(var1, false);
	}

	public void dropPlayerItemWithRandomChoice(ItemStack var1, boolean var2) {
		if(var1 != null) {
			EntityItem var3 = new EntityItem(this.worldObj, this.posX, this.posY - (double)0.3F + (double)this.getEyeHeight(), this.posZ, var1);
			var3.delayBeforeCanPickup = 40;
			float var4 = 0.1F;
			float var5;
			if(var2) {
				var5 = this.rand.nextFloat() * 0.5F;
				float var6 = this.rand.nextFloat() * (float)Math.PI * 2.0F;
				var3.motionX = (double)(-MathHelper.sin(var6) * var5);
				var3.motionZ = (double)(MathHelper.cos(var6) * var5);
				var3.motionY = (double)0.2F;
			} else {
				var4 = 0.3F;
				var3.motionX = (double)(-MathHelper.sin(this.rotationYaw / 180.0F * (float)Math.PI) * MathHelper.cos(this.rotationPitch / 180.0F * (float)Math.PI) * var4);
				var3.motionZ = (double)(MathHelper.cos(this.rotationYaw / 180.0F * (float)Math.PI) * MathHelper.cos(this.rotationPitch / 180.0F * (float)Math.PI) * var4);
				var3.motionY = (double)(-MathHelper.sin(this.rotationPitch / 180.0F * (float)Math.PI) * var4 + 0.1F);
				var4 = 0.02F;
				var5 = this.rand.nextFloat() * (float)Math.PI * 2.0F;
				var4 *= this.rand.nextFloat();
				var3.motionX += Math.cos((double)var5) * (double)var4;
				var3.motionY += (double)((this.rand.nextFloat() - this.rand.nextFloat()) * 0.1F);
				var3.motionZ += Math.sin((double)var5) * (double)var4;
			}

			this.joinEntityItemWithWorld(var3);
			this.addStat(StatList.dropStat, 1);
		}
	}

	protected void joinEntityItemWithWorld(EntityItem var1) {
		this.worldObj.entityJoinedWorld(var1);
	}

	public float getCurrentPlayerStrVsBlock(Block var1) {
		float var2 = this.inventory.getStrVsBlock(var1);
		if(this.isInsideOfMaterial(Material.water)) {
			var2 /= 5.0F;
		}

		if(!this.onGround) {
			var2 /= 5.0F;
		}

		if(this.func_35184_a(Potion.field_35452_e)) {
			var2 *= 1.0F + (float)(this.func_35187_b(Potion.field_35452_e).func_35652_c() + 1) * 0.2F;
		}

		if(this.func_35184_a(Potion.field_35449_f)) {
			var2 *= 1.0F - (float)(this.func_35187_b(Potion.field_35449_f).func_35652_c() + 1) * 0.2F;
		}

		return var2;
	}

	public boolean canHarvestBlock(Block var1) {
		return this.inventory.canHarvestBlock(var1);
	}

	public void readEntityFromNBT(NBTTagCompound var1) {
		super.readEntityFromNBT(var1);
		NBTTagList var2 = var1.getTagList("Inventory");
		this.inventory.readFromNBT(var2);
		this.dimension = var1.getInteger("Dimension");
		this.sleeping = var1.getBoolean("Sleeping");
		this.sleepTimer = var1.getShort("SleepTimer");
		this.field_35210_L = var1.getInteger("Xp");
		this.field_35211_M = var1.getInteger("XpLevel");
		this.field_35212_N = var1.getInteger("XpTotal");
		if(this.sleeping) {
			this.playerLocation = new ChunkCoordinates(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.posY), MathHelper.floor_double(this.posZ));
			this.wakeUpPlayer(true, true, false);
		}

		if(var1.hasKey("SpawnX") && var1.hasKey("SpawnY") && var1.hasKey("SpawnZ")) {
			this.spawnChunk = new ChunkCoordinates(var1.getInteger("SpawnX"), var1.getInteger("SpawnY"), var1.getInteger("SpawnZ"));
		}

		this.field_35217_m.func_35589_a(var1);
	}

	public void writeEntityToNBT(NBTTagCompound var1) {
		super.writeEntityToNBT(var1);
		var1.setTag("Inventory", this.inventory.writeToNBT(new NBTTagList()));
		var1.setInteger("Dimension", this.dimension);
		var1.setBoolean("Sleeping", this.sleeping);
		var1.setShort("SleepTimer", (short)this.sleepTimer);
		var1.setInteger("Xp", this.field_35210_L);
		var1.setInteger("XpLevel", this.field_35211_M);
		var1.setInteger("XpTotal", this.field_35212_N);
		if(this.spawnChunk != null) {
			var1.setInteger("SpawnX", this.spawnChunk.posX);
			var1.setInteger("SpawnY", this.spawnChunk.posY);
			var1.setInteger("SpawnZ", this.spawnChunk.posZ);
		}

		this.field_35217_m.func_35588_b(var1);
	}

	public void displayGUIChest(IInventory var1) {
	}

	public void displayWorkbenchGUI(int var1, int var2, int var3) {
	}

	public void onItemPickup(Entity var1, int var2) {
	}

	public float getEyeHeight() {
		return 0.12F;
	}

	protected void resetHeight() {
		this.yOffset = 1.62F;
	}

	public boolean attackEntityFrom(DamageSource var1, int var2) {
		if(this.field_35214_K.field_35660_a && !var1.func_35077_d()) {
			return false;
		} else {
			this.entityAge = 0;
			if(this.health <= 0) {
				return false;
			} else {
				if(this.isPlayerSleeping() && !this.worldObj.singleplayerWorld) {
					this.wakeUpPlayer(true, true, false);
				}

				Entity var3 = var1.func_35080_a();
				if(var3 instanceof EntityMob || var3 instanceof EntityArrow) {
					if(this.worldObj.difficultySetting == 0) {
						var2 = 0;
					}

					if(this.worldObj.difficultySetting == 1) {
						var2 = var2 / 3 + 1;
					}

					if(this.worldObj.difficultySetting == 3) {
						var2 = var2 * 3 / 2;
					}
				}

				if(var2 == 0) {
					return false;
				} else {
					Entity var4 = var3;
					if(var3 instanceof EntityArrow && ((EntityArrow)var3).shootingEntity != null) {
						var4 = ((EntityArrow)var3).shootingEntity;
					}

					if(var4 instanceof EntityLiving) {
						this.alertWolves((EntityLiving)var4, false);
					}

					this.addStat(StatList.damageTakenStat, var2);
					return super.attackEntityFrom(var1, var2);
				}
			}
		}
	}

	protected boolean isPVPEnabled() {
		return false;
	}

	protected void alertWolves(EntityLiving var1, boolean var2) {
		if(!(var1 instanceof EntityCreeper) && !(var1 instanceof EntityGhast)) {
			if(var1 instanceof EntityWolf) {
				EntityWolf var3 = (EntityWolf)var1;
				if(var3.isTamed() && this.username.equals(var3.getOwner())) {
					return;
				}
			}

			if(!(var1 instanceof EntityPlayer) || this.isPVPEnabled()) {
				List var7 = this.worldObj.getEntitiesWithinAABB(EntityWolf.class, AxisAlignedBB.getBoundingBoxFromPool(this.posX, this.posY, this.posZ, this.posX + 1.0D, this.posY + 1.0D, this.posZ + 1.0D).expand(16.0D, 4.0D, 16.0D));
				Iterator var4 = var7.iterator();

				while(true) {
					EntityWolf var6;
					do {
						do {
							do {
								do {
									if(!var4.hasNext()) {
										return;
									}

									Entity var5 = (Entity)var4.next();
									var6 = (EntityWolf)var5;
								} while(!var6.isTamed());
							} while(var6.getEntityToAttack() != null);
						} while(!this.username.equals(var6.getOwner()));
					} while(var2 && var6.getIsSitting());

					var6.setIsSitting(false);
					var6.setTarget(var1);
				}
			}
		}
	}

	protected void damageEntity(DamageSource var1, int var2) {
		if(!var1.func_35083_b() && this.func_35180_G()) {
			var2 = 1 + var2 >> 1;
		}

		if(!var1.func_35083_b()) {
			int var3 = 25 - this.inventory.getTotalArmorValue();
			int var4 = var2 * var3 + this.damageRemainder;
			this.inventory.damageArmor(var2);
			var2 = var4 / 25;
			this.damageRemainder = var4 % 25;
		}

		this.func_35198_b(var1.func_35074_c());
		super.damageEntity(var1, var2);
	}

	public void displayGUIFurnace(TileEntityFurnace var1) {
	}

	public void displayGUIDispenser(TileEntityDispenser var1) {
	}

	public void displayGUIEditSign(TileEntitySign var1) {
	}

	public void useCurrentItemOnEntity(Entity var1) {
		if(!var1.interact(this)) {
			ItemStack var2 = this.getCurrentEquippedItem();
			if(var2 != null && var1 instanceof EntityLiving) {
				var2.useItemOnEntity((EntityLiving)var1);
				if(var2.stackSize <= 0) {
					var2.onItemDestroyedByUse(this);
					this.destroyCurrentEquippedItem();
				}
			}

		}
	}

	public ItemStack getCurrentEquippedItem() {
		return this.inventory.getCurrentItem();
	}

	public void destroyCurrentEquippedItem() {
		this.inventory.setInventorySlotContents(this.inventory.currentItem, (ItemStack)null);
	}

	public double getYOffset() {
		return (double)(this.yOffset - 0.5F);
	}

	public void swingItem() {
		if(!this.isSwinging || this.swingProgressInt >= this.func_35204_o() / 2 || this.swingProgressInt < 0) {
			this.swingProgressInt = -1;
			this.isSwinging = true;
		}

	}

	public void attackTargetEntityWithCurrentItem(Entity var1) {
		int var2 = this.inventory.getDamageVsEntity(var1);
		if(var2 > 0) {
			boolean var3 = this.motionY < 0.0D && !this.onGround && !this.isOnLadder() && !this.isInWater();
			if(var3) {
				var2 = var2 * 3 / 2 + 1;
			}

			boolean var4 = var1.attackEntityFrom(DamageSource.func_35076_b(this), var2);
			if(var4) {
				if(this.func_35149_at()) {
					var1.addVelocity((double)(-MathHelper.sin(this.rotationYaw * (float)Math.PI / 180.0F) * 1.0F), 0.1D, (double)(MathHelper.cos(this.rotationYaw * (float)Math.PI / 180.0F) * 1.0F));
					this.motionX *= 0.6D;
					this.motionZ *= 0.6D;
					this.func_35146_g(false);
				}

				if(var3) {
					this.func_35202_e(var1);
				}
			}

			ItemStack var5 = this.getCurrentEquippedItem();
			if(var5 != null && var1 instanceof EntityLiving) {
				var5.hitEntity((EntityLiving)var1, this);
				if(var5.stackSize <= 0) {
					var5.onItemDestroyedByUse(this);
					this.destroyCurrentEquippedItem();
				}
			}

			if(var1 instanceof EntityLiving) {
				if(var1.isEntityAlive()) {
					this.alertWolves((EntityLiving)var1, true);
				}

				this.addStat(StatList.damageDealtStat, var2);
			}

			this.func_35198_b(0.3F);
		}

	}

	public void func_35202_e(Entity var1) {
	}

	public void onItemStackChanged(ItemStack var1) {
	}

	public void setEntityDead() {
		super.setEntityDead();
		this.personalCraftingInventory.onCraftGuiClosed(this);
		if(this.currentCraftingInventory != null) {
			this.currentCraftingInventory.onCraftGuiClosed(this);
		}

	}

	public boolean isEntityInsideOpaqueBlock() {
		return !this.sleeping && super.isEntityInsideOpaqueBlock();
	}

	public EnumStatus sleepInBedAt(int var1, int var2, int var3) {
		if(!this.worldObj.singleplayerWorld) {
			if(this.isPlayerSleeping() || !this.isEntityAlive()) {
				return EnumStatus.OTHER_PROBLEM;
			}

			if(this.worldObj.worldProvider.canSleepInWorld) {
				return EnumStatus.NOT_POSSIBLE_HERE;
			}

			if(this.worldObj.isDaytime()) {
				return EnumStatus.NOT_POSSIBLE_NOW;
			}

			if(Math.abs(this.posX - (double)var1) > 3.0D || Math.abs(this.posY - (double)var2) > 2.0D || Math.abs(this.posZ - (double)var3) > 3.0D) {
				return EnumStatus.TOO_FAR_AWAY;
			}
		}

		this.setSize(0.2F, 0.2F);
		this.yOffset = 0.2F;
		if(this.worldObj.blockExists(var1, var2, var3)) {
			int var4 = this.worldObj.getBlockMetadata(var1, var2, var3);
			int var5 = BlockBed.getDirectionFromMetadata(var4);
			float var6 = 0.5F;
			float var7 = 0.5F;
			switch(var5) {
			case 0:
				var7 = 0.9F;
				break;
			case 1:
				var6 = 0.1F;
				break;
			case 2:
				var7 = 0.1F;
				break;
			case 3:
				var6 = 0.9F;
			}

			this.func_22059_e(var5);
			this.setPosition((double)((float)var1 + var6), (double)((float)var2 + 15.0F / 16.0F), (double)((float)var3 + var7));
		} else {
			this.setPosition((double)((float)var1 + 0.5F), (double)((float)var2 + 15.0F / 16.0F), (double)((float)var3 + 0.5F));
		}

		this.sleeping = true;
		this.sleepTimer = 0;
		this.playerLocation = new ChunkCoordinates(var1, var2, var3);
		this.motionX = this.motionZ = this.motionY = 0.0D;
		if(!this.worldObj.singleplayerWorld) {
			this.worldObj.updateAllPlayersSleepingFlag();
		}

		return EnumStatus.OK;
	}

	private void func_22059_e(int var1) {
		this.field_22066_z = 0.0F;
		this.field_22067_A = 0.0F;
		switch(var1) {
		case 0:
			this.field_22067_A = -1.8F;
			break;
		case 1:
			this.field_22066_z = 1.8F;
			break;
		case 2:
			this.field_22067_A = 1.8F;
			break;
		case 3:
			this.field_22066_z = -1.8F;
		}

	}

	public void wakeUpPlayer(boolean var1, boolean var2, boolean var3) {
		this.setSize(0.6F, 1.8F);
		this.resetHeight();
		ChunkCoordinates var4 = this.playerLocation;
		ChunkCoordinates var5 = this.playerLocation;
		if(var4 != null && this.worldObj.getBlockId(var4.posX, var4.posY, var4.posZ) == Block.bed.blockID) {
			BlockBed.setBedOccupied(this.worldObj, var4.posX, var4.posY, var4.posZ, false);
			var5 = BlockBed.getNearestEmptyChunkCoordinates(this.worldObj, var4.posX, var4.posY, var4.posZ, 0);
			if(var5 == null) {
				var5 = new ChunkCoordinates(var4.posX, var4.posY + 1, var4.posZ);
			}

			this.setPosition((double)((float)var5.posX + 0.5F), (double)((float)var5.posY + this.yOffset + 0.1F), (double)((float)var5.posZ + 0.5F));
		}

		this.sleeping = false;
		if(!this.worldObj.singleplayerWorld && var2) {
			this.worldObj.updateAllPlayersSleepingFlag();
		}

		if(var1) {
			this.sleepTimer = 0;
		} else {
			this.sleepTimer = 100;
		}

		if(var3) {
			this.setSpawnChunk(this.playerLocation);
		}

	}

	private boolean isInBed() {
		return this.worldObj.getBlockId(this.playerLocation.posX, this.playerLocation.posY, this.playerLocation.posZ) == Block.bed.blockID;
	}

	public static ChunkCoordinates verifyBedCoordinates(World var0, ChunkCoordinates var1) {
		IChunkProvider var2 = var0.getChunkProvider();
		var2.loadChunk(var1.posX - 3 >> 4, var1.posZ - 3 >> 4);
		var2.loadChunk(var1.posX + 3 >> 4, var1.posZ - 3 >> 4);
		var2.loadChunk(var1.posX - 3 >> 4, var1.posZ + 3 >> 4);
		var2.loadChunk(var1.posX + 3 >> 4, var1.posZ + 3 >> 4);
		if(var0.getBlockId(var1.posX, var1.posY, var1.posZ) != Block.bed.blockID) {
			return null;
		} else {
			ChunkCoordinates var3 = BlockBed.getNearestEmptyChunkCoordinates(var0, var1.posX, var1.posY, var1.posZ, 0);
			return var3;
		}
	}

	public boolean isPlayerSleeping() {
		return this.sleeping;
	}

	public boolean isPlayerFullyAsleep() {
		return this.sleeping && this.sleepTimer >= 100;
	}

	public void addChatMessage(String var1) {
	}

	public ChunkCoordinates getSpawnChunk() {
		return this.spawnChunk;
	}

	public void setSpawnChunk(ChunkCoordinates var1) {
		if(var1 != null) {
			this.spawnChunk = new ChunkCoordinates(var1);
		} else {
			this.spawnChunk = null;
		}

	}

	public void triggerAchievement(StatBase var1) {
		this.addStat(var1, 1);
	}

	public void addStat(StatBase var1, int var2) {
	}

	protected void jump() {
		super.jump();
		this.addStat(StatList.jumpStat, 1);
		if(this.func_35149_at()) {
			this.func_35198_b(0.8F);
		} else {
			this.func_35198_b(0.2F);
		}

	}

	public void moveEntityWithHeading(float var1, float var2) {
		double var3 = this.posX;
		double var5 = this.posY;
		double var7 = this.posZ;
		if(this.field_35214_K.field_35658_b) {
			double var9 = this.motionY;
			float var11 = this.field_35193_ak;
			this.field_35193_ak = 0.05F;
			super.moveEntityWithHeading(var1, var2);
			this.motionY = var9 * 0.6D;
			this.field_35193_ak = var11;
		} else {
			super.moveEntityWithHeading(var1, var2);
		}

		this.addMovementStat(this.posX - var3, this.posY - var5, this.posZ - var7);
	}

	public void addMovementStat(double var1, double var3, double var5) {
		if(this.ridingEntity == null) {
			int var7;
			if(this.isInsideOfMaterial(Material.water)) {
				var7 = Math.round(MathHelper.sqrt_double(var1 * var1 + var3 * var3 + var5 * var5) * 100.0F);
				if(var7 > 0) {
					this.addStat(StatList.distanceDoveStat, var7);
					this.func_35198_b(0.015F * (float)var7 * 0.01F);
				}
			} else if(this.isInWater()) {
				var7 = Math.round(MathHelper.sqrt_double(var1 * var1 + var5 * var5) * 100.0F);
				if(var7 > 0) {
					this.addStat(StatList.distanceSwumStat, var7);
					this.func_35198_b(0.015F * (float)var7 * 0.01F);
				}
			} else if(this.isOnLadder()) {
				if(var3 > 0.0D) {
					this.addStat(StatList.distanceClimbedStat, (int)Math.round(var3 * 100.0D));
				}
			} else if(this.onGround) {
				var7 = Math.round(MathHelper.sqrt_double(var1 * var1 + var5 * var5) * 100.0F);
				if(var7 > 0) {
					this.addStat(StatList.distanceWalkedStat, var7);
					if(this.func_35149_at()) {
						this.func_35198_b(10.0F * 0.01F * (float)var7 * 0.01F);
					} else {
						this.func_35198_b(0.01F * (float)var7 * 0.01F);
					}
				}
			} else {
				var7 = Math.round(MathHelper.sqrt_double(var1 * var1 + var5 * var5) * 100.0F);
				if(var7 > 25) {
					this.addStat(StatList.distanceFlownStat, var7);
				}
			}

		}
	}

	private void addMountedMovementStat(double var1, double var3, double var5) {
		if(this.ridingEntity != null) {
			int var7 = Math.round(MathHelper.sqrt_double(var1 * var1 + var3 * var3 + var5 * var5) * 100.0F);
			if(var7 > 0) {
				if(this.ridingEntity instanceof EntityMinecart) {
					this.addStat(StatList.distanceByMinecartStat, var7);
					if(this.startMinecartRidingCoordinate == null) {
						this.startMinecartRidingCoordinate = new ChunkCoordinates(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.posY), MathHelper.floor_double(this.posZ));
					} else if(this.startMinecartRidingCoordinate.getSqDistanceTo(MathHelper.floor_double(this.posX), MathHelper.floor_double(this.posY), MathHelper.floor_double(this.posZ)) >= 1000.0D) {
						this.addStat(AchievementList.onARail, 1);
					}
				} else if(this.ridingEntity instanceof EntityBoat) {
					this.addStat(StatList.distanceByBoatStat, var7);
				} else if(this.ridingEntity instanceof EntityPig) {
					this.addStat(StatList.distanceByPigStat, var7);
				}
			}
		}

	}

	protected void fall(float var1) {
		if(!this.field_35214_K.field_35659_c) {
			if(var1 >= 2.0F) {
				this.addStat(StatList.distanceFallenStat, (int)Math.round((double)var1 * 100.0D));
			}

			super.fall(var1);
		}
	}

	public void onKillEntity(EntityLiving var1) {
		if(var1 instanceof EntityMob) {
			this.triggerAchievement(AchievementList.killEnemy);
		}

	}

	public void setInPortal() {
		if(this.timeUntilPortal > 0) {
			this.timeUntilPortal = 10;
		} else {
			this.inPortal = true;
		}
	}

	public void func_35195_d(int var1) {
		this.field_35210_L += var1;
		this.field_35212_N += var1;

		while(this.field_35210_L >= this.func_35203_U()) {
			this.field_35210_L -= this.func_35203_U();
			this.updateHeldItem();
		}

	}

	public int func_35203_U() {
		return (this.field_35211_M + 1) * 10;
	}

	private void updateHeldItem() {
		++this.field_35211_M;
	}

	public void func_35198_b(float var1) {
		if(!this.field_35214_K.field_35660_a) {
			if(!this.worldObj.singleplayerWorld) {
				this.field_35217_m.func_35583_a(var1);
			}

		}
	}

	public FoodStats func_35207_V() {
		return this.field_35217_m;
	}

	public boolean func_35197_c(boolean var1) {
		return (var1 || this.field_35217_m.func_35587_b()) && !this.field_35214_K.field_35660_a;
	}

	public boolean func_35206_W() {
		return this.health > 0 && this.health < 20;
	}

	public void func_35201_a(ItemStack var1, int var2) {
		if(var1 != this.field_34908_d) {
			this.field_34908_d = var1;
			this.field_34909_e = var2;
			if(!this.worldObj.singleplayerWorld) {
				this.func_35148_h(true);
			}

		}
	}

	public boolean func_35200_c(int var1, int var2, int var3) {
		return true;
	}

	protected int a(EntityPlayer var1) {
		return this.field_35212_N >> 1;
	}

	protected boolean func_35188_X() {
		return true;
	}

	public String func_35150_Y() {
		return this.username;
	}
}
