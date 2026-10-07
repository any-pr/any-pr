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
 * A name-only {@code @Inject} whose name binds a method its handler was not written for, beside an injector that binds,
 * through the real weave -- on a game class, where the adapter's verdict decides what Mixin receives.
 *
 * <p>The fixture's {@code ClickServer} declares the carrier's four-argument {@code handleCustomClickAction} before
 * vanilla's two-argument one; the mixin's {@code onClick} is written for vanilla's, its {@code onTick} for a method with no
 * overloads. The runs:
 * <ul>
 *   <li>pinned (defaults) -- MixinOverloadPin spells vanilla's overload and both hooks run: the verdict judges the pinned
 *       injector where it lands, so nothing is refused and nothing is pruned (the two do not overlap);</li>
 *   <li>pruned (pin off) -- the verdict sees the name bind the carrier's overload, which Mixin rejects outright, and the
 *       pruner takes {@code onClick} out before Mixin reads the mixin: no "Invalid descriptor", the tick hook runs, and the
 *       loss is one confirmed, required injector finding;</li>
 *   <li>rejected (pin off, {@code -Dforbric.guestInjectorPruner.refused=off}) -- the RED control: Mixin binds the FIRST
 *       method of that name although the second fits ("Expected" names the carrier's four arguments), throws, and the
 *       tick hook dies with the mixin. This is the premise of the verdict's handler-fit rule, shown by Mixin itself;</li>
 *   <li>verdict-off (pin off, {@code -Dforbric.mixinFit.handlerFit=off}) -- the same, because the verdict never saw it;</li>
 *   <li>left-out (pin off, {@code -Dforbric.guestInjectorPruner=off}) -- with the pruner off the mixin is left out whole
 *       before Mixin reads it: no "Invalid descriptor", no half-applied mixin.</li>
 * </ul>
 */
class MixinRefusedBindingWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/refusedbinding");
	private static final String CONFIG = "refusedbinding.mixins.json";
	private static final String MOD = "refusedbinding";
	private static final String TARGET = "net/minecraft/server/ClickServer";
	private static final String MIXIN = "fixture.refusedbinding.mixin.ClickServerMixin";
	private static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	private static final String CARRIER = "(Ljava/lang/String;Ljava/util/Optional;Ljava/lang/StringBuilder;Ljava/lang/Integer;)V";

	private static final String BOTH = WeaveHarnessMain.DONE + " [mod tick] ticked | [carrier event] [mod saw dialog] vanilla handled dialog=ok";
	private static final String TICK_ONLY = WeaveHarnessMain.DONE + " [mod tick] ticked | [carrier event] vanilla handled dialog=ok";
	private static final String NEITHER = WeaveHarnessMain.DONE + " ticked | [carrier event] vanilla handled dialog=ok";
	private static final String MIXIN_REJECTS = "Invalid descriptor on " + CONFIG + ":ClickServerMixin";
	private static final String PRUNED = "[Forbric/GuestInjectorPruner] pruned " + MIXIN + ".onClick";
	private static final String LEFT_OUT = "auto-suppressing guest mixin " + MOD + " (" + CONFIG + "):ClickServerMixin — Mixin would reject onClick outright";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result pinned;
	private static WeaveHarness.Result pruned;
	private static WeaveHarness.Result rejected;
	private static WeaveHarness.Result verdictOff;
	private static WeaveHarness.Result leftOut;

	@BeforeAll static void weave() throws Exception {
		fixture = WeaveHarness.fixture(work, "refusedbinding", List.of(
				SOURCES.resolve("net/minecraft/server/ClickServer.java"),
				SOURCES.resolve("net/minecraft/server/ClickProbe.java"),
				SOURCES.resolve("fixture/refusedbinding/mixin/ClickServerMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		pinned = run("pinned", Map.of());
		pruned = run("pruned", Map.of("forbric.mixinOverloadPin", "off"));
		rejected = run("rejected", Map.of("forbric.mixinOverloadPin", "off", "forbric.guestInjectorPruner.refused", "off"));
		verdictOff = run("verdict-off", Map.of("forbric.mixinOverloadPin", "off", "forbric.mixinFit.handlerFit", "off"));
		leftOut = run("left-out", Map.of("forbric.mixinOverloadPin", "off", "forbric.guestInjectorPruner", "off"));
	}

	@Test void thePinMovesWhatItCanAndNothingIsPruned() throws Exception {
		assertTrue(pinnedHolds(pinned), pinned.describe() + "\nfindings: " + pinned.findings());
		WeaveHarness.assertWovenAndVerified(pinned, TARGET, fixture);
	}

	@Test void whatThePinLeavesIsPrunedAndTheRestOfTheMixinApplies() throws Exception {
		assertTrue(prunedHolds(pruned), pruned.describe() + "\nfindings: " + pruned.findings());
		WeaveHarness.Finding loss = losses(pruned).get(0);
		assertTrue(loss.detail().contains("binds handleCustomClickAction" + CARRIER), loss.detail());
		WeaveHarness.assertWovenAndVerified(pruned, TARGET, fixture);
	}

	/** Mixin's own message is the evidence that it binds the first method of the name and does not try the second. */
	@Test void withThePrunerOffForRejectionsMixinBindsTheFirstOverloadAndFailsTheMixin() throws Exception {
		for (WeaveHarness.Result control : List.of(rejected, verdictOff)) {
			assertTrue(rejectedHolds(control), control.describe() + "\nfindings: " + control.findings());
			assertTrue(control.printed("Expected " + CARRIER.replace(")V", CI + ")V")), control.describe());
		}
	}

	@Test void withThePrunerOffTheMixinIsLeftOutWhole() throws Exception {
		assertTrue(leftOutHolds(leftOut), leftOut.describe() + "\nfindings: " + leftOut.findings());
	}

	/** Each run and its controls must be told apart by the very predicates the tests above use. */
	@Test void theControlsFlipEveryAssertion() {
		List<WeaveHarness.Result> all = List.of(pinned, pruned, rejected, verdictOff, leftOut);
		for (WeaveHarness.Result run : all) {
			assertEquals(run == pinned, pinnedHolds(run), run.label());
			assertEquals(run == pruned, prunedHolds(run), run.label());
			assertEquals(run == rejected || run == verdictOff, rejectedHolds(run), run.label());
			assertEquals(run == leftOut, leftOutHolds(run), run.label());
		}
	}

	private static boolean pinnedHolds(WeaveHarness.Result run) {
		return run.printedLine(BOTH) && !run.printed(PRUNED) && !run.printed(MIXIN_REJECTS) && losses(run).isEmpty();
	}

	private static boolean prunedHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printedLine(TICK_ONLY) && run.printed(PRUNED) && !run.printed(MIXIN_REJECTS) && !run.printed(LEFT_OUT)
				&& losses.size() == 1 && losses.get(0).id().equals("mixin-injector:" + CONFIG + ":" + MIXIN
						+ "#onClick(Ljava/lang/String;Ljava/util/Optional;" + CI + ")V");
	}

	private static boolean rejectedHolds(WeaveHarness.Result run) {
		return run.printedLine(NEITHER) && run.printed(MIXIN_REJECTS) && !run.printed(PRUNED) && !run.printed(LEFT_OUT)
				&& losses(run).stream().anyMatch(f -> f.id().equals("mixin:" + CONFIG + ":" + MIXIN)
						&& f.detail().contains("InvalidInjectionException"));
	}

	private static boolean leftOutHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printedLine(NEITHER) && run.printed(LEFT_OUT) && !run.printed(MIXIN_REJECTS) && !run.printed(PRUNED)
				&& losses.size() == 1 && losses.get(0).id().equals("mixin:" + CONFIG + ":" + MIXIN);
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.modId().equals(MOD) && f.confirmedRequired()).toList();
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"net.minecraft.server.ClickProbe", "click", properties);
	}
}
