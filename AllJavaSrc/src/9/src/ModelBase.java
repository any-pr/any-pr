package net.minecraft.src;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class ModelBase {
	public float onGround;
	public boolean isRiding = false;
	public List field_35394_j = new ArrayList();

	public void render(Entity var1, float var2, float var3, float var4, float var5, float var6, float var7) {
	}

	public void setRotationAngles(float var1, float var2, float var3, float var4, float var5, float var6) {
	}

	public void setLivingAnimations(EntityLiving var1, float var2, float var3, float var4) {
	}

	public ModelRenderer func_35393_a(Random var1) {
		return (ModelRenderer)this.field_35394_j.get(var1.nextInt(this.field_35394_j.size()));
	}
}
