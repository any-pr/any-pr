package net.minecraft.src;

public class ItemFood extends Item {
	public final int field_35430_a;
	private final int healAmount;
	private final float field_35428_c;
	private final boolean isWolfsFavoriteMeat;
	private boolean field_35431_bw;
	private int field_35433_bx;
	private int field_35432_by;
	private int field_35427_bz;
	private float field_35429_bA;

	public ItemFood(int var1, int var2, float var3, boolean var4) {
		super(var1);
		this.field_35430_a = 32;
		this.healAmount = var2;
		this.isWolfsFavoriteMeat = var4;
		this.field_35428_c = var3;
	}

	public ItemFood(int var1, int var2, boolean var3) {
		this(var1, var2, 0.6F, var3);
	}

	public ItemStack func_35413_b(ItemStack var1, World var2, EntityPlayer var3) {
		--var1.stackSize;
		var3.func_35191_at().func_35761_a(this);
		if(!var2.multiplayerWorld && this.field_35433_bx > 0 && var2.rand.nextFloat() < this.field_35429_bA) {
			var3.func_35165_a(new PotionEffect(this.field_35433_bx, this.field_35432_by * 20, this.field_35427_bz));
		}

		return var1;
	}

	public int func_35411_c(ItemStack var1) {
		return 32;
	}

	public EnumAction func_35412_b(ItemStack var1) {
		return EnumAction.eat;
	}

	public ItemStack onItemRightClick(ItemStack var1, World var2, EntityPlayer var3) {
		if(var3.func_35197_b(this.field_35431_bw)) {
			var3.func_35199_b(var1, this.func_35411_c(var1));
		}

		return var1;
	}

	public int getHealAmount() {
		return this.healAmount;
	}

	public float func_35426_m() {
		return this.field_35428_c;
	}

	public boolean getIsWolfsFavoriteMeat() {
		return this.isWolfsFavoriteMeat;
	}

	public ItemFood func_35425_a(int var1, int var2, int var3, float var4) {
		this.field_35433_bx = var1;
		this.field_35432_by = var2;
		this.field_35427_bz = var3;
		this.field_35429_bA = var4;
		return this;
	}

	public ItemFood func_35424_o() {
		this.field_35431_bw = true;
		return this;
	}

	public Item setItemName(String var1) {
		return super.setItemName(var1);
	}
}
