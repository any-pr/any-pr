package net.minecraft.src;

import java.util.ArrayList;
import java.util.List;

class ContainerCreative extends Container {
	public List field_35375_a = new ArrayList();

	public ContainerCreative(EntityPlayer var1) {
		// ---------- 1. 添加所有方块（包括所有 metadata 变种） ----------
		for (int blockId = 0; blockId < Block.blocksList.length; blockId++) {
			Block block = Block.blocksList[blockId];
			if (block == null) continue;                       // 跳过未使用的 ID
			if (Item.itemsList[blockId] == null) continue;    // 跳过没有物品形式的方块（如空气、水等）

			// 遍历所有可能的 metadata（0~15）
			for (int meta = 0; meta < 16; meta++) {
				// 某些方块在特定 metadata 下可能没有有效纹理，但添加无妨
				this.field_35375_a.add(new ItemStack(blockId, 1, meta));
			}
		}

		// ---------- 2. 添加所有非方块物品（ID >= 256） ----------
		for (int itemId = 256; itemId < Item.itemsList.length; itemId++) {
			if (Item.itemsList[itemId] != null) {
				this.field_35375_a.add(new ItemStack(Item.itemsList[itemId]));
			}
		}

		// ---------- 3. 添加染料（16 种颜色） ----------
		for (int dyeMeta = 0; dyeMeta < 16; dyeMeta++) {
			this.field_35375_a.add(new ItemStack(Item.dyePowder.shiftedIndex, 1, dyeMeta));
		}

		// ---------- 原有槽位绑定逻辑（保持不变） ----------
		InventoryPlayer var11 = var1.inventory;
		for (int var9 = 0; var9 < 9; ++var9) {
			for (int var10 = 0; var10 < 8; ++var10) {
				this.addSlot(new Slot(GuiContainerCreative.func_35310_g(), var10 + var9 * 8, 8 + var10 * 18, 18 + var9 * 18));
			}
		}
		for (int var9 = 0; var9 < 9; ++var9) {
			this.addSlot(new Slot(var11, var9, 8 + var9 * 18, 184));
		}
		this.func_35374_a(0.0F);
	}

	public boolean canInteractWith(EntityPlayer var1) {
		return true;
	}

	public void func_35374_a(float var1) {
		int var2 = this.field_35375_a.size() / 8 - 8 + 1;
		int var3 = (int)((double)(var1 * (float)var2) + 0.5D);
		if(var3 < 0) {
			var3 = 0;
		}
		for(int var4 = 0; var4 < 9; ++var4) {
			for(int var5 = 0; var5 < 8; ++var5) {
				int var6 = var5 + (var4 + var3) * 8;
				if(var6 >= 0 && var6 < this.field_35375_a.size()) {
					GuiContainerCreative.func_35310_g().setInventorySlotContents(var5 + var4 * 8, (ItemStack)this.field_35375_a.get(var6));
				} else {
					GuiContainerCreative.func_35310_g().setInventorySlotContents(var5 + var4 * 8, (ItemStack)null);
				}
			}
		}
	}

	protected void func_35373_b(int var1, int var2, boolean var3, EntityPlayer var4) {
	}
}