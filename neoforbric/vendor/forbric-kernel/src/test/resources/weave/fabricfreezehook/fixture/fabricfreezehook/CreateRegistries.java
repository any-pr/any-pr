package fixture.fabricfreezehook;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Create's {@code CreateRegistries}: its initialiser creates the mod's registry and registers it into the root, so
 * whatever touches it first decides when that happens. In a frozen root it throws — issue #52.
 */
public final class CreateRegistries {
	public static final MappedRegistry ARM_INTERACTION_POINT_TYPE = create("create:arm_interaction_point_type");

	private CreateRegistries() {
	}

	/** Runs the initialiser, if nothing has yet. */
	public static void init() {
	}

	private static MappedRegistry create(String key) {
		BuiltInRegistries.WRITABLE_REGISTRY.register(key);
		return new MappedRegistry(key);
	}
}
