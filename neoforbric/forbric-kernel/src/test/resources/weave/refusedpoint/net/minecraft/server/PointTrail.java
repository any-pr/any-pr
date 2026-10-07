package net.minecraft.server;

/** What both targets and the mixin write to, so one probe reads every hook. */
public final class PointTrail {
	private static final StringBuilder TRAIL = new StringBuilder();

	private PointTrail() {
	}

	public static void add(String s) {
		TRAIL.append(s);
	}

	public static String read() {
		return TRAIL.toString().trim();
	}
}
