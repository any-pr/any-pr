package fixture.overloadpin;

/**
 * A class as the byte merge leaves it when two ecosystems patched the same method: ONE body of {@code render}, but
 * both ecosystems' lambda bodies, under the same name. javac numbers lambdas per class and cannot produce two
 * {@code lambda$render$0}; only a merge can, so they are written out by hand here (and no real lambda is used in
 * this class, or javac's own would collide with them).
 *
 * <p>The kept body is the other ecosystem's and reaches only its own lambda shape. Vanilla's shape — the one a
 * guest mixin is written against — is reachable from nothing, so the duplicate-lambda pruner drops it.
 */
public class SkyPass {
	public String render() {
		StringBuilder out = new StringBuilder();
		lambda$render$0(out, "sky", "moon");
		return out.toString();
	}

	/** The surviving ecosystem's lambda: its body calls this one. */
	private void lambda$render$0(StringBuilder out, String sky, String moon) {
		out.append("drew ").append(sky).append('+').append(moon);
	}

	/** Vanilla's lambda, orphaned by the merge: nothing in this class reaches it. */
	private void lambda$render$0(String sky, StringBuilder out) {
		out.append("vanilla drew ").append(sky);
	}
}
