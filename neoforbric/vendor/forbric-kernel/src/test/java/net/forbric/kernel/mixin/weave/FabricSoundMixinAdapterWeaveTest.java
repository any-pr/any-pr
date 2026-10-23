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
import net.forbric.kernel.transform.FabricSoundContractTransformer;

/**
 * {@code FabricSoundMixinAdapter} through the real weave, on a CLIENT run: fabric-sound-api's stream redirect binds to
 * the call the merged {@code SoundEngine.play} makes, and the sound's own stream still wins.
 *
 * <p>The fixture's engine asks the instance ({@code SoundInstance.getStream}, whose default already dispatches to
 * Fabric's {@code getAudioStream}) and never the library call the guest redirect names. With the adapter the redirect
 * moves onto the instance call: {@code play} goes through the merged handler, which asks the instance — so the
 * custom stream is used and the handler is who asked for it. With {@code -Dforbric.fabricSoundContracts=off} the
 * redirect finds nothing and is a confirmed loss; the stream is the same, asked for by {@code play} directly. The
 * adapter's switch is {@code FabricSoundContractTransformer}'s, which writes that default; the fixture's default is
 * written already transformed, so the switch here turns off only the adapter.
 */
class FabricSoundMixinAdapterWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/fabricsound");
	private static final String CONFIG = "fabricsound.mixins.json";
	private static final String MOD = "fabricsound";
	private static final String ENGINE = "net/minecraft/client/sounds/SoundEngine";
	private static final String THROUGH_HANDLER = "STARTED custom:ambient/cave1 asked-by=merged-handler";
	private static final String FROM_PLAY = "STARTED custom:ambient/cave1 asked-by=play";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result adapted;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, MOD, sources(), Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		adapted = run("adapted", "on");
		off = run("off", "off");
	}

	@Test void theRedirectBindsToTheInstanceCallAndKeepsTheSoundsOwnStream() throws Exception {
		assertTrue(adaptedHolds(adapted), adapted.describe() + "\nfindings: " + adapted.findings());
		assertTrue(WeaveHarness.hasMergedMethod(adapted.defined(ENGINE)), adapted.describe());
		WeaveHarness.assertWovenAndVerified(adapted, ENGINE, fixture);
	}

	@Test void withTheSwitchOffTheRedirectIsAReportedLoss() throws Exception {
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		WeaveHarness.assertWovenAndVerified(off, ENGINE, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryAssertion() {
		assertTrue(adaptedHolds(adapted) && !adaptedHolds(off), "adapted predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(adapted), "control predicate does not separate the runs");
	}

	private static boolean adaptedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + THROUGH_HANDLER) && losses(run).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printed(WeaveHarnessMain.DONE + " " + FROM_PLAY) && losses.size() == 1
				&& losses.get(0).id().contains("getStream");
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.confirmedRequired()).toList();
	}

	private static WeaveHarness.Result run(String label, String adapter) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.CLIENT,
				"fixture.fabricsound.Probe", "run", Map.of(FabricSoundContractTransformer.PROPERTY, adapter));
	}

	private static List<Path> sources() throws Exception {
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
	}
}
