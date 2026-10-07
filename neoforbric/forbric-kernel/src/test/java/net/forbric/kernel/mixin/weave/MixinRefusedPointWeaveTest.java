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

/**
 * A name-only {@code @Inject} whose name binds a method its handler was not written for, where its {@code @At} finds no
 * point -- through the real weave. Mixin checks the handler's descriptor at each point it finds
 * ({@code CallbackInjector.inject(Target, InjectionNode)}), so there it injects nothing and throws nothing: the binding
 * is a miss, not a rejection, and the rest of the mixin is Mixin's to apply.
 *
 * <p>The fixture's {@code PointMixin} targets two classes. {@code PointRelay} is vanilla-shaped: {@code handle(String,
 * Optional)} calls {@code note}, where {@code onNote} hooks. In {@code PointServer} vanilla's {@code handle} is gone and
 * the name binds the carrier's four-argument overload, which makes no such call. The runs:
 * <ul>
 *   <li>kept (defaults) -- the verdict reads the server's binding as a miss whose {@code @At} may find nothing, so not a
 *       rejection: the mixin is kept PARTIAL, Mixin applies it to both classes, both tick hooks run and the relay's call
 *       is hooked, and nothing is rejected;</li>
 *   <li>left-out ({@code -Dforbric.mixinFit.rejectionPoint=off}) -- the RED control, the rule as it was: a rejection that
 *       some target binds as written cannot go alone, so the whole mixin was left out and every hook with it;</li>
 *   <li>handed-as-is ({@code rejectionPoint=off}, {@code -Dforbric.guestInjectorPruner.refused=off}) -- the old verdict's
 *       "rejection" handed to Mixin as written: Mixin applies the mixin exactly as in the kept run, no "Invalid
 *       descriptor". This is the premise, shown by Mixin itself.</li>
 * </ul>
 * {@link MixinRefusedBindingWeaveTest} is the other half: there the point is HEAD, Mixin finds it and does throw.
 */
class MixinRefusedPointWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/refusedpoint");
	private static final String CONFIG = "refusedpoint.mixins.json";
	private static final String MOD = "refusedpoint";
	private static final String MIXIN = "fixture.refusedpoint.mixin.PointMixin";

	private static final String HOOKED = WeaveHarnessMain.DONE + " [mod tick] server ticked | [carrier handled dialog] [mod tick] relay "
			+ "ticked | [mod saw relay] relay noted relay |";
	private static final String NONE = WeaveHarnessMain.DONE + " server ticked | [carrier handled dialog] relay ticked | relay noted relay |";
	private static final String MIXIN_REJECTS = "Invalid descriptor";
	private static final String LEFT_OUT = "auto-suppressing guest mixin " + MOD + " (" + CONFIG + "):PointMixin — Mixin would reject onNote outright";
	private static final String KEPT_PARTIAL = "guest mixin " + MOD + " (" + CONFIG + "):PointMixin applies only partially";
	private static final String NO_POINT = "its @At may find no point there, and Mixin rejects a handler only at a point it finds";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result kept;
	private static WeaveHarness.Result leftOut;
	private static WeaveHarness.Result handedAsIs;

	@BeforeAll static void weave() throws Exception {
		fixture = WeaveHarness.fixture(work, "refusedpoint", List.of(
				SOURCES.resolve("net/minecraft/server/PointServer.java"),
				SOURCES.resolve("net/minecraft/server/PointRelay.java"),
				SOURCES.resolve("net/minecraft/server/PointTrail.java"),
				SOURCES.resolve("net/minecraft/server/PointProbe.java"),
				SOURCES.resolve("fixture/refusedpoint/mixin/PointMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		kept = run("kept", Map.of());
		leftOut = run("left-out", Map.of("forbric.mixinFit.rejectionPoint", "off"));
		handedAsIs = run("handed-as-is", Map.of("forbric.mixinFit.rejectionPoint", "off", "forbric.guestInjectorPruner.refused", "off"));
	}

	@Test void aBindingWhoseAtFindsNoPointIsAMissAndTheMixinApplies() throws Exception {
		assertTrue(keptHolds(kept), kept.describe() + "\nfindings: " + kept.findings());
		WeaveHarness.assertWovenAndVerified(kept, "net/minecraft/server/PointServer", fixture);
		WeaveHarness.assertWovenAndVerified(kept, "net/minecraft/server/PointRelay", fixture);
	}

	/** RED control: read as a rejection, the binding took the whole mixin out, though Mixin would have applied it. */
	@Test void readAsARejectionTheWholeMixinWasLeftOut() {
		assertTrue(leftOutHolds(leftOut), leftOut.describe() + "\nfindings: " + leftOut.findings());
	}

	/** Mixin's own run: handed the mixin as written, it applies it and rejects nothing. */
	@Test void handedAsWrittenMixinRejectsNothing() {
		assertTrue(handedHolds(handedAsIs), handedAsIs.describe() + "\nfindings: " + handedAsIs.findings());
	}

	/** Each run and its controls must be told apart by the very predicates the tests above use. */
	@Test void theControlsFlipEveryAssertion() {
		for (WeaveHarness.Result run : List.of(kept, leftOut, handedAsIs)) {
			assertEquals(run == kept, keptHolds(run), run.label());
			assertEquals(run == leftOut, leftOutHolds(run), run.label());
			assertEquals(run == handedAsIs, handedHolds(run), run.label());
		}
	}

	private static boolean keptHolds(WeaveHarness.Result run) {
		return run.printedLine(HOOKED) && run.printed(KEPT_PARTIAL) && run.printed(NO_POINT) && !run.printed(LEFT_OUT)
				&& !run.printed(MIXIN_REJECTS) && !run.printed("which Mixin rejects outright") && losses(run).isEmpty();
	}

	private static boolean leftOutHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printedLine(NONE) && run.printed(LEFT_OUT) && !run.printed(MIXIN_REJECTS) && losses.size() == 1
				&& losses.get(0).id().equals("mixin:" + CONFIG + ":" + MIXIN);
	}

	private static boolean handedHolds(WeaveHarness.Result run) {
		return run.printedLine(HOOKED) && !run.printed(LEFT_OUT) && !run.printed(MIXIN_REJECTS) && !run.printed(NO_POINT)
				&& run.printed("which Mixin rejects outright") && losses(run).isEmpty();
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.modId().equals(MOD) && f.confirmedRequired()).toList();
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"net.minecraft.server.PointProbe", "handle", properties);
	}
}
