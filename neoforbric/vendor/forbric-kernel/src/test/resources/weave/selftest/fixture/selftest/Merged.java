package fixture.selftest;

/**
 * A byte-merged class in miniature, for the harness's pre-Mixin chain: two bodies named {@code lambda$label$0}, which
 * javac never emits for lambdas and only a merge leaves behind. The (String) one is live; the (String, int) one is
 * reached by nothing, the half that lost, which KernelBoot's DuplicateLambdaPruneInjector removes before Mixin looks.
 */
public class Merged {
	public String label() {
		return lambda$label$0("merged");
	}

	/** How many such bodies the DEFINED class kept, and what label() answered. */
	public String probe() {
		int bodies = 0;
		for (java.lang.reflect.Method method : Merged.class.getDeclaredMethods()) {
			if (method.getName().equals("lambda$label$0")) bodies++;
		}
		return "bodies=" + bodies + " label=" + label();
	}

	private static String lambda$label$0(String value) {
		return value;
	}

	private static String lambda$label$0(String value, int unused) {
		return value + unused;
	}
}
