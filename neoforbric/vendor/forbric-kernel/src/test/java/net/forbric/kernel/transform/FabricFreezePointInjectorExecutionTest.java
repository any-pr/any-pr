/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import net.fabricmc.api.EnvType;

/**
 * {@link FabricFreezePointInjector}'s output, run: {@code BuiltInRegistries} carries Fabric's registry freeze point, two
 * public static no-argument hooks that {@code KernelLifecycle} finds by name and calls after the Fabric entrypoints, and
 * that do nothing until {@code FabricFreezeHookMixinAdapter} moves a Fabric mod's HEAD and TAIL injectors on
 * {@code freeze()} onto them — where as merged there was nowhere to move them, and Create Fly's TAIL injector ran in
 * {@code Bootstrap} against a frozen root (issue #52). The bootstrap freeze itself stays where vanilla has it.
 *
 * <p>The stand-in {@code BuiltInRegistries} has the two vanilla methods the hooks sit beside, a public
 * {@code bootStrap()} that creates, freezes and validates and a private {@code freeze()}, and records each step.
 */
@ExecutesInjector(FabricFreezePointInjector.class)
@ResourceLock("system-properties")
class FabricFreezePointInjectorExecutionTest {
	private static final String REGISTRIES = FabricFreezePointInjector.TARGET;
	private static final String INTERNAL = REGISTRIES.replace('.', '/');
	private static final String SIBLING = "net.minecraft.core.registries.Registries";
	private static final List<String> HOOKS = List.of(FabricFreezePointInjector.HEAD_HOOK, FabricFreezePointInjector.TAIL_HOOK);

	/** Vanilla's shape, with {@code %s} for whatever else the class already declares. */
	private static final String BUILT_IN_REGISTRIES = """
			package net.minecraft.core.registries;

			import java.util.ArrayList;
			import java.util.List;

			public class BuiltInRegistries {
				public static final List<String> runs = new ArrayList<>();

				public static void bootStrap() {
					runs.add("createContents");
					freeze();
					runs.add("validate");
				}

				private static void freeze() {
					runs.add("freeze");
				}
			%s}
			""";

	private static final Map<String, String> STAND_INS = Map.of(
			REGISTRIES, BUILT_IN_REGISTRIES.formatted(""),
			// The registry keys' class, beside it and with the same two methods: matched by name, not by shape.
			SIBLING, """
					package net.minecraft.core.registries;

					public class Registries {
						public static void bootStrap() {
							freeze();
						}

						private static void freeze() {
						}
					}
					""");

	@AfterEach void reset() {
		System.clearProperty(FabricFreezePointInjector.PROPERTY);
	}

	private static byte[] transform(byte[] bytes, EnvType side) {
		return InjectorExecution.transform(new FabricFreezePointInjector(), REGISTRIES, bytes, side);
	}

	private static List<?> runs(Class<?> registries) throws ReflectiveOperationException {
		return List.copyOf((List<?>) InjectorExecution.getStatic(registries, "runs"));
	}

