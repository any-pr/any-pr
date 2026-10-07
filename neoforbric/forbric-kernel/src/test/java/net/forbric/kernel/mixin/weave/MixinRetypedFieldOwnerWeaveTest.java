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
 * {@code MixinSubtypeOwnerRetarget}'s widened-field rule, woven by the real pipeline: an {@code @Inject} AFTER
 * {@code Monster.lookAt} in {@code RangedBowAttackGoal.tick}, on the merged shape where the field is a {@code Mob} and both
 * looks are {@code Mob.lookAt} (debugify's MC-121706 fix, in miniature).
 *
 * <p>The probe ticks three goals and returns, per goal, who looked at the target and whether the guest ran ("fix"):
 * <ul>
 *   <li>monster — the archer is a Monster. Moved, the fix runs right after its look; with the switch off it never runs
 *       and the audit names it a confirmed, required loss (the strict launch debugify could not get past);</li>
 *   <li>riding — the archer rides a Mob, which looks first through {@code Mob.lookAt} as in vanilla. The fix follows the
 *       archer's look, not the horse's: the ordinal picked the call whose receiver is the field;</li>
 *   <li>widened — the archer is a Mob that is no Monster, which only the carriers' widening admits. The guard keeps the
 *       vanilla-compiled handler out in both runs.</li>
 * </ul>
 */
class MixinRetypedFieldOwnerWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/retypedfield");
	private static final String CONFIG = "retypedfield.mixins.json";
	private static final String MOD = "retypedfield";
	private static final String TARGET = "net/minecraft/world/entity/ai/goal/RangedBowAttackGoal";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result moved;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, "retypedfield", List.of(
				SOURCES.resolve("net/minecraft/world/entity/Entity.java"),
				SOURCES.resolve("net/minecraft/world/entity/Mob.java"),
				SOURCES.resolve("net/minecraft/world/entity/PathfinderMob.java"),
				SOURCES.resolve("net/minecraft/world/entity/monster/Monster.java"),
				SOURCES.resolve("net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java"),
				SOURCES.resolve("fixture/retypedfield/mixin/RangedBowAttackGoalMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		moved = run("moved", "on");
		off = run("off", "off");
	}

	@Test void theFixRunsAfterTheArchersOwnLookAndNeverForAWidenedMob() throws Exception {
		assertTrue(moved.printed(WeaveHarnessMain.DONE), "the woven probe threw — " + moved.describe());
		assertTrue(movedHolds(moved), moved.describe() + "\nfindings: " + moved.findings());
		assertTrue(moved.printed(MOVE_LOG), "the move was not announced — " + moved.describe());
		WeaveHarness.assertWovenAndVerified(moved, TARGET, fixture);
	}

	@Test void withTheSwitchOffTheFixIsAReportedRequiredLoss() throws Exception {
		assertTrue(off.printed(WeaveHarnessMain.DONE), "the control probe threw — " + off.describe());
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryMoveAssertion() {
		assertTrue(movedHolds(moved) && !movedHolds(off), "move predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(moved), "control predicate does not separate the runs");
	}

	private static final String MOVE_LOG = "lookAtTarget → Mob.lookAt (ordinal 1, through mob, only while it holds a Monster)";

	private static boolean movedHolds(WeaveHarness.Result run) {
		return run.printedLine(WeaveHarnessMain.DONE + " monster=look:archer,fix riding=look:horse,look:archer,fix widened=look:golem")
				&& losses(run).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		return run.printedLine(WeaveHarnessMain.DONE + " monster=look:archer riding=look:horse,look:archer widened=look:golem")
				&& losses(run).size() == 1;
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.id().contains("#lookAtTarget")
				&& f.modId().equals(MOD) && f.confirmedRequired()).toList();
	}

	private static WeaveHarness.Result run(String label, String rule) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"net.minecraft.world.entity.ai.goal.RangedBowAttackGoal", "probe",
				Map.of(MixinSubtypeOwnerRetarget.RETYPED_FIELD_PROPERTY, rule));
	}
}
