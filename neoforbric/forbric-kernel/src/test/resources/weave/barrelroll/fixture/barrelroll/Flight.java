package fixture.barrelroll;

import java.util.ArrayList;
import java.util.List;

/** What the guest's handlers did, for the probe to report. */
public final class Flight {
	/** Degrees of roll per unit of tick delta while flying. */
	public static final float ROLL_PER_TICK = 40.0F;
	public static final List<String> RAN = new ArrayList<>();

	private Flight() {
	}

	public static void ran(String hook) {
		RAN.add(hook);
	}
}
