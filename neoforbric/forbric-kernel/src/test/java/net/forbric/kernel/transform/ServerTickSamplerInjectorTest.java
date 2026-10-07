package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;
import net.forbric.kernel.boot.KernelServerTicks;

/**
 * {@link ServerTickSamplerInjector}'s output, run: a stand-in {@code MinecraftServer} whose transformed
 * {@code tickServer} must feed {@link KernelServerTicks}, the one performance number this loader has.
 *
 * <p>The sampler's own accounting is {@code KernelServerTicksTest}'s; this is whether a real tick ever reaches it.
 * Its first call only arms it, so two ticks are the least that can show one sample.
 */
@ExecutesInjector(ServerTickSamplerInjector.class)
class ServerTickSamplerInjectorTest {
	private static final String SERVER = "net.minecraft.server.MinecraftServer";
	private static final String SERVER_INTERNAL = "net/minecraft/server/MinecraftServer";
	/** Two returns, as the real one has several: the hook must run on the early one too. */
	private static final String SERVER_SOURCE = """
			package net.minecraft.server;
			import java.util.function.BooleanSupplier;
			public class MinecraftServer {
				static int bodies;
				static int fullTicks;
				public void tickServer(BooleanSupplier hasTimeLeft) {
					bodies++;
					if (!hasTimeLeft.getAsBoolean()) return;
					fullTicks++;
				}
			}
			""";
	/** A gap well inside the sampler's pause threshold (2s), so the second tick is counted rather than discarded. */
	private static final long GAP_MILLIS = 5;

	@BeforeEach
	@AfterEach
	void clear() throws Throwable {
		System.clearProperty(KernelServerTicks.SWITCH);
		InjectorExecution.invokeStatic(KernelServerTicks.class, "reset");
	}

	@Test void theTransformedTickFeedsTheSampler(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, Map.of(SERVER, SERVER_SOURCE));
		Class<?> server = loadTransformed(original, new ServerTickSamplerInjector());

