package net.minecraft.src;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

public class WeightedRandom {
	public static int func_35692_a(Collection var0) {
		int var1 = 0;

		WeightedRandomChoice var3;
		for(Iterator var2 = var0.iterator(); var2.hasNext(); var1 += var3.field_35483_d) {
			var3 = (WeightedRandomChoice)var2.next();
		}

		return var1;
	}

	public static WeightedRandomChoice func_35693_a(Random var0, Collection var1, int var2) {
		if(var2 <= 0) {
			throw new IllegalArgumentException();
		} else {
			int var3 = var0.nextInt(var2);
			Iterator var4 = var1.iterator();

			WeightedRandomChoice var5;
			do {
				if(!var4.hasNext()) {
					return null;
				}

				var5 = (WeightedRandomChoice)var4.next();
				var3 -= var5.field_35483_d;
			} while(var3 >= 0);

			return var5;
		}
	}

	public static WeightedRandomChoice func_35689_a(Random var0, Collection var1) {
		return func_35693_a(var0, var1, func_35692_a(var1));
	}

	public static int func_35690_a(WeightedRandomChoice[] var0) {
		int var1 = 0;
		WeightedRandomChoice[] var2 = var0;
		int var3 = var0.length;

		for(int var4 = 0; var4 < var3; ++var4) {
			WeightedRandomChoice var5 = var2[var4];
			var1 += var5.field_35483_d;
		}

		return var1;
	}

	public static WeightedRandomChoice func_35688_a(Random var0, WeightedRandomChoice[] var1, int var2) {
		if(var2 <= 0) {
			throw new IllegalArgumentException();
		} else {
			int var3 = var0.nextInt(var2);
			WeightedRandomChoice[] var4 = var1;
			int var5 = var1.length;

			for(int var6 = 0; var6 < var5; ++var6) {
				WeightedRandomChoice var7 = var4[var6];
				var3 -= var7.field_35483_d;
				if(var3 < 0) {
					return var7;
				}
			}

			return null;
		}
	}

	public static WeightedRandomChoice func_35691_a(Random var0, WeightedRandomChoice[] var1) {
		return func_35688_a(var0, var1, func_35690_a(var1));
	}
}
