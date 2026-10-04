package net.minecraft.src;

public class ModelPig extends ModelQuadruped {
	public ModelRenderer field_35401_a;

	public ModelPig() {
		this(0.0F);
	}

	public ModelPig(float var1) {
		super(6, var1);
		this.field_35401_a = new ModelRenderer(this, 16, 16);
		this.field_35401_a.addBox(-2.0F, 0.0F, -9.0F, 4, 3, 1, var1);
		this.field_35401_a.setRotationPoint(0.0F, 12.0F, -6.0F);
	}

	public void setRotationAngles(float var1, float var2, float var3, float var4, float var5, float var6) {
		super.setRotationAngles(var1, var2, var3, var4, var5, var6);
		this.field_35401_a.func_35969_a(this.head);
	}

	public void render(Entity var1, float var2, float var3, float var4, float var5, float var6, float var7) {
		super.render(var1, var2, var3, var4, var5, var6, var7);
		this.field_35401_a.render(var7);
	}
}
