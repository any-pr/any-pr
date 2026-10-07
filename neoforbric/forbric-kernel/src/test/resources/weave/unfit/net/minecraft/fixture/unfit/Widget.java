package net.minecraft.fixture.unfit;

/**
 * A merged-base class in miniature, in the game's package so the kernel judges it as one. The carrier that won the
 * merge renamed vanilla's oldLabel() away; label() is what the merged class has.
 */
public class Widget {
	public String label() {
		return "widget";
	}
}
