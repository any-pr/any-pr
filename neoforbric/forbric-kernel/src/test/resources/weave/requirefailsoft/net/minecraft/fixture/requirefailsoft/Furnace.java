package net.minecraft.fixture.requirefailsoft;

/**
 * A merged-base class in miniature, in the game's package so the kernel judges it as one. On the carrier that won the
 * merge, burn() no longer calls consumeFuel() itself (the call moved elsewhere), so an injector a mod wrote on that
 * call, with its own require = 1, finds nothing to attach to.
 */
public class Furnace {
	public String burn() {
		return "burning";
	}

	public void consumeFuel() {
	}
}
