package net.minecraft.src;

class PlayerListEntry {
	final long field_35834_a;
	Object field_35832_b;
	PlayerListEntry field_35833_c;
	final int field_35831_d;

	PlayerListEntry(int var1, long var2, Object var4, PlayerListEntry var5) {
		this.field_35832_b = var4;
		this.field_35833_c = var5;
		this.field_35834_a = var2;
		this.field_35831_d = var1;
	}

	public final long func_35830_a() {
		return this.field_35834_a;
	}

	public final Object func_35829_b() {
		return this.field_35832_b;
	}

	public final boolean equals(Object var1) {
		if(!(var1 instanceof PlayerListEntry)) {
			return false;
		} else {
			PlayerListEntry var2 = (PlayerListEntry)var1;
			Long var3 = Long.valueOf(this.func_35830_a());
			Long var4 = Long.valueOf(var2.func_35830_a());
			if(var3 == var4 || var3 != null && var3.equals(var4)) {
				Object var5 = this.func_35829_b();
				Object var6 = var2.func_35829_b();
				if(var5 == var6 || var5 != null && var5.equals(var6)) {
					return true;
				}
			}

			return false;
		}
	}

	public final int hashCode() {
		return PlayerList.func_35566_f(this.field_35834_a);
	}

	public final String toString() {
		return this.func_35830_a() + "=" + this.func_35829_b();
	}
}
