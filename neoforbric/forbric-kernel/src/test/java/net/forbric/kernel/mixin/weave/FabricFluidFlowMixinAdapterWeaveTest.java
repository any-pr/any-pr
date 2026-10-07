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
import net.forbric.kernel.mixin.FabricFluidFlowMixinAdapter;

/**
 * {@code FabricFluidFlowMixinAdapter} through the real weave: fabric-block-api's fluid-flow veto guards the three paths
 * the merged {@code LiquidBlock} schedules fluid ticks on.
 *
 * <p>The fixture is laid out like the merged base: placing asks MinecraftForge's interaction registry, a neighbour
 * update NeoForge's, a shape update schedules directly, and vanilla's {@code shouldSpreadLiquid} — where the guest
 * veto is injected — is still declared and never called. A listener denies one position. With the adapter the veto
 * wraps all three: at the denied position no tick is scheduled and no native check runs, at the allowed one every path
 * behaves natively. With {@code -Dforbric.fabricFluidFlow=off} the injector binds cleanly to the orphan, so nothing is
 * reported, and the denied position floods exactly like the allowed one.
 */
class FabricFluidFlowMixinAdapterWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/fabricfluidflow");
	private static final String CONFIG = "fabricfluidflow.mixins.json";
	private static final String MOD = "fabricfluidflow";
	private static final String LIQUID = "net/minecraft/world/level/block/LiquidBlock";
	private static final String VETOED = "allowed:ticks=1,1,1,checks=2 denied:ticks=0,0,0,checks=0";
	private static final String FLOODS = "allowed:ticks=1,1,1,checks=2 denied:ticks=1,1,1,checks=2";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result adapted;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, MOD, sources(), Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		adapted = run("adapted", "on");
		off = run("off", "off");
	}

	@Test void theVetoGuardsEveryPathThatSchedulesFlow() throws Exception {
		assertTrue(adaptedHolds(adapted), adapted.describe() + "\nfindings: " + adapted.findings());
		assertTrue(adapted.printed("[Forbric/FluidFlow] Fabric's original ALLOW callback now guards both carrier interactions"),
				adapted.describe());
		assertTrue(WeaveHarness.hasMergedMethod(adapted.defined(LIQUID)), adapted.describe());
		WeaveHarness.assertWovenAndVerified(adapted, LIQUID, fixture);
	}

	@Test void withTheSwitchOffTheVetoBindsToTheOrphanAndNeverRuns() throws Exception {
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		assertFalse(off.printed("[Forbric/FluidFlow]"), off.describe());
		assertTrue(WeaveHarness.hasMergedMethod(off.defined(LIQUID)), off.describe());
		WeaveHarness.assertWovenAndVerified(off, LIQUID, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryAssertion() {
		assertTrue(adaptedHolds(adapted) && !adaptedHolds(off), "adapted predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(adapted), "control predicate does not separate the runs");
	}

	private static boolean adaptedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + VETOED) && losses(run).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + FLOODS) && losses(run).isEmpty();
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.modId().equals(MOD) && f.required()).toList();
	}

	private static WeaveHarness.Result run(String label, String adapter) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.fabricfluidflow.Probe", "run", Map.of(FabricFluidFlowMixinAdapter.PROPERTY, adapter));
	}

	private static List<Path> sources() throws Exception {
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
	}
}