		Object instance = InjectorExecution.construct(server);
		tick(instance, () -> true);
		assertEquals(0L, sampled(), "the first tick only arms the sampler; it has nothing to subtract from");
		Thread.sleep(GAP_MILLIS);
		tick(instance, () -> false);
		assertEquals(1L, sampled(), "the second tick start must be measured, even on the method's early return");
		assertEquals(2, InjectorExecution.getStatic(server, "bodies"), "the original body must still run every tick");
		assertEquals(1, InjectorExecution.getStatic(server, "fullTicks"));
		assertTrue(KernelServerTicks.summary().startsWith("[Forbric/Tick] 1 tick(s): "), KernelServerTicks.summary());
	}

	@Test void theUntransformedTickMeasuresNothing(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, Map.of(SERVER, SERVER_SOURCE));
		Class<?> server = InjectorExecution.load(original).loadClass(SERVER);

		Object instance = InjectorExecution.construct(server);
		tick(instance, () -> true);
		Thread.sleep(GAP_MILLIS);
		tick(instance, () -> true);
		assertEquals(2, InjectorExecution.getStatic(server, "bodies"));
		assertEquals(0L, sampled(), "without the injector nothing calls the sampler, so the measurement above is its doing");
	}

	@Test void theHookBindsToTheNameWhateverTheDescriptor(@TempDir Path work) throws Throwable {
		// The injector's stated reason for matching by name alone: tickServer has changed shape before.
		Map<String, byte[]> original = InjectorExecution.compile(work, Map.of(SERVER, """
				package net.minecraft.server;
				public class MinecraftServer {
					static int bodies;
					protected void tickServer() {
						bodies++;
					}
				}
				"""));
		Class<?> server = loadTransformed(original, new ServerTickSamplerInjector());

		Object instance = InjectorExecution.construct(server);
		InjectorExecution.invoke(instance, "tickServer");
		Thread.sleep(GAP_MILLIS);
		InjectorExecution.invoke(instance, "tickServer");
		assertEquals(1L, sampled());
		assertEquals(2, InjectorExecution.getStatic(server, "bodies"));
	}

	@Test void aClassWithoutTheAnchorComesBackUntouched(@TempDir Path work) throws Exception {
		Map<String, byte[]> compiled = InjectorExecution.compile(work, Map.of(
				"net.minecraft.server.dedicated.DedicatedServer", """
						package net.minecraft.server.dedicated;
						public class DedicatedServer {
							public void tickServer(java.util.function.BooleanSupplier hasTimeLeft) {
							}
						}
						""",
				SERVER, """
						package net.minecraft.server;
						public class MinecraftServer {
							public void runServer() {
							}
						}
						"""));
		ServerTickSamplerInjector injector = new ServerTickSamplerInjector();

		// Another class with the same method: the injector keys on MinecraftServer and nothing else.
		byte[] other = compiled.get("net/minecraft/server/dedicated/DedicatedServer");
		assertSame(other, InjectorExecution.transform(injector, "net.minecraft.server.dedicated.DedicatedServer", other, EnvType.SERVER));

		// MinecraftServer with no tickServer: warned about, and handed back as it came.
		byte[] noTick = compiled.get(SERVER_INTERNAL);
		assertSame(noTick, InjectorExecution.transform(injector, SERVER, noTick, EnvType.SERVER));
	}

	@Test void switchedOffItNeitherEditsNorMeasures(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, Map.of(SERVER, SERVER_SOURCE));
		// Transformed while the switch is on, as a class loaded before the switch is read would be.
		Class<?> server = loadTransformed(original, new ServerTickSamplerInjector());

		System.setProperty(KernelServerTicks.SWITCH, "off");
		InjectorExecution.invokeStatic(KernelServerTicks.class, "reset");
		assertFalse(KernelServerTicks.enabled());

		ServerTickSamplerInjector off = new ServerTickSamplerInjector();
		AnchorSet anchors = off.anchors();
		assertEquals(List.of(), anchors.anchors(), "an injector that is off must not demand its anchor");
		assertEquals("the tick sampler is switched off", anchors.scanNote());
		byte[] bytes = original.get(SERVER_INTERNAL);
		assertSame(bytes, InjectorExecution.transform(off, SERVER, bytes, EnvType.SERVER));

		// The hook already in a class must go quiet too: off means no measurement, not only no new edits.
		Object instance = InjectorExecution.construct(server);
		tick(instance, () -> true);
		Thread.sleep(GAP_MILLIS);
		tick(instance, () -> true);
		assertEquals(2, InjectorExecution.getStatic(server, "bodies"));
		assertEquals(0L, sampled());
	}

	@Test void switchedOnItDemandsMinecraftServer() {
		AnchorSet anchors = new ServerTickSamplerInjector().anchors();
		assertEquals(1, anchors.anchors().size(), anchors.toString());
		assertEquals(SERVER, anchors.anchors().get(0).binaryName());
		assertEquals(AnchorSet.Severity.REQUIRED, anchors.anchors().get(0).severity());
	}

	/** Transforms the stand-in, checks the edit verifies, and defines it beside the classes it was compiled with. */
	private static Class<?> loadTransformed(Map<String, byte[]> original, ClassTransformer injector) throws ClassNotFoundException {
		byte[] bytes = original.get(SERVER_INTERNAL);
		byte[] transformed = InjectorExecution.transform(injector, SERVER, bytes, EnvType.SERVER);
		assertNotSame(bytes, transformed, "the injector did not touch MinecraftServer");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(SERVER_INTERNAL, transformed);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(transformed, loader));
		Class<?> server = loader.loadClass(SERVER);
		assertSame(loader, server.getClassLoader());
		return server;
	}

	private static void tick(Object server, BooleanSupplier hasTimeLeft) throws Throwable {
		InjectorExecution.invoke(server, "tickServer", hasTimeLeft);
	}

	private static long sampled() throws Throwable {
		return (long) InjectorExecution.invokeStatic(KernelServerTicks.class, "sampled");
	}
}
