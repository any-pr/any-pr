package fixture.atshape;

/** MixinAtShape's target: answers "limit=8" unless the guest @Redirect on its limit() call was woven and runs. */
public class Spawner {
	public String spawn() {
		return "limit=" + limit();
	}

	public int limit() {
		return 8;
	}
}