	@Test void bothHooksAreCallableEmptyAndTheBootstrapFreezeIsUnchanged(@TempDir Path work) throws Throwable {
		Map<String, byte[]> original = InjectorExecution.compile(work, STAND_INS);
		byte[] hooked = transform(original.get(INTERNAL), EnvType.SERVER);
		assertNotSame(original.get(INTERNAL), hooked, "the hooks were added");
		assertArrayEquals(hooked, transform(original.get(INTERNAL), EnvType.CLIENT),
				"a client gets the same hooks: it calls them after its own client entrypoints");
		Map<String, byte[]> classes = new HashMap<>(original);
		classes.put(INTERNAL, hooked);
		ClassLoader loader = InjectorExecution.load(classes);
		assertEquals("", InjectorExecution.verify(hooked, loader));

		Class<?> registries = Class.forName(REGISTRIES, true, loader);
		for (String hook : HOOKS) {
			// The lookup KernelLifecycle.fabricFreezePoint makes: public, by name, no parameters.
			Method method = registries.getMethod(hook);
			assertEquals(Modifier.PUBLIC | Modifier.STATIC, method.getModifiers(), hook);
			assertEquals(void.class, method.getReturnType(), hook);
			assertNull(method.invoke(null), hook);
		}
		assertEquals(List.of(), runs(registries), "with no injector moved onto them the hooks do nothing, and freeze nothing");

		InjectorExecution.invokeStatic(registries, "bootStrap");
		assertEquals(List.of("createContents", "freeze", "validate"), runs(registries), "Bootstrap still freezes where vanilla does");
		assertTrue(Modifier.isPrivate(registries.getDeclaredMethod("freeze").getModifiers()), "and freeze() is still vanilla's own");
		for (String hook : HOOKS) registries.getMethod(hook).invoke(null);
		assertEquals(List.of("createContents", "freeze", "validate"), runs(registries), "nor do they after the freeze");

		Class<?> merged = Class.forName(REGISTRIES, true, InjectorExecution.load(original));
		for (String hook : HOOKS) {
			assertThrows(NoSuchMethodException.class, () -> merged.getMethod(hook),
					"premise: as merged there is no freeze point, which KernelLifecycle reads as nothing waiting for it");
		}
	}

	@Test void aSecondPassOrAClassWithOneHookAddsOnlyWhatIsMissing(@TempDir Path work) throws Throwable {
		byte[] once = transform(InjectorExecution.compile(work, STAND_INS).get(INTERNAL), EnvType.SERVER);
		assertSame(once, transform(once, EnvType.SERVER), "both hooks are there: nothing to add");

		// A class that already declares the HEAD hook, with a body of its own: only TAIL is added, and HEAD is neither
		// replaced nor doubled (a second method of the same name and descriptor would not define).
		byte[] half = InjectorExecution.compile(work, Map.of(REGISTRIES, BUILT_IN_REGISTRIES.formatted("""

					public static void %s() {
						runs.add("its own head");
					}
				""".formatted(FabricFreezePointInjector.HEAD_HOOK)))).get(INTERNAL);
		byte[] completed = transform(half, EnvType.SERVER);
		assertNotSame(half, completed, "the TAIL hook was added");
		ClassLoader loader = InjectorExecution.load(Map.of(INTERNAL, completed));
		assertEquals("", InjectorExecution.verify(completed, loader));
		Class<?> registries = Class.forName(REGISTRIES, true, loader);
		for (String hook : HOOKS) registries.getMethod(hook).invoke(null);
		assertEquals(List.of("its own head"), runs(registries));
		assertSame(completed, transform(completed, EnvType.SERVER));
	}

	@Test void switchedOffBuiltInRegistriesIsLeftAsMergedAndNothingIsPromised(@TempDir Path work) throws Exception {
		byte[] bytes = InjectorExecution.compile(work, STAND_INS).get(INTERNAL);
		FabricFreezePointInjector injector = new FabricFreezePointInjector();
		var on = injector.anchors().anchors();
		assertEquals(1, on.size());
		assertEquals(REGISTRIES, on.getFirst().binaryName());
		assertEquals(AnchorSet.Severity.REQUIRED, on.getFirst().severity());

		System.setProperty(FabricFreezePointInjector.PROPERTY, "off");
		assertSame(bytes, transform(bytes, EnvType.SERVER));
		assertSame(bytes, transform(bytes, EnvType.CLIENT));
		AnchorSet off = injector.anchors();
		assertTrue(off.anchors().isEmpty(), "a deliberate off switch is not a missed repair");
		assertFalse(off.isUndeclared());
		assertTrue(off.scanNote().contains("-D" + FabricFreezePointInjector.PROPERTY), off.scanNote());
	}

	@Test void otherClassesAreLeftAlone(@TempDir Path work) throws Exception {
		byte[] sibling = InjectorExecution.compile(work, STAND_INS).get(SIBLING.replace('.', '/'));
		assertSame(sibling, InjectorExecution.transform(new FabricFreezePointInjector(), SIBLING, sibling, EnvType.SERVER));
	}
}
