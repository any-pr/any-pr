package net.minecraft.util;

import fixture.fabricregistryinit.Trace;

/** Hand-written stand-in, not game code: the one call Fabric's server hook names as its anchor. */
public final class Util {
	private Util() {
	}

	public static void startTimerHackThread() {
		Trace.add("timerHack");
	}
}
