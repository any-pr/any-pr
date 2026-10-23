package fixture.nativetail;

import java.util.ArrayList;
import java.util.List;

/** What the TAIL handler saw, in call order. */
public final class Seen {
	private static final List<String> CHOICES = new ArrayList<>();

	private Seen() {
	}

	public static void choice(Object chosen) {
		CHOICES.add(String.valueOf(chosen));
	}

	public static String all() {
		return CHOICES.toString();
	}
}
