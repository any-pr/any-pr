package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.mixin.MixinRetarget;

/**
 * MixinRetarget's R3 through the real weave, into a PIECE whose method returns its lefts: handlers that cancel
 * startSleepInBed follow vanilla's checks into NeoForge's lambda, and cancelling there still leaves the method with
 * their problem.
 *
 * <p>The fixture's {@code ServerPlayer} has the shape of the carrier-renames.txt pair
 * {@code startSleepInBed -> lambda$startSleepInBed$0 | PIECE | LEFT}: the checks in a lambda, its answer through NeoForge's
 * hook, returned when it names a problem, and only then the sleep. The mixin carries apoli-legacy's avian veto (a
 * cancellable {@code @Inject} at the respawn call whose callback a lambda sets to a left) and fabric-entity-events'
 * direction wrap (a {@code @WrapOperation} with a {@code @Cancellable} callback), and a HEAD handler that binds where it
 * is, so the mixin reads PARTIAL. The probe tries a bed as a plain player, an avian one and one no direction suits.
 * <ul>
 *   <li>fabric — both move; each veto reaches NeoForge's hook and is what startSleepInBed returns, with no respawn and no
 *       sleep, as on vanilla;</li>
 *   <li>left-exit-off — {@code -Dforbric.mixinRetarget.renameCensus.leftExit=off}: no handler that can cancel enters the
 *       piece; both are reported losses and both players sleep;</li>
 *   <li>census-off — R3 on the bytes alone, as before the census: both move, the same result;</li>
 *   <li>retarget-off — nothing moves.</li>
 * </ul>
 */
class MixinRetargetSleepPieceWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/sleeppiece");
	private static final String CONFIG = "sleeppiece.mixins.json";
	private static final String MOD = "sleeppiece";
	private static final String PLAYER = "net/minecraft/server/level/ServerPlayer";

	private static final String PLAIN = "plain[head;checks;respawn;event:null;slept;right(INSTANCE)]";
	/** Each veto leaves startSleepInBed with its problem, through NeoForge's hook, before the respawn and the sleep. */
	private static final String VETOED = PLAIN + " avian[head;checks;event:OTHER_PROBLEM;left(OTHER_PROBLEM)]"
			+ " nodirection[head;checks;event:NOT_POSSIBLE_HERE;left(NOT_POSSIBLE_HERE)]";
	/** Neither handler bound: both players respawn and sleep. */
	private static final String LOST = PLAIN + " avian[head;checks;respawn;event:null;slept;right(INSTANCE)]"
			+ " nodirection[head;checks;respawn;event:null;slept;right(INSTANCE)]";
	private static final String MOVED = "ServerPlayerMixin — startSleepInBed → lambda$startSleepInBed$0";

	@TempDir static Path work;
	private static Path fixture;
	private static final Map<String, WeaveHarness.Result> RUNS = new LinkedHashMap<>();

	@BeforeAll static void weave() throws Exception {
		fixture = WeaveHarness.fixture(work, "sleeppiece", List.of(
				SOURCES.resolve("com/mojang/datafixers/util/Either.java"),
				SOURCES.resolve("net/minecraft/core/BlockPos.java"),
				SOURCES.resolve("net/minecraft/util/Unit.java"),
				SOURCES.resolve("net/minecraft/world/level/block/state/properties/Property.java"),
				SOURCES.resolve("net/minecraft/world/level/block/state/BlockState.java"),
				SOURCES.resolve("net/minecraft/world/entity/player/Player.java"),
				SOURCES.resolve("net/minecraft/server/level/ServerPlayer.java"),
				SOURCES.resolve("net/neoforged/neoforge/event/EventHooks.java"),
				SOURCES.resolve("fixture/sleeppiece/Probe.java"),
				SOURCES.resolve("fixture/sleeppiece/mixin/ServerPlayerMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		RUNS.put("fabric", run("fabric", Map.of()));
		RUNS.put("left-exit-off", run("left-exit-off", Map.of("forbric.mixinRetarget.renameCensus.leftExit", "off")));
		RUNS.put("census-off", run("census-off", Map.of("forbric.mixinRetarget.renameCensus", "off")));
		RUNS.put("retarget-off", run("retarget-off", Map.of(MixinRetarget.PROPERTY, "off")));
	}

	@Test void aVetoInTheLambdaLeavesStartSleepInBedWithItsProblem() throws Exception {
		WeaveHarness.Result fabric = RUNS.get("fabric");
		assertTrue(vetoed(fabric), fabric.describe() + "\nfindings: " + fabric.findings());
		WeaveHarness.assertWovenAndVerified(fabric, PLAYER, fixture);
	}

	/** Without the left exit no handler that can cancel enters the piece: both vetoes are lost and both players sleep. */
	@Test void withoutTheLeftExitBothVetoesAreLost() throws Exception {
		WeaveHarness.Result off = RUNS.get("left-exit-off");
		assertTrue(lost(off), off.describe() + "\nfindings: " + off.findings());
		WeaveHarness.assertWovenAndVerified(off, PLAYER, fixture);
	}

	@Test void onTheBytesAloneTheyMoveTooAndWithTheRetargetOffNothingDoes() {
		WeaveHarness.Result census = RUNS.get("census-off"), off = RUNS.get("retarget-off");
		assertTrue(vetoed(census), census.describe() + "\nfindings: " + census.findings());
		assertTrue(lost(off), off.describe() + "\nfindings: " + off.findings());
	}

	/** The runs and their controls must be told apart by the very predicates the tests use on them. */
	@Test void theControlsFlipEveryAssertion() {
		WeaveHarness.Result fabric = RUNS.get("fabric"), off = RUNS.get("left-exit-off");
		assertTrue(vetoed(fabric) && !vetoed(off), "vetoed predicate does not separate the runs");
		assertTrue(lost(off) && !lost(fabric), "lost predicate does not separate the runs");
	}

	private static boolean vetoed(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + VETOED) && run.printed(MOVED)
				&& losses(run, "sleeppiece$avian", false) == 0 && losses(run, "sleeppiece$direction", false) == 0;
	}

	/** The wrap's loss reads SUSPECTED: an injector with sugar cannot be confirmed absent from the defined class. */
	private static boolean lost(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + LOST) && !run.printed(MOVED)
				&& losses(run, "sleeppiece$avian", true) == 1 && losses(run, "sleeppiece$direction", false) == 1;
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER, "fixture.sleeppiece.Probe", "run",
				properties);
	}

	/** Required injector losses the audit recorded for {@code handler}, only confirmed ones when {@code confirmed}. */
	private static long losses(WeaveHarness.Result run, String handler, boolean confirmed) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.id().contains("#" + handler)
				&& f.modId().equals(MOD) && f.required() && (!confirmed || f.confirmedRequired())).count();
	}
}
