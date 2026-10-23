package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.mixin.MixinSubtypeOwnerRetarget;

/**
 * {@code MixinSubtypeOwnerRetarget}, woven by the real pipeline: an {@code @Inject} a mod compiled against vanilla's
 * {@code Decoder.parse} call, on a merged body that makes the same call through {@code Codec} (lithostitched's Fabric
 * predicate check on {@code RegistryLoadTask.PendingRegistration.loadFromResource}, in miniature).
 *
 * <p>Each of {@code EntryLoader}'s three methods returns a trace: "parse" per decode, "gate" when the guest handler
 * ran. The handler is written once per method against {@code Decoder.parse}:
 * <ul>
 *   <li>loadMerged — one call through Codec. Moved, so the gate runs before the decode; with the switch off it never
 *       runs and the audit names it a confirmed, required loss;</li>
 *   <li>loadMixed — vanilla's Decoder call is still there beside a Codec one. Left as written: Mixin binds the
 *       Decoder call natively, in both runs;</li>
 *   <li>loadTwice — two calls through Codec. Left as written (which one was meant is not knowable), so in both runs
 *       the gate never runs and the loss is reported rather than hidden.</li>
 * </ul>
 */
class MixinSubtypeOwnerRetargetWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/subtypeowner");
	private static final String CONFIG = "subtypeowner.mixins.json";
	private static final String MOD = "subtypeowner";
	private static final String TARGET = "net/minecraft/subtypeowner/EntryLoader";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result moved;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, "subtypeowner", List.of(
				SOURCES.resolve("com/mojang/serialization/DataResult.java"),
				SOURCES.resolve("com/mojang/serialization/DynamicOps.java"),
				SOURCES.resolve("com/mojang/serialization/Decoder.java"),
				SOURCES.resolve("com/mojang/serialization/Codec.java"),
				SOURCES.resolve("net/minecraft/subtypeowner/EntryLoader.java"),
				SOURCES.resolve("fixture/subtypeowner/mixin/EntryLoaderMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		moved = run("moved", "on");
		off = run("off", "off");
	}

	@Test void theAnchorMovesToTheSubtypeCallAndTheHandlerRuns() throws Exception {
		assertTrue(moved.printed(WeaveHarnessMain.DONE), "the woven probe threw — " + moved.describe());
		assertTrue(movedHolds(moved), moved.describe() + "\nfindings: " + moved.findings());
		assertTrue(WeaveHarness.hasMergedMethod(moved.defined(TARGET)), moved.describe());
		WeaveHarness.assertWovenAndVerified(moved, TARGET, fixture);
	}

	@Test void withTheSwitchOffTheHandlerIsAReportedLoss() throws Exception {
		assertTrue(off.printed(WeaveHarnessMain.DONE), "the control probe threw — " + off.describe());
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		// loadMixed is still woven, so the class is defined through Mixin even with nothing moved.
		assertTrue(WeaveHarness.hasMergedMethod(off.defined(TARGET)), off.describe());
		WeaveHarness.assertWovenAndVerified(off, TARGET, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryMoveAssertion() {
		assertTrue(movedHolds(moved) && !movedHolds(off), "move predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(moved), "control predicate does not separate the runs");
	}

	private static boolean movedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " merged=gate,parse mixed=gate,parse,parse twice=parse,parse")
				&& lossesOf(run, "gateMerged").isEmpty() && lossesOf(run, "gateMixed").isEmpty()
				&& lossesOf(run, "gateTwice").size() == 1;
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " merged=parse mixed=gate,parse,parse twice=parse,parse")
				&& lossesOf(run, "gateMerged").size() == 1
				&& lossesOf(run, "gateMixed").isEmpty() && lossesOf(run, "gateTwice").size() == 1;
	}

	private static List<WeaveHarness.Finding> lossesOf(WeaveHarness.Result run, String handler) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.id().contains("#" + handler)
				&& f.modId().equals(MOD) && f.confirmedRequired()).toList();
	}

	private static WeaveHarness.Result run(String label, String stage) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"net.minecraft.subtypeowner.EntryLoader", "probe", Map.of(MixinSubtypeOwnerRetarget.PROPERTY, stage));
	}
}
