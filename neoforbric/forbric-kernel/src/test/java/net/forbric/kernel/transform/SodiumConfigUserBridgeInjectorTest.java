package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.fabricmc.api.EnvType;

import net.forbric.kernel.boot.KernelLifecycle;
import net.forbric.kernel.fabric.FabricModMetadataParser;
import net.forbric.kernel.fabric.KernelFabricLoader;
import net.forbric.kernel.fabric.KernelModContainer;

/**
 * Sodium's NeoForge config loader, edited and run against the kernel's real hook: a Fabric mod that declares a Sodium
 * config entry point reaches Sodium's config registry, after the mods Sodium found in {@code ModList} by itself.
 *
 * <p>The stand-ins are the three game-side classes the edit and its hook touch: {@code ConfigLoaderForge} (the target,
 * with a branch, a loop and two returns, so the stack map frames the injector must carry through are really there),
 * {@code ConfigManager} (records every registration), and NeoForge's {@code ModList} (the only mods Sodium's own walk
 * can see). The Fabric mod is a real {@link KernelFabricLoader} entry, installed as the process-wide instance.
 */
@ExecutesInjector(SodiumConfigUserBridgeInjector.class)
@ResourceLock("KernelFabricEcosystem")
@ResourceLock("system-properties")
class SodiumConfigUserBridgeInjectorTest {
	private static final String CONFIG_LOADER = "net/caffeinemc/mods/sodium/neoforge/config/ConfigLoaderForge";
	private static final String CONFIG_MANAGER = "net.caffeinemc.mods.sodium.client.config.ConfigManager";
	private static final String MOD_LIST = "net.neoforged.fml.ModList";
	/** The hook's runtime switch, read in {@code KernelLifecycle.onSodiumConfigUsers}; the injector itself has none. */
	private static final String SWITCH = "forbric.sodiumConfigUsers";

	private static final String NEO_PAGE = "neopage=fixture.neo.neopage.Page";
	private static final String FABRIC_PAGE = "voxyish=fixture.voxyish.VoxyishPage";

	private static final Map<String, String> SOURCES = Map.of(
			"net.caffeinemc.mods.sodium.neoforge.config.ConfigLoaderForge", """
					package net.caffeinemc.mods.sodium.neoforge.config;

					import net.caffeinemc.mods.sodium.client.config.ConfigManager;
					import net.neoforged.fml.ModList;

					public final class ConfigLoaderForge {
						/** Sodium's own walk: only what ModList holds. Two returns, a loop, so real frames. */
						public static void collectConfigEntryPoints() {
							ModList mods = ModList.get();
							if (mods == null) return;
							for (String modId : mods.ids()) {
								ConfigManager.registerConfigEntryPoint("fixture.neo." + modId + ".Page", modId);
							}
						}
					}
					""",
			CONFIG_MANAGER, """
					package net.caffeinemc.mods.sodium.client.config;

					import java.util.ArrayList;
					import java.util.List;
					import java.util.function.Function;

					public final class ConfigManager {
						/** Every registration in arrival order, as modId=entryPoint. */
						public static final List<String> REGISTERED = new ArrayList<>();
						/** Null, so the hook leaves the name lookup alone: that half is not this edit's. */
						public static Function<String, Object> modInfoFunction;

						public static void registerConfigEntryPoint(String entryPoint, String modId) {
							REGISTERED.add(modId + "=" + entryPoint);
						}
					}
					""",
			MOD_LIST, """
					package net.neoforged.fml;

					import java.util.List;
					import java.util.Optional;

					public final class ModList {
						/** False: ModList is not up yet, and Sodium's walk takes its early return. */
						public static boolean up = true;
						private static final ModList INSTANCE = new ModList();

						public static ModList get() {
							return up ? INSTANCE : null;
						}

						public List<String> ids() {
							return List.of("neopage");
						}

						public Optional<Object> getModContainerById(String modId) {
							return ids().contains(modId) ? Optional.of(modId) : Optional.empty();
						}
					}
					""");

	@TempDir Path work;

	private Field fabricInstance;
	private Object previousFabric;
	private Field gameLoader;
	private Object previousGameLoader;
	private String previousSwitch;

	@BeforeEach
	void installAFabricModWithASodiumPage() throws Exception {
		fabricInstance = KernelFabricLoader.class.getDeclaredField("instance");
		fabricInstance.setAccessible(true);
		previousFabric = fabricInstance.get(null);
		gameLoader = KernelLifecycle.class.getDeclaredField("gameLoader");
		gameLoader.setAccessible(true);
		previousGameLoader = gameLoader.get(null);
		previousSwitch = System.getProperty(SWITCH);
		System.clearProperty(SWITCH);

		// The private constructor, not create(): create() refuses a second instance and resets global adapter state.
		Constructor<KernelFabricLoader> constructor = KernelFabricLoader.class.getDeclaredConstructor(
				EnvType.class, Path.class, Path.class, String[].class, String.class);
		constructor.setAccessible(true);
		KernelFabricLoader fabric = constructor.newInstance(EnvType.CLIENT, work, work.resolve("config"), new String[0], "26.2");
		fabric.register(new KernelModContainer(FabricModMetadataParser.read(new StringReader("""
				{"schemaVersion":1,"id":"voxyish","version":"1.0",
				 "entrypoints":{"sodium:config_api_user":["fixture.voxyish.VoxyishPage"]}}
				""")), null, null));
		fabricInstance.set(null, fabric);
	}

