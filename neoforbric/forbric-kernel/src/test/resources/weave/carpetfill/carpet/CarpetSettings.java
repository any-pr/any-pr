package carpet;

/** The guest's own switch: set while a fill runs with updates turned off. */
public final class CarpetSettings {
	public static final ThreadLocal<Boolean> impendingFillSkipUpdates = ThreadLocal.withInitial(() -> false);

	private CarpetSettings() {
	}
}
