package fixture.localscapture;

/**
 * A merged method in miniature. The body a mod was written against keeps {@code block} and {@code state} as its first
 * two locals; the other family's patch put {@code xp} between them, the way NeoForge's break event sits between the
 * locals architectury's MixinServerPlayerGameMode captures from destroyBlock.
 */
public class BlockBreaker {
	public String destroyBlock() {
		String block = pick("stone");
		int xp = block.length();
		String state = pick("broken");
		drop(block, state, xp);
		return block + ":" + state;
	}

	private static String pick(String value) {
		return value;
	}

	private static void drop(String block, String state, int xp) {
	}
}
