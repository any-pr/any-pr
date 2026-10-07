package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.mixin.FabricSectionCompilerMixinAdapter;

/**
 * {@code FabricSectionCompilerMixinAdapter} through the real weave, on a CLIENT run: fabric-renderer-api's chunk setup
 * and tessellation redirect move together onto the compile body that receives NeoForge's extra geometry.
 *
 * <p>The fixture's {@code SectionCompiler} (compiled with its local variable table, as the game's is) keeps vanilla's
 * four-argument {@code compile} as a stub forwarding to NeoForge's five-argument one, which walks the blocks,
 * tessellates them into the {@code startedLayers} map and adds the extra geometry. The guest pair shares a renderer
 * and an emitter between a setup {@code @Inject} (which also takes {@code startedLayers} by name) and a tessellation
 * {@code @Redirect}; both select {@code compile} by name, so Mixin binds them to the stub. With the adapter both move
 * to the live overload — the setup through a generated wrapper that takes the extra list and hands the original its
 * own arguments — and every block is emitted by Fabric's renderer, with NeoForge's geometry kept. With
 * {@code -Dforbric.fabricChunkRendering=off} both injectors find nothing in the stub and are reported, and the
 * chunk is tessellated by vanilla.
 */
class FabricSectionCompilerMixinAdapterWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/fabricsectioncompiler");
	private static final String CONFIG = "fabricsectioncompiler.mixins.json";
	private static final String MOD = "fabricsectioncompiler";
	private static final String COMPILER = "net/minecraft/client/renderer/chunk/SectionCompiler";
	private static final String FABRIC_QUADS = "quads=[fabric:stone, fabric:ctm_glass, neoforge:fluid]";
	private static final String VANILLA_QUADS = "quads=[vanilla:stone, vanilla:ctm_glass, neoforge:fluid]";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result adapted;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, MOD, sources(), Map.of(CONFIG, SOURCES.resolve(CONFIG)), List.of("-g"));
		adapted = run("adapted", Map.of(FabricSectionCompilerMixinAdapter.PROPERTY, "on"));
		off = run("off", Map.of(FabricSectionCompilerMixinAdapter.PROPERTY, "off"));
	}

	@Test void fabricsRendererEmitsEveryBlockOfTheLiveCompile() throws Exception {
		assertTrue(adaptedHolds(adapted), adapted.describe() + "\nfindings: " + adapted.findings());
		assertTrue(adapted.printed("[Forbric/Renderer] Fabric's renderer setup and block emission now run in the live chunk compile overload"),
				adapted.describe());
		assertTrue(WeaveHarness.hasMergedMethod(adapted.defined(COMPILER)), adapted.describe());
		WeaveHarness.assertWovenAndVerified(adapted, COMPILER, fixture);
	}

	@Test void withTheSwitchOffTheChunkIsTessellatedByVanillaAndBothInjectorsAreReported() throws Exception {
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		WeaveHarness.assertWovenAndVerified(off, COMPILER, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryAssertion() {
		assertTrue(adaptedHolds(adapted) && !adaptedHolds(off), "adapted predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(adapted), "control predicate does not separate the runs");
	}

	private static boolean adaptedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + FABRIC_QUADS) && losses(run).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printed(WeaveHarnessMain.DONE + " " + VANILLA_QUADS) && losses.size() == 2
				&& losses.stream().anyMatch(f -> f.id().contains("#beforeLoopCompile"))
				&& losses.stream().anyMatch(f -> f.id().contains("#tesselateBlockProxy"));
	}

	/** Every required injector of the mod the runtime audit reports, at any confidence. */
	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.required()).toList();
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.CLIENT,
				"fixture.fabricsectioncompiler.Probe", "run", properties);
	}

	private static List<Path> sources() throws Exception {
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
	}
}
