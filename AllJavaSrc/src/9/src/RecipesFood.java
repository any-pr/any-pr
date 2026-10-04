package net.minecraft.src;

public class RecipesFood {
	public void addRecipes(CraftingManager var1) {
		var1.addRecipe(new ItemStack(Item.bowlSoup), new Object[]{"Y", "X", "#", Character.valueOf('X'), Block.mushroomBrown, Character.valueOf('Y'), Block.mushroomRed, Character.valueOf('#'), Item.bowlEmpty});
		var1.addRecipe(new ItemStack(Item.bowlSoup), new Object[]{"Y", "X", "#", Character.valueOf('X'), Block.mushroomRed, Character.valueOf('Y'), Block.mushroomBrown, Character.valueOf('#'), Item.bowlEmpty});
		var1.addRecipe(new ItemStack(Item.cookie, 8), new Object[]{"#X#", Character.valueOf('X'), new ItemStack(Item.dyePowder, 1, 3), Character.valueOf('#'), Item.wheat});
		var1.addRecipe(new ItemStack(Block.field_35281_bs), new Object[]{"MMM", "MMM", "MMM", Character.valueOf('M'), Item.field_35421_bg});
		var1.addRecipe(new ItemStack(Item.field_35423_bi), new Object[]{"M", Character.valueOf('M'), Item.field_35421_bg});
	}
}
