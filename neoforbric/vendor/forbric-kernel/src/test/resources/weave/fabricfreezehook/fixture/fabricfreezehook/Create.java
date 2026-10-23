package fixture.fabricfreezehook;

/**
 * The mod in Create Fly's shape. With fabric-api loaded its Fabric main creates the registries; without it, its HEAD
 * injector on {@code BuiltInRegistries.freeze()} does. {@link #FABRIC_API} stands in for
 * {@code FabricLoader.isModLoaded("fabric-api")}.
 */
public final class Create {
	public static final String FABRIC_API = "fixture.fabricfreezehook.fabricApi";

	private Create() {
	}

	public static boolean fabricApiLoaded() {
		return "present".equals(System.getProperty(FABRIC_API));
	}

	/** The Fabric main entrypoint, {@code Create.onInitialize}. */
	public static void onInitialize() {
		Trace.add("main");
		if (fabricApiLoaded()) register();
	}

	/** Creates the registries: the first touch of {@link CreateRegistries} registers them into the root. */
	public static void register() {
		Trace.add("create:register");
		CreateRegistries.init();
	}
}
