package net.minecraft.src;

public class ItemFood extends Item {
	public final int field_35427_a;
	private final int healAmount;
	private final float field_35431_bu;
	private final boolean isWolfsFavoriteMeat;
	private boolean field_35428_bw;
	private int field_35430_bx;
	private int field_35429_by;
	private int field_35425_bz;
	private float field_35426_bA;

	public ItemFood(int var1, int var2, float var3, boolean var4) {
		super(var1);
		this.field_35427_a = 32;
		this.healAmount = var2;
		this.isWolfsFavoriteMeat = var4;
		this.field_35431_bu = var3;
	}

	public ItemFood(int var1, int var2, boolean var3) {
		this(var1, var2, 0.6F, var3);
	}

	public ItemStack func_35405_b(ItemStack var1, World var2, EntityPlayer var3) {
		--var1.stackSize;
		var3.func_35207_V().func_35582_a(this);
		if(!var2.singleplayerWorld && this.field_35430_bx > 0 && var2.rand.nextFloat() < this.field_35426_bA) {
			var3.func_35182_d(new PotionEffect(this.field_35430_bx, this.field_35429_by * 20, this.field_35425_bz));
		}

		return var1;
	}

	public int func_35404_c(ItemStack var1) {
		return 32;
	}

	public EnumAction func_35406_b(ItemStack var1) {
		return EnumAction.eat;
	}

	public ItemStack onItemRightClick(ItemStack var1, World var2, EntityPlayer var3) {
		if(var3.func_35197_c(this.field_35428_bw)) {
			var3.func_35201_a(var1, this.func_35404_c(var1));
		}

		return var1;
	}

	public int getHealAmount() {
		return this.healAmount;
	}

	public float func_35424_l() {
		return this.field_35431_bu;
	}

	public boolean getIsWolfsFavoriteMeat() {
		return this.isWolfsFavoriteMeat;
	}

	public ItemFood func_35422_a(int var1, int var2, int var3, float var4) {
		this.field_35430_bx = var1;
		this.field_35429_by = var2;
		this.field_35425_bz = var3;
		this.field_35426_bA = var4;
		return this;
	}

	public ItemFood func_35423_n() {
		this.field_35428_bw = true;
		return this;
	}

	public Item setItemName(String var1) {
		return super.setItemName(var1);
	}
}
