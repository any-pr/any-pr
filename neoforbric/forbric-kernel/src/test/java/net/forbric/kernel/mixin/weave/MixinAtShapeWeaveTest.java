package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;

/**
 * {@code MixinAtShape}, woven by the real pipeline: a Fabric mod's {@code @Redirect} compiled against another Mixin
 * fork, so its class file carries {@code at=[@At(...)]} (architectury-fabric's MixinNaturalSpawner shape).
 *
 * <p>With the stage on, the one-element array is unwrapped before Mixin reads the mixin, MixinExtras' pre-apply
 * transformer sees the single {@code @At} it casts to, and the redirect runs: {@code spawn()} answers
 * {@code limit=64}. The control, {@code -Dforbric.mixinAtShape=off}, is the crash the stage exists for: MixinExtras'
 * {@code FactoryRedirectWrapperMixinTransformer} casts the list to {@code AnnotationNode}, Mixin turns that into a
 * {@code MixinTransformerError}, and the target class is never defined — on the popular set that was
 * {@code EntityTypes.<clinit>} taking the server down.
 */
class MixinAtShapeWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/atshape");
	private static final String CONFIG = "atshape.mixins.json";
	private static final String TARGET = "fixture/atshape/Spawner";
	private static final String MIXIN = "fixture/atshape/mixin/SpawnerMixin";
	private static final String FORK_REDIRECT = "org/spongepowered/asm/mixin/injection/Redirect.class";
	private static final String UNWRAPPED = "[Forbric/Mixin] fixture.atshape.mixin.SpawnerMixin: @Redirect.at was "
			+ "compiled as a one-element array";
	private static final String CAST = "java.lang.ClassCastException: class java.util.ArrayList cannot be cast to "
			+ "class org.objectweb.asm.tree.AnnotationNode";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result shaped;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveWithAndWithoutTheStage() throws Exception {
		fixture = WeaveHarness.fixture(work, "atshape", List.of(
				SOURCES.resolve("fixture/atshape/Spawner.java"),
				SOURCES.resolve("fixture/atshape/mixin/SpawnerMixin.java"),
				SOURCES.resolve("fork/org/spongepowered/asm/mixin/injection/Redirect.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		dropForkAnnotation(fixture);
		shaped = run("shaped", "on");
		off = run("shape-off", "off");
	}

	/** Without this the test proves nothing: the jar must hold the fork's shape, and only the mod's classes. */
	@Test void theFixtureCarriesTheForkShapeAndNotTheForkAnnotation() throws Exception {
		try (JarFile jar = new JarFile(fixture.toFile())) {
			assertNull(jar.getEntry(FORK_REDIRECT), "the compile-time stand-in leaked into the mod jar");
			ClassNode mixin = new ClassNode();
			try (InputStream in = jar.getInputStream(jar.getEntry(MIXIN + ".class"))) {
				new ClassReader(in).accept(mixin, 0);
			}
			List<Object> redirect = mixin.methods.stream().filter(m -> m.name.equals("raiseLimit")).findFirst()
					.orElseThrow().visibleAnnotations.get(0).values;
			Object at = redirect.get(redirect.indexOf("at") + 1);
			assertTrue(at instanceof List<?> list && list.size() == 1 && list.get(0) instanceof AnnotationNode,
					"SpawnerMixin was compiled against the real Redirect, not the fork's: at=" + at);
		}
	}

	@Test void theUnwrappedRedirectIsWovenAndRuns() throws Exception {
		assertTrue(shaped.printed(UNWRAPPED), shaped.describe());
		assertTrue(shaped.printed(WeaveHarnessMain.DONE + " limit=64"), shaped.describe());
		assertFalse(shaped.printed("ClassCastException"), shaped.describe());
		assertTrue(WeaveHarness.hasMergedMethod(shaped.defined(TARGET)), shaped.describe());
		WeaveHarness.assertWovenAndVerified(shaped, TARGET, fixture);
		assertEquals(List.of(), losses(shaped), shaped.describe());
	}

	/** RED control: the stage off is the production crash, and the target never reaches the class loader's define. */
	@Test void withTheStageOffMixinExtrasCastKillsTheTarget() {
		assertFalse(off.printed(UNWRAPPED), off.describe());
		assertTrue(off.printed(WeaveHarnessMain.THREW
				+ "org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError"), off.describe());
		assertTrue(off.printed(CAST), off.describe());
		assertTrue(off.printed("com.llamalad7.mixinextras.wrapper.factory.FactoryRedirectWrapperMixinTransformer.transform"),
				off.describe());
		assertFalse(off.printed(WeaveHarnessMain.DONE), off.describe());
		AssertionError undefined = assertThrows(AssertionError.class, () -> off.defined(TARGET));
		assertTrue(undefined.getMessage().contains("was not defined"), undefined.getMessage());
	}

	/** Each run's predicate must fail on the other run, or the control proves nothing. */
	@Test void theControlFlipsEveryShapeAssertion() {
		assertTrue(shapedHolds(shaped) && !shapedHolds(off), "shape predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(shaped), "control predicate does not separate the runs");
	}

	private static boolean shapedHolds(WeaveHarness.Result run) {
		return run.printed(UNWRAPPED) && run.printed(WeaveHarnessMain.DONE + " limit=64") && !run.printed(CAST);
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		return !run.printed(UNWRAPPED) && run.printed(WeaveHarnessMain.THREW) && run.printed(CAST)
				&& !run.printed(WeaveHarnessMain.DONE);
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:")
				&& f.modId().equals("atshape") && f.confirmedRequired()).toList();
	}

	private static WeaveHarness.Result run(String label, String shape) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, "atshape", Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.atshape.Spawner", "spawn", Map.of("forbric.mixinAtShape", shape));
	}

	/** A mod jar does not ship the annotation it was compiled against, so the fixture must not either. */
	private static void dropForkAnnotation(Path jar) throws IOException {
		Path copy = jar.resolveSibling(jar.getFileName() + ".stripped");
		try (JarFile in = new JarFile(jar.toFile()); JarOutputStream out = new JarOutputStream(Files.newOutputStream(copy))) {
			for (JarEntry entry : Collections.list(in.entries())) {
				if (entry.getName().startsWith("org/")) continue;
				out.putNextEntry(new JarEntry(entry.getName()));
				try (InputStream bytes = in.getInputStream(entry)) {
					bytes.transferTo(out);
				}
				out.closeEntry();
			}
		}
		Files.move(copy, jar, StandardCopyOption.REPLACE_EXISTING);
	}
}
