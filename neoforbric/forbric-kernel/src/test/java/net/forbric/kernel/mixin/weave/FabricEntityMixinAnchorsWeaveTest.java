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
import net.forbric.kernel.mixin.FabricEntityMixinAnchors;

/**
 * {@code FabricEntityMixinAnchors} through the real weave: two of fabric-entity-events' effect hooks keep firing on
 * the merged {@code LivingEntity}.
 *
 * <p>The fixture's entity asks NeoForge's applicability hook where vanilla called {@code canBeAffected}, and clears
 * effects one at a time through NeoForge's keep-or-remove question instead of {@code activeEffects.clear()}. With the
 * adapter BEFORE_ADD moves to the native question (an anchor move) and the clear-all veto becomes a generated wrap of
 * NeoForge's per-effect question: every add is announced, the effect a Fabric listener vetoes stays, and the one
 * NeoForge keeps stays too. With {@code -Dforbric.fabricEntityAnchors=off} both hooks bind nothing: nothing is announced
 * and only NeoForge's effect survives the clear. The audit reports the {@code @Inject} as a confirmed loss and the
 * MixinExtras wrap as a suspected one.
 *
 * <p>The adapter's other rows (elytra, sleeping, bed occupancy, balm's veto) are not woven here.
 */
class FabricEntityMixinAnchorsWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/fabricentityanchors");
	private static final String CONFIG = "fabricentityanchors.mixins.json";
	private static final String MOD = "fabricentityanchors";
	private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
	private static final String HOOKED = "announced=[speed, fabric_kept, neo_kept] removed=true left=[fabric_kept, neo_kept]";
	private static final String NATIVE_ONLY = "announced=[] removed=true left=[neo_kept]";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result adapted;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, MOD, sources(), Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		adapted = run("adapted", "on");
		off = run("off", "off");
	}

	@Test void addsAreAnnouncedAndTheFabricVetoKeepsItsEffect() throws Exception {
		assertTrue(adaptedHolds(adapted), adapted.describe() + "\nfindings: " + adapted.findings());
		assertTrue(adapted.printed("[Forbric/Mixin] restored 2 entity callback anchor(s) in "
				+ "net.fabricmc.fabric.mixin.entity.event.effect.LivingEntityMixin"), adapted.describe());
		assertTrue(WeaveHarness.hasMergedMethod(adapted.defined(LIVING)), adapted.describe());
		WeaveHarness.assertWovenAndVerified(adapted, LIVING, fixture);
	}

	@Test void withTheSwitchOffBothHooksAreReportedLosses() throws Exception {
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		assertFalse(off.printed("entity callback anchor"), off.describe());
		WeaveHarness.assertWovenAndVerified(off, LIVING, fixture);
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryAssertion() {
		assertTrue(adaptedHolds(adapted) && !adaptedHolds(off), "adapted predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(adapted), "control predicate does not separate the runs");
	}

	private static boolean adaptedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + HOOKED) && losses(run).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printed(WeaveHarnessMain.DONE + " " + NATIVE_ONLY) && losses.size() == 2
				&& losses.stream().anyMatch(f -> f.id().contains("#beforeForceAddEffect") && f.confidence().equals("CONFIRMED"))
				&& losses.stream().anyMatch(f -> f.id().contains("#allowRemoveAllEffects") && f.confidence().equals("SUSPECTED"));
	}

	/** Every required injector of the mod the runtime audit reports, at any confidence. */
	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.required()).toList();
	}

	private static WeaveHarness.Result run(String label, String adapter) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.fabricentityanchors.Probe", "run", Map.of(FabricEntityMixinAnchors.PROPERTY, adapter));
	}

	private static List<Path> sources() throws Exception {
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
	}
}