	@AfterEach
	void restore() throws Exception {
		fabricInstance.set(null, previousFabric);
		gameLoader.set(null, previousGameLoader);
		if (previousSwitch == null) System.clearProperty(SWITCH);
		else System.setProperty(SWITCH, previousSwitch);
	}

	@Test
	void theFabricModsPageIsRegisteredAfterSodiumsOwnWalk() throws Throwable {
		Map<String, byte[]> classes = transformed();
		ClassLoader game = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(classes.get(CONFIG_LOADER), game));

		// Sodium's ModList walk first, the kernel's Fabric pass after it: the hook sits before the return.
		assertEquals(List.of(NEO_PAGE, FABRIC_PAGE), collect(game, true));
	}

	@Test
	void theEarlyReturnIsBridgedToo() throws Throwable {
		// ModList not up: Sodium's walk returns before its loop, through the method's other RETURN.
		assertEquals(List.of(FABRIC_PAGE), collect(InjectorExecution.load(transformed()), false));
	}

	@Test
	void untransformedOnlyModListIsWalked() throws Throwable {
		Map<String, byte[]> original = compile();
		assertEquals(List.of(NEO_PAGE), collect(InjectorExecution.load(original), true));
		assertEquals(List.of(), collect(InjectorExecution.load(original), false));
	}

	@Test
	void theHooksOffSwitchLeavesOnlySodiumsOwnWalk() throws Throwable {
		System.setProperty(SWITCH, "off");
		assertEquals(List.of(NEO_PAGE), collect(InjectorExecution.load(transformed()), true));
	}

	@Test
	void aClassWithoutTheAnchorComesBackUnchanged() throws Throwable {
		SodiumConfigUserBridgeInjector injector = new SodiumConfigUserBridgeInjector();
		Map<String, byte[]> original = compile();

		byte[] manager = original.get(CONFIG_MANAGER.replace('.', '/'));
		assertSame(manager, InjectorExecution.transform(injector, CONFIG_MANAGER, manager, EnvType.CLIENT),
				"a class that is not ConfigLoaderForge");

		// The right class, but collectConfigEntryPoints is not ()V: no return site is hooked.
		byte[] otherShape = compile(Map.of("net.caffeinemc.mods.sodium.neoforge.config.ConfigLoaderForge", """
				package net.caffeinemc.mods.sodium.neoforge.config;
				public final class ConfigLoaderForge {
					public static void collectConfigEntryPoints(boolean late) {
						if (late) return;
					}
				}
				""")).get(CONFIG_LOADER);
		assertSame(otherShape, InjectorExecution.transform(injector, binary(CONFIG_LOADER), otherShape, EnvType.CLIENT),
				"a ConfigLoaderForge without collectConfigEntryPoints()V");

		// Offered twice (the pre-mixin read, then the define): the second pass must not add a second call.
		byte[] once = transformed().get(CONFIG_LOADER);
		assertSame(once, InjectorExecution.transform(injector, binary(CONFIG_LOADER), once, EnvType.CLIENT),
				"an already hooked ConfigLoaderForge");
	}

	/** Runs Sodium's collection in {@code game}, bound as the kernel's game loader, and returns what was registered. */
	private static List<?> collect(ClassLoader game, boolean modListUp) throws Throwable {
		KernelLifecycle.bind(game);
		game.loadClass(MOD_LIST).getField("up").setBoolean(null, modListUp);
		InjectorExecution.invokeStatic(game.loadClass(binary(CONFIG_LOADER)), "collectConfigEntryPoints");
		return List.copyOf((List<?>) InjectorExecution.getStatic(game.loadClass(CONFIG_MANAGER), "REGISTERED"));
	}

	private Map<String, byte[]> transformed() throws Exception {
		Map<String, byte[]> classes = new HashMap<>(compile());
		byte[] original = classes.get(CONFIG_LOADER);
		byte[] edited = InjectorExecution.transform(new SodiumConfigUserBridgeInjector(), binary(CONFIG_LOADER), original,
				EnvType.CLIENT);
		assertNotSame(original, edited, "the injector did not edit ConfigLoaderForge");
		classes.put(CONFIG_LOADER, edited);
		return classes;
	}

	private Map<String, byte[]> compile() throws Exception {
		return compile(SOURCES);
	}

	private Map<String, byte[]> compile(Map<String, String> sources) throws Exception {
		return InjectorExecution.compile(work, sources);
	}

	private static String binary(String internalName) {
		return internalName.replace('/', '.');
	}
}
