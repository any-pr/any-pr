package net.minecraft.src;

public class ModelSilverfish extends ModelBase {
	private ModelRenderer[] field_35400_a = new ModelRenderer[7];
	private ModelRenderer[] field_35398_b;
	private float[] field_35399_c = new float[7];
	private static final int[][] field_35396_d = new int[][]{{3, 2, 2}, {4, 3, 2}, {6, 4, 3}, {3, 3, 3}, {2, 2, 3}, {2, 1, 2}, {1, 1, 2}};
	private static final int[][] field_35397_e = new int[][]{{0, 0}, {0, 4}, {0, 9}, {0, 16}, {0, 22}, {11, 0}, {13, 4}};

	public ModelSilverfish() {
		float var1 = -3.5F;

		for(int var2 = 0; var2 < this.field_35400_a.length; ++var2) {
			this.field_35400_a[var2] = new ModelRenderer(this, field_35397_e[var2][0], field_35397_e[var2][1]);
			this.field_35400_a[var2].addBox((float)field_35396_d[var2][0] * -0.5F, 0.0F, (float)field_35396_d[var2][2] * -0.5F, field_35396_d[var2][0], field_35396_d[var2][1], field_35396_d[var2][2]);
			this.field_35400_a[var2].setRotationPoint(0.0F, (float)(24 - field_35396_d[var2][1]), var1);
			this.field_35399_c[var2] = var1;
			if(var2 < this.field_35400_a.length - 1) {
				var1 += (float)(field_35396_d[var2][2] + field_35396_d[var2 + 1][2]) * 0.5F;
			}
		}

		this.field_35398_b = new ModelRenderer[3];
		this.field_35398_b[0] = new ModelRenderer(this, 20, 0);
		this.field_35398_b[0].addBox(-5.0F, 0.0F, (float)field_35396_d[2][2] * -0.5F, 10, 8, field_35396_d[2][2]);
		this.field_35398_b[0].setRotationPoint(0.0F, 16.0F, this.field_35399_c[2]);
		this.field_35398_b[1] = new ModelRenderer(this, 20, 11);
		this.field_35398_b[1].addBox(-3.0F, 0.0F, (float)field_35396_d[4][2] * -0.5F, 6, 4, field_35396_d[4][2]);
		this.field_35398_b[1].setRotationPoint(0.0F, 20.0F, this.field_35399_c[4]);
		this.field_35398_b[2] = new ModelRenderer(this, 20, 18);
		this.field_35398_b[2].addBox(-3.0F, 0.0F, (float)field_35396_d[4][2] * -0.5F, 6, 5, field_35396_d[1][2]);
		this.field_35398_b[2].setRotationPoint(0.0F, 19.0F, this.field_35399_c[1]);
	}

	public int func_35395_a() {
		return 38;
	}

	public void render(Entity var1, float var2, float var3, float var4, float var5, float var6, float var7) {
		this.setRotationAngles(var2, var3, var4, var5, var6, var7);

		int var8;
		for(var8 = 0; var8 < this.field_35400_a.length; ++var8) {
			this.field_35400_a[var8].render(var7);
		}

		for(var8 = 0; var8 < this.field_35398_b.length; ++var8) {
			this.field_35398_b[var8].render(var7);
		}

	}

	public void setRotationAngles(float var1, float var2, float var3, float var4, float var5, float var6) {
		for(int var7 = 0; var7 < this.field_35400_a.length; ++var7) {
			this.field_35400_a[var7].rotateAngleY = MathHelper.cos(var3 * 0.9F + (float)var7 * 0.15F * (float)Math.PI) * (float)Math.PI * 0.05F * (float)(1 + Math.abs(var7 - 2));
			this.field_35400_a[var7].rotationPointX = MathHelper.sin(var3 * 0.9F + (float)var7 * 0.15F * (float)Math.PI) * (float)Math.PI * 0.2F * (float)Math.abs(var7 - 2);
		}

		this.field_35398_b[0].rotateAngleY = this.field_35400_a[2].rotateAngleY;
		this.field_35398_b[1].rotateAngleY = this.field_35400_a[4].rotateAngleY;
		this.field_35398_b[1].rotationPointX = this.field_35400_a[4].rotationPointX;
		this.field_35398_b[2].rotateAngleY = this.field_35400_a[1].rotateAngleY;
		this.field_35398_b[2].rotationPointX = this.field_35400_a[1].rotationPointX;
	}
}
