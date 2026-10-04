package net.minecraft.src;

public class ChestItemRenderHelper {
	public static ChestItemRenderHelper field_35611_a = new ChestItemRenderHelper();
	private TileEntityChest field_35610_b = new TileEntityChest();

	public void func_35609_a(Block var1, int var2, float var3) {
		TileEntityRenderer.instance.renderTileEntityAt(this.field_35610_b, 0.0D, 0.0D, 0.0D, 0.0F);
	}
}
