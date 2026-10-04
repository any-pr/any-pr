package net.minecraft.src;

public class ModelChest extends ModelBase {
	public ModelRenderer field_35405_a = (new ModelRenderer(this, 0, 0)).func_35968_a(64, 64);
	public ModelRenderer field_35403_b;
	public ModelRenderer field_35404_c;

	public ModelChest() {
		this.field_35405_a.addBox(0.0F, -5.0F, -14.0F, 14, 5, 14, 0.0F);
		this.field_35405_a.rotationPointX = 1.0F;
		this.field_35405_a.rotationPointY = 7.0F;
		this.field_35405_a.rotationPointZ = 15.0F;
		this.field_35404_c = (new ModelRenderer(this, 0, 0)).func_35968_a(64, 64);
		this.field_35404_c.addBox(-1.0F, -2.0F, -15.0F, 2, 4, 1, 0.0F);
		this.field_35404_c.rotationPointX = 8.0F;
		this.field_35404_c.rotationPointY = 7.0F;
		this.field_35404_c.rotationPointZ = 15.0F;
		this.field_35403_b = (new ModelRenderer(this, 0, 19)).func_35968_a(64, 64);
		this.field_35403_b.addBox(0.0F, 0.0F, 0.0F, 14, 10, 14, 0.0F);
		this.field_35403_b.rotationPointX = 1.0F;
		this.field_35403_b.rotationPointY = 6.0F;
		this.field_35403_b.rotationPointZ = 1.0F;
	}

	public void func_35402_a() {
		this.field_35404_c.rotateAngleX = this.field_35405_a.rotateAngleX;
		this.field_35405_a.render(1.0F / 16.0F);
		this.field_35404_c.render(1.0F / 16.0F);
		this.field_35403_b.render(1.0F / 16.0F);
	}
}
