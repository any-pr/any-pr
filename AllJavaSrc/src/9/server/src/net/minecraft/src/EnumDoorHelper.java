package net.minecraft.src;

class EnumDoorHelper {
	static final int[] field_35581_a = new int[EnumDoor.values().length];

	static {
		try {
			field_35581_a[EnumDoor.OPENING.ordinal()] = 1;
		} catch (NoSuchFieldError var4) {
		}

		try {
			field_35581_a[EnumDoor.WOOD_DOOR.ordinal()] = 2;
		} catch (NoSuchFieldError var3) {
		}

		try {
			field_35581_a[EnumDoor.GRATES.ordinal()] = 3;
		} catch (NoSuchFieldError var2) {
		}

		try {
			field_35581_a[EnumDoor.IRON_DOOR.ordinal()] = 4;
		} catch (NoSuchFieldError var1) {
		}

	}
}
