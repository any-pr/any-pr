package net.minecraft.src;

public class ModelLargeChest extends ModelChest {
	public ModelLargeChest() {
		this.field_35405_a = (new ModelRenderer(this, 0, 0)).func_35968_a(128, 64);
		this.field_35405_a.addBox(0.0F, -5.0F, -14.0F, 30, 5, 14, 0.0F);
		this.field_35405_a.rotationPointX = 1.0F;
		this.field_35405_a.rotationPointY = 7.0F;
		this.field_35405_a.rotationPointZ = 15.0F;
		this.field_35404_c = (new ModelRenderer(this, 0, 0)).func_35968_a(128, 64);
		this.field_35404_c.addBox(-1.0F, -2.0F, -15.0F, 2, 4, 1, 0.0F);
		this.field_35404_c.rotationPointX = 16.0F;
		this.field_35404_c.rotationPointY = 7.0F;
		this.field_35404_c.rotationPointZ = 15.0F;
		this.field_35403_b = (new ModelRenderer(this, 0, 19)).func_35968_a(128, 64);
		this.field_35403_b.addBox(0.0F, 0.0F, 0.0F, 30, 10, 14, 0.0F);
		this.field_35403_b.rotationPointX = 1.0F;
		this.field_35403_b.rotationPointY = 6.0F;
		this.field_35403_b.rotationPointZ = 1.0F;
	}
}
