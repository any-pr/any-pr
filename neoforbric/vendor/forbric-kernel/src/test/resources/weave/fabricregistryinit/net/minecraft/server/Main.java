package net.minecraft.server;

import fixture.fabricregistryinit.Trace;
import net.minecraft.util.Util;

/** Hand-written stand-in, not game code: the server entry point Fabric's post-freeze hook anchors in. */
public final class Main {
	private Main() {
	}

	public static void main(String[] args) {
		Trace.add("main:start");
		Util.startTimerHackThread();
		Trace.add("main:end");
	}
}
