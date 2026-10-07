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
import net.forbric.kernel.mixin.MixinAnonymousRetarget;

/**
 * {@code MixinAnonymousRetarget} through the real weave: a mixin aimed at a renumbered anonymous class lands on the
 * class that carries the body it was compiled against.
 *
 * <p>The fixture is laid out like the merged base for {@code BoundedFloatFunction}, one of the table's
 * single-home rows: vanilla's {@code $2} body is at {@code $1}, and {@code $2} is a different class with a method
 * of the same shape. The guest mixin targets {@code $2} with a RETURN {@code @Inject} and a {@code @ModifyArg} on
 * an INVOKE point that pins {@code $2} as the owner. With the stage on, both handlers run in {@code $1}; with
 * {@code -Dforbric.mixinAnonymousDrift=off} the RETURN handler binds cleanly to the unrelated {@code $2} — the
 * silent half of the bug — and the pinned point matches nothing, a confirmed loss.
 */
class MixinAnonymousRetargetWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/anonretarget");
	private static final String CONFIG = "anonretarget.mixins.json";
	private static final String MOD = "anonretarget";
	private static final String HOME = "net/minecraft/util/BoundedFloatFunction$1";
	private static final String HEIR = "net/minecraft/util/BoundedFloatFunction$2";
	private static final String MIXIN = "fixture.anonretarget.mixin.BoundedFloatFunctionMixin";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result moved;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, MOD, List.of(
				SOURCES.resolve("net/minecraft/util/BoundedFloatFunction.java"),
				SOURCES.resolve("fixture/anonretarget/Probe.java"),
				SOURCES.resolve("fixture/anonretarget/mixin/BoundedFloatFunctionMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		moved = run("moved", "on");
		off = run("off", "off");
	}

	@Test void theMixinLandsOnTheBodyItWasCompiledAgainst() throws Exception {
		// Both handlers ran in $1: the argument one only matches once the pinned owner was dropped as well.
		assertTrue(moved.printed(WeaveHarnessMain.DONE + " vanilla-body(x+guest-arg)+guest-return|heir-body(x)"),
				moved.describe());
		assertEquals(List.of(), losses(moved), moved.describe());
		assertFalse(drifted(moved), moved.findings().toString());
		assertEquals("RESOLVED", verdict(moved), moved.findings().toString());

		assertTrue(WeaveHarness.hasMergedMethod(moved.defined(HOME)), "$1 was not woven — " + moved.describe());
		assertFalse(WeaveHarness.hasMergedMethod(moved.defined(HEIR)), "$2 was woven anyway — " + moved.describe());
		WeaveHarness.assertWovenAndVerified(moved, HOME, fixture);
	}

	@Test void withTheSwitchOffTheMixinAppliesCleanlyToUnrelatedCode() throws Exception {
		assertTrue(off.printed(WeaveHarnessMain.DONE + " vanilla-body(x)|heir-body(x)+guest-return"), off.describe());
		List<WeaveHarness.Finding> losses = losses(off);
		assertEquals(1, losses.size(), off.findings() + "\n" + off.describe());
		assertTrue(losses.get(0).id().endsWith("#anonretarget$markArgument(Ljava/lang/String;)Ljava/lang/String;@"
				+ HEIR.replace('/', '.')), losses.get(0).toString());
		assertTrue(drifted(off), off.findings().toString());
		assertEquals("SUSPECTED", verdict(off), off.findings().toString());

		assertTrue(WeaveHarness.hasMergedMethod(off.defined(HEIR)), "$2 was not woven — " + off.describe());
		assertFalse(WeaveHarness.hasMergedMethod(off.defined(HOME)), "$1 was woven anyway — " + off.describe());
		WeaveHarness.assertWovenAndVerified(off, HEIR, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryRetargetAssertion() throws Exception {
		assertTrue(movedHolds(moved) && !movedHolds(off), "the retarget predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(moved), "the control predicate does not separate the runs");
	}

	private static boolean movedHolds(WeaveHarness.Result run) throws Exception {
		return run.printed("vanilla-body(x+guest-arg)+guest-return|heir-body(x)") && losses(run).isEmpty()
				&& !drifted(run) && "RESOLVED".equals(verdict(run))
				&& WeaveHarness.hasMergedMethod(run.defined(HOME)) && !WeaveHarness.hasMergedMethod(run.defined(HEIR));
	}

	private static boolean offHolds(WeaveHarness.Result run) throws Exception {
		return run.printed("vanilla-body(x)|heir-body(x)+guest-return") && losses(run).size() == 1 && drifted(run)
				&& WeaveHarness.hasMergedMethod(run.defined(HEIR)) && !WeaveHarness.hasMergedMethod(run.defined(HOME));
	}

	private static WeaveHarness.Result run(String label, String stage) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.anonretarget.Probe", "run", Map.of(MixinAnonymousRetarget.PROPERTY, stage));
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.confirmedRequired()).toList();
	}

	/** Whether the preflight still saw the mixin aimed at a renumbered name. */
	private static boolean drifted(WeaveHarness.Result run) {
		return run.findings().stream().anyMatch(f -> f.id().equals("mixin-target-drift:" + CONFIG + ":" + MIXIN));
	}

	/** The mixin's own row after the runtime audit: RESOLVED once every injector it models is attached. */
	private static String verdict(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().equals("mixin:" + CONFIG + ":" + MIXIN))
				.map(WeaveHarness.Finding::confidence).findFirst().orElse("absent");
	}
}
