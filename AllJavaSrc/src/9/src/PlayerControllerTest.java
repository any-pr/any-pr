package net.minecraft.src;

import net.minecraft.client.Minecraft;

public class PlayerControllerTest extends PlayerController {
	private int field_35647_c;

	public PlayerControllerTest(Minecraft var1) {
		super(var1);
		this.isInTestMode = true;
	}

	public static void func_35646_d(EntityPlayer var0) {
		var0.field_35212_aW.field_35758_c = true;
		var0.field_35212_aW.field_35756_d = true;
		var0.field_35212_aW.field_35759_a = true;
	}

	public static void func_35645_e(EntityPlayer var0) {
		var0.field_35212_aW.field_35758_c = false;
		var0.field_35212_aW.field_35757_b = false;
		var0.field_35212_aW.field_35756_d = false;
		var0.field_35212_aW.field_35759_a = false;
	}

	public void func_6473_b(EntityPlayer var1) {
		func_35646_d(var1);

		for(int var2 = 0; var2 < 9; ++var2) {
			if(var1.inventory.mainInventory[var2] == null) {
				var1.inventory.mainInventory[var2] = new ItemStack((Block)Session.registeredBlocksList.get(var2));
			}
		}

	}

	public static void func_35644_a(Minecraft var0, PlayerController var1, int var2, int var3, int var4, int var5) {
		var0.theWorld.onBlockHit(var0.thePlayer, var2, var3, var4, var5);
		var1.sendBlockRemoved(var2, var3, var4, var5);
	}

	public boolean sendPlaceBlock(EntityPlayer var1, World var2, ItemStack var3, int var4, int var5, int var6, int var7) {
		int var8 = var2.getBlockId(var4, var5, var6);
		if(var8 > 0 && Block.blocksList[var8].blockActivated(var2, var4, var5, var6, var1)) {
			return true;
		} else if(var3 == null) {
			return false;
		} else {
			int var9 = var3.getItemDamage();
			int var10 = var3.stackSize;
			boolean var11 = var3.useItem(var1, var2, var4, var5, var6, var7);
			var3.setItemDamage(var9);
			var3.stackSize = var10;
			return var11;
		}
	}

	public void clickBlock(int var1, int var2, int var3, int var4) {
		func_35644_a(this.mc, this, var1, var2, var3, var4);
		this.field_35647_c = 5;
	}

	public void sendBlockRemoving(int var1, int var2, int var3, int var4) {
		--this.field_35647_c;
		if(this.field_35647_c <= 0) {
			this.field_35647_c = 5;
			func_35644_a(this.mc, this, var1, var2, var3, var4);
		}

	}

	public void resetBlockRemoving() {
	}

	public boolean shouldDrawHUD() {
		return false;
	}

	public void func_717_a(World var1) {
		super.func_717_a(var1);
	}

	public float getBlockReachDistance() {
		return 5.0F;
	}

	public boolean func_35641_g() {
		return false;
	}

	public boolean func_35640_h() {
		return true;
	}

	public boolean func_35636_i() {
		return true;
	}
}
