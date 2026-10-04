package net.minecraft.src;

public class RenderSilverfish extends RenderLiving {
	private int field_35450_c;

	public RenderSilverfish() {
		super(new ModelSilverfish(), 0.3F);
	}

	protected float func_35447_a(EntitySilverfish var1) {
		return 180.0F;
	}

	public void func_35448_a(EntitySilverfish var1, double var2, double var4, double var6, float var8, float var9) {
		int var10 = ((ModelSilverfish)this.mainModel).func_35395_a();
		if(var10 != this.field_35450_c) {
			this.field_35450_c = var10;
			this.mainModel = new ModelSilverfish();
			System.out.println("new silverfish model");
		}

		super.doRenderLiving(var1, var2, var4, var6, var8, var9);
	}

	protected boolean func_35449_a(EntitySilverfish var1, int var2, float var3) {
		return false;
	}

	protected float getDeathMaxRotation(EntityLiving var1) {
		return this.func_35447_a((EntitySilverfish)var1);
	}

	protected boolean shouldRenderPass(EntityLiving var1, int var2, float var3) {
		return this.func_35449_a((EntitySilverfish)var1, var2, var3);
	}

	public void doRenderLiving(EntityLiving var1, double var2, double var4, double var6, float var8, float var9) {
		this.func_35448_a((EntitySilverfish)var1, var2, var4, var6, var8, var9);
	}

	public void doRender(Entity var1, double var2, double var4, double var6, float var8, float var9) {
		this.func_35448_a((EntitySilverfish)var1, var2, var4, var6, var8, var9);
	}
}
