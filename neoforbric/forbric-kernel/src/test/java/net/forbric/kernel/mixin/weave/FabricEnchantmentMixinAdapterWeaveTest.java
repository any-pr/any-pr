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
import net.forbric.kernel.transform.FabricItemContractTransformer;

/**
 * {@code FabricEnchantmentMixinAdapter} through the real weave: fabric-item-api's per-item enchanting answer is asked
 * where the merged {@code /enchant} and anvil ask NeoForge's native question.
 *
 * <p>Two of the adapter's routes, the static command handler and the instance anvil handler, whose bodies match the
 * fingerprints it pins. On the fixture the anvil's question sits in {@code createResultInternal}, which
 * {@code createResult} only calls. A Fabric item accepts {@code soulbound}, which no native rule does. With the
 * adapter both redirects move onto {@code supportsEnchantment} and ask the item: the command enchants the Fabric item
 * and the anvil makes the result. With {@code -Dforbric.fabricItemContracts=off} (FabricItemContractTransformer's
 * switch, which the adapter shares) both redirects name vanilla's {@code canEnchant}, find nothing, and are confirmed
 * losses: only native answers count. {@code sharpness}, which both items take natively, is the same in both runs.
 */
class FabricEnchantmentMixinAdapterWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/fabricenchantment");
	private static final String CONFIG = "fabricenchantment.mixins.json";
	private static final String MOD = "fabricenchantment";
	private static final String COMMAND = "net/minecraft/server/commands/EnchantCommand";
	private static final String ANVIL = "net/minecraft/world/inventory/AnvilMenu";
	private static final String ITEM_ANSWERS = "command sharpness=2 soulbound=1 anvil=soul_blade[soulbound]";
	private static final String NATIVE_ONLY = "command sharpness=2 soulbound=0 anvil=none";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result adapted;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, MOD, sources(), Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		adapted = run("adapted", "on");
		off = run("off", "off");
	}

	@Test void theCommandAndTheAnvilAskTheItem() throws Exception {
		assertTrue(adaptedHolds(adapted), adapted.describe() + "\nfindings: " + adapted.findings());
		for (String target : List.of(COMMAND, ANVIL)) {
			assertTrue(WeaveHarness.hasMergedMethod(adapted.defined(target)), target + " — " + adapted.describe());
			WeaveHarness.assertWovenAndVerified(adapted, target, fixture);
		}
	}

	@Test void withTheSwitchOffBothRedirectsAreReportedLosses() throws Exception {
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		for (String target : List.of(COMMAND, ANVIL)) WeaveHarness.assertWovenAndVerified(off, target, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryAssertion() {
		assertTrue(adaptedHolds(adapted) && !adaptedHolds(off), "adapted predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(adapted), "control predicate does not separate the runs");
	}

	private static boolean adaptedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + ITEM_ANSWERS) && losses(run).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printed(WeaveHarnessMain.DONE + " " + NATIVE_ONLY) && losses.size() == 2
				&& losses.stream().allMatch(f -> f.id().contains("callAllowEnchantingEvent"))
				&& losses.stream().anyMatch(f -> f.id().endsWith("@" + COMMAND.replace('/', '.')))
				&& losses.stream().anyMatch(f -> f.id().endsWith("@" + ANVIL.replace('/', '.')));
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.confirmedRequired()).toList();
	}

	private static WeaveHarness.Result run(String label, String adapter) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.fabricenchantment.Probe", "run", Map.of(FabricItemContractTransformer.PROPERTY, adapter));
	}

	private static List<Path> sources() throws Exception {
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
	}
}
