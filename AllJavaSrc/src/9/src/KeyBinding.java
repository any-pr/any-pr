package net.minecraft.src;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class KeyBinding {
	public static List field_35967_a = new ArrayList();
	public static MCHash field_35966_b = new MCHash();
	public String keyDescription;
	public int keyCode;
	public boolean field_35965_e;
	public int field_35964_f = 0;

	public static void func_35960_a(int var0) {
		KeyBinding var1 = (KeyBinding)field_35966_b.lookup(var0);
		if(var1 != null) {
			++var1.field_35964_f;
		}

	}

	public static void func_35963_a(int var0, boolean var1) {
		KeyBinding var2 = (KeyBinding)field_35966_b.lookup(var0);
		if(var2 != null) {
			var2.field_35965_e = var1;
		}

	}

	public static void func_35959_a() {
		Iterator var0 = field_35967_a.iterator();

		while(var0.hasNext()) {
			KeyBinding var1 = (KeyBinding)var0.next();
			var1.func_35958_d();
		}

	}

	public static void func_35961_b() {
		field_35966_b.clearMap();
		Iterator var0 = field_35967_a.iterator();

		while(var0.hasNext()) {
			KeyBinding var1 = (KeyBinding)var0.next();
			field_35966_b.addKey(var1.keyCode, var1);
		}

	}

	public KeyBinding(String var1, int var2) {
		this.keyDescription = var1;
		this.keyCode = var2;
		field_35967_a.add(this);
		field_35966_b.addKey(var2, this);
	}

	public boolean func_35962_c() {
		if(this.field_35964_f == 0) {
			return false;
		} else {
			--this.field_35964_f;
			return true;
		}
	}

	private void func_35958_d() {
		this.field_35964_f = 0;
		this.field_35965_e = false;
	}
}
