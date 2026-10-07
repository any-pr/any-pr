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
import net.forbric.kernel.mixin.FabricClientMixinAnchors;

/**
 * {@code FabricClientMixinAnchors} through the real weave, on a CLIENT run with two guest mods: fabric-screen-api's
 * per-screen draw events and fabric-renderer-api's destroy-animation redirect, each on the merged body.
 *
 * <p>The fixture's {@code Gui} hands the top screen and its layers to NeoForge's {@code ClientHooks.extractScreen},
 * so the wrap fabric-screen-api puts on the screen's own draw call has nothing to bind to; the adapter generates a
 * wrap of NeoForge's call that fires the same events around it. The fixture's destroy animation collects parts through
 * the context-expanded {@code collectParts}; the adapter widens fabric-renderer-api's no-op redirect to it. With the
 * adapter the frame is bracketed by Fabric's listeners (with the mouse position and tick they were given) and the
 * animation submits nothing of vanilla's; with {@code -Dforbric.fabricClientAnchors=off} neither listener runs, the
 * vanilla part is submitted, and both injectors are reported. The block-entity removal row is not woven here.
 */
class FabricClientMixinAnchorsWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/fabricclientanchors");
	private static final String SCREEN_CONFIG = "fabricclientanchors-screen.mixins.json";
	private static final String RENDER_CONFIG = "fabricclientanchors-render.mixins.json";
	private static final String SCREEN_MOD = "fabricscreen";
	private static final String RENDER_MOD = "fabricrender";
	private static final String GUI = "net/minecraft/client/gui/Gui";
	private static final String RENDERER = "net/minecraft/client/renderer/LevelRenderer";
	private static final String ANCHORED = "frame=[before:inventory@3,4,0.5, layer:toast-layer, screen:inventory, after:inventory] destroy=[]";
	private static final String UNANCHORED = "frame=[layer:toast-layer, screen:inventory] destroy=[vanilla-part]";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result adapted;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, "fabricclientanchors", sources(),
				Map.of(SCREEN_CONFIG, SOURCES.resolve(SCREEN_CONFIG), RENDER_CONFIG, SOURCES.resolve(RENDER_CONFIG)));
		adapted = run("adapted", "on");
		off = run("off", "off");
	}

	@Test void theScreenEventsBracketNeoForgesDrawAndTheRedirectBinds() throws Exception {
		assertTrue(adaptedHolds(adapted), adapted.describe() + "\nfindings: " + adapted.findings());
		for (String target : List.of(GUI, RENDERER)) {
			assertTrue(WeaveHarness.hasMergedMethod(adapted.defined(target)), target + " — " + adapted.describe());
			WeaveHarness.assertWovenAndVerified(adapted, target, fixture);
		}
	}

	@Test void withTheSwitchOffNeitherHookRunsAndBothAreReported() throws Exception {
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		for (String target : List.of(GUI, RENDERER)) WeaveHarness.assertWovenAndVerified(off, target, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryAssertion() {
		assertTrue(adaptedHolds(adapted) && !adaptedHolds(off), "adapted predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(adapted), "control predicate does not separate the runs");
	}

	private static boolean adaptedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + ANCHORED) && losses(run, SCREEN_MOD).isEmpty()
				&& losses(run, RENDER_MOD).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> screen = losses(run, SCREEN_MOD), render = losses(run, RENDER_MOD);
		return run.printed(WeaveHarnessMain.DONE + " " + UNANCHORED)
				&& screen.size() == 1 && screen.get(0).id().contains("#onExtractGui")
				&& render.size() == 1 && render.get(0).id().contains("#cancelCollectParts");
	}

	/** Every required injector of the mod the runtime audit reports, at any confidence. */
	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run, String mod) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(mod)
				&& f.required()).toList();
	}

	private static WeaveHarness.Result run(String label, String adapter) throws Exception {
		return WeaveHarness.run(work, label, fixture, List.of(
				new WeaveHarness.Config(SCREEN_CONFIG, SCREEN_MOD, Ecosystem.FABRIC),
				new WeaveHarness.Config(RENDER_CONFIG, RENDER_MOD, Ecosystem.FABRIC)),
				List.of(), EnvType.CLIENT, "fixture.fabricclientanchors.Probe", "run",
				Map.of(FabricClientMixinAnchors.PROPERTY, adapter));
	}

	private static List<Path> sources() throws Exception {
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
	}
}
