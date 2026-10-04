package net.minecraft.src;

public class PlayerList {
	private transient PlayerListEntry[] field_35584_a = new PlayerListEntry[16];
	private transient int field_35582_b;
	private int field_35583_c = 12;
	private final float field_35580_d = 12.0F / 16.0F;
	private transient volatile int field_35581_e;

	private static int func_35568_g(long var0) {
		return func_35571_a((int)(var0 ^ var0 >>> 32));
	}

	private static int func_35571_a(int var0) {
		var0 ^= var0 >>> 20 ^ var0 >>> 12;
		return var0 ^ var0 >>> 7 ^ var0 >>> 4;
	}

	private static int func_35573_a(int var0, int var1) {
		return var0 & var1 - 1;
	}

	public int func_35576_a() {
		return this.field_35582_b;
	}

	public Object func_35578_a(long var1) {
		int var3 = func_35568_g(var1);

		for(PlayerListEntry var4 = this.field_35584_a[func_35573_a(var3, this.field_35584_a.length)]; var4 != null; var4 = var4.field_35833_c) {
			if(var4.field_35834_a == var1) {
				return var4.field_35832_b;
			}
		}

		return null;
	}

	public boolean func_35575_b(long var1) {
		return this.func_35569_c(var1) != null;
	}

	final PlayerListEntry func_35569_c(long var1) {
		int var3 = func_35568_g(var1);

		for(PlayerListEntry var4 = this.field_35584_a[func_35573_a(var3, this.field_35584_a.length)]; var4 != null; var4 = var4.field_35833_c) {
			if(var4.field_35834_a == var1) {
				return var4;
			}
		}

		return null;
	}

	public void func_35577_a(long var1, Object var3) {
		int var4 = func_35568_g(var1);
		int var5 = func_35573_a(var4, this.field_35584_a.length);

		for(PlayerListEntry var6 = this.field_35584_a[var5]; var6 != null; var6 = var6.field_35833_c) {
			if(var6.field_35834_a == var1) {
				var6.field_35832_b = var3;
			}
		}

		++this.field_35581_e;
		this.func_35570_a(var4, var1, var3, var5);
	}

	private void func_35567_b(int var1) {
		PlayerListEntry[] var2 = this.field_35584_a;
		int var3 = var2.length;
		if(var3 == 1073741824) {
			this.field_35583_c = Integer.MAX_VALUE;
		} else {
			PlayerListEntry[] var4 = new PlayerListEntry[var1];
			this.func_35579_a(var4);
			this.field_35584_a = var4;
			this.field_35583_c = (int)((float)var1 * this.field_35580_d);
		}
	}

	private void func_35579_a(PlayerListEntry[] var1) {
		PlayerListEntry[] var2 = this.field_35584_a;
		int var3 = var1.length;

		for(int var4 = 0; var4 < var2.length; ++var4) {
			PlayerListEntry var5 = var2[var4];
			if(var5 != null) {
				var2[var4] = null;

				PlayerListEntry var6;
				do {
					var6 = var5.field_35833_c;
					int var7 = func_35573_a(var5.field_35831_d, var3);
					var5.field_35833_c = var1[var7];
					var1[var7] = var5;
					var5 = var6;
				} while(var6 != null);
			}
		}

	}

	public Object func_35574_d(long var1) {
		PlayerListEntry var3 = this.func_35572_e(var1);
		return var3 == null ? null : var3.field_35832_b;
	}

	final PlayerListEntry func_35572_e(long var1) {
		int var3 = func_35568_g(var1);
		int var4 = func_35573_a(var3, this.field_35584_a.length);
		PlayerListEntry var5 = this.field_35584_a[var4];

		PlayerListEntry var6;
		PlayerListEntry var7;
		for(var6 = var5; var6 != null; var6 = var7) {
			var7 = var6.field_35833_c;
			if(var6.field_35834_a == var1) {
				++this.field_35581_e;
				--this.field_35582_b;
				if(var5 == var6) {
					this.field_35584_a[var4] = var7;
				} else {
					var5.field_35833_c = var7;
				}

				return var6;
			}

			var5 = var6;
		}

		return var6;
	}

	private void func_35570_a(int var1, long var2, Object var4, int var5) {
		PlayerListEntry var6 = this.field_35584_a[var5];
		this.field_35584_a[var5] = new PlayerListEntry(var1, var2, var4, var6);
		if(this.field_35582_b++ >= this.field_35583_c) {
			this.func_35567_b(2 * this.field_35584_a.length);
		}

	}

	static int func_35566_f(long var0) {
		return func_35568_g(var0);
	}
}
