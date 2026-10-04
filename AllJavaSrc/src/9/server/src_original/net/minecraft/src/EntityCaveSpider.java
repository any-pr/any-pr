package net.minecraft.src;

public class EntityCaveSpider extends EntitySpider {
	public EntityCaveSpider(World var1) {
		super(var1);
		this.texture = "/mob/cavespider.png";
		this.setSize(0.7F, 0.5F);
	}

	protected boolean func_35224_c(Entity var1) {
		if(super.func_35224_c(var1)) {
			if(var1 instanceof EntityLiving) {
				byte var2 = 0;
				if(this.worldObj.difficultySetting > 1) {
					if(this.worldObj.difficultySetting == 2) {
						var2 = 7;
					} else if(this.worldObj.difficultySetting == 3) {
						var2 = 15;
					}
				}

				if(var2 > 0) {
					((EntityLiving)var1).func_35182_d(new PotionEffect(Potion.field_35466_u.field_35447_H, var2 * 20, 0));
				}
			}

			return true;
		} else {
			return false;
		}
	}
}
