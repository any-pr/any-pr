package net.minecraft.network.chat;

/**
 * Hand-written stand-in with the shape that misled R3 for text_styles, no game code: withColor(I) and withShadowColor(I)
 * take the same arguments, only withShadowColor makes the checkEmptyAfterChange call, and withColor never calls it —
 * the class does, from applyShadow. Vanilla declares both, so no carrier renamed anything: carrier-renames.txt has no row.
 */
public final class Style {
	public final StringBuilder trace = new StringBuilder();

	public Style withColor(int color) {
		trace.append("color;");
		return this;
	}

	public Style withShadowColor(int color) {
		trace.append("shadow;");
		return checkEmptyAfterChange(this, null, color);
	}

	public Style applyShadow(int color) {
		return withShadowColor(color);
	}

	private static Style checkEmptyAfterChange(Style style, Object before, Object after) {
		return style;
	}
}
