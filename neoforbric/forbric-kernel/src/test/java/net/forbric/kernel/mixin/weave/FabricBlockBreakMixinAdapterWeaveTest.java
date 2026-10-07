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
import net.forbric.kernel.mixin.FabricBlockBreakMixinAdapter;

/**
 * {@code FabricBlockBreakMixinAdapter} through the real weave: fabric-api's PlayerBlockBreakEvents.AFTER fires when the
 * merged {@code destroyBlock} removes a block, with the values vanilla handed it.
 *
 * <p>The fixture's {@code destroyBlock} (compiled with its local variable table, as the game's is) never calls
 * {@code Block.destroy}: it hands removal to its own {@code removeBlock}, once on the creative path and once on the
 * survival path, and that helper calls {@code Block.destroy} exactly when it removed the block. The guest handler
 * anchors on {@code Block.destroy} and takes {@code blockEntity} and {@code adjustedState} by name. With the adapter
 * it runs behind a generated {@code @ModifyExpressionValue} on {@code removeBlock}'s answer: AFTER fires for the
 * survival chest (with its block entity and the state {@code playerWillDestroy} returned) and the creative dirt, and
 * not for the bedrock that refused. With {@code -Dforbric.fabricBlockBreak=off} AFTER never fires and the handler is a
 * reported loss. The architectury and apoli-legacy rows are not woven here.
 */
class FabricBlockBreakMixinAdapterWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/fabricblockbreak");
	private static final String CONFIG = "fabricblockbreak.mixins.json";
	private static final String MOD = "fabricblockbreak";
	private static final String GAME_MODE = "net/minecraft/server/level/ServerPlayerGameMode";
	private static final String FIRED = "broke=true,false,true after=[chest/adjusted-chest/chest-inventory, dirt/adjusted-dirt/-]";
	private static final String SILENT = "broke=true,false,true after=[]";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result adapted;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, MOD, sources(), Map.of(CONFIG, SOURCES.resolve(CONFIG)), List.of("-g"));
		adapted = run("adapted", "on");
		off = run("off", "off");
	}

	@Test void afterFiresForEveryRemovalWithTheValuesVanillaGave() throws Exception {
		assertTrue(adaptedHolds(adapted), adapted.describe() + "\nfindings: " + adapted.findings());
		assertTrue(adapted.printed("PlayerBlockBreakEvents.AFTER now fires when NeoForge's removeBlock reports the block removed"),
				adapted.describe());
		assertTrue(WeaveHarness.hasMergedMethod(adapted.defined(GAME_MODE)), adapted.describe());
		WeaveHarness.assertWovenAndVerified(adapted, GAME_MODE, fixture);
	}

	@Test void withTheSwitchOffAfterNeverFiresAndTheHandlerIsAReportedLoss() throws Exception {
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		WeaveHarness.assertWovenAndVerified(off, GAME_MODE, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryAssertion() {
		assertTrue(adaptedHolds(adapted) && !adaptedHolds(off), "adapted predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(adapted), "control predicate does not separate the runs");
	}

	private static boolean adaptedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + FIRED) && losses(run).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printed(WeaveHarnessMain.DONE + " " + SILENT) && losses.size() == 1 && losses.get(0).id().contains("#onBlockBroken");
	}

	/** Every required injector of the mod the runtime audit reports, at any confidence. */
	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.required()).toList();
	}

	private static WeaveHarness.Result run(String label, String adapter) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.fabricblockbreak.Probe", "run", Map.of(FabricBlockBreakMixinAdapter.PROPERTY, adapter));
	}

	private static List<Path> sources() throws Exception {
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
	}
}
