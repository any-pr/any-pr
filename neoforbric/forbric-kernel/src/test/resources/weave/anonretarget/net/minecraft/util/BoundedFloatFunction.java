package net.minecraft.util;

/**
 * Hand-written stand-in, not game code: an outer class whose anonymous classes the merge renumbered.
 *
 * <p>{@code MergedBaseAnonymousDrift.RELOCATED} records that vanilla's {@code BoundedFloatFunction$2} body lives at
 * {@code $1} on the merged base, and that is the only reason this name is used. The layout is the MERGED one:
 * javac numbers the two classes below {@code $1} and {@code $2} in source order, so {@code $1} carries the body a
 * mod compiled against vanilla's {@code $2} expects, and {@code $2} is a different class that inherited the number
 * with a method of the same name and descriptor — a mixin left on it applies cleanly, to the wrong code.
 */
public interface BoundedFloatFunction {
	String describe(String input);

	/** {@code $1}: the body vanilla compiled at {@code $2}. It calls its own helper, so a pinned INVOKE names it. */
	BoundedFloatFunction MOVED_BODY = new BoundedFloatFunction() {
		@Override
		public String describe(String input) {
			return this.tag(input);
		}

		String tag(String input) {
			return "vanilla-body(" + input + ")";
		}
	};

	/** {@code $2}: the class that inherited the number. Same method shape, no helper call. */
	BoundedFloatFunction NUMBER_HEIR = new BoundedFloatFunction() {
		@Override
		public String describe(String input) {
			return "heir-body(" + input + ")";
		}
	};
}
