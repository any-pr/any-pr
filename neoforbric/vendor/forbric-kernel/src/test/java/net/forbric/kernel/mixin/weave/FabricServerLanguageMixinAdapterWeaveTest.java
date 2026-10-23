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
import net.forbric.kernel.mixin.FabricServerLanguageMixinAdapter;
import net.forbric.kernel.mixin.MixinStubRebind;

/**
 * {@code FabricServerLanguageMixinAdapter} through the real weave: fabric-resource-loader's redirect of the vanilla
 * language file read lands on the {@code parseTranslations} overload that still opens the resource.
 *
 * <p>The fixture's {@code Language} has the merged shape: the two-argument form the redirect names only delegates,
 * and the three-argument form makes the one {@code getResourceAsStream} call. With the adapter the redirect's
 * selector follows the call and the probe reads the game's own file. With {@code -Dforbric.fabricServerLanguage=off}
 * the redirect finds nothing in the delegate, the audit names it a confirmed loss, and the read falls through to
 * the class's own jar, which has no such file.
 *
 * <p>The two-argument form is a row of {@code carrier-stubs.txt}, so {@code MixinStubRebind} would move the same
 * selector on its own: both runs hold {@code -Dforbric.mixinStubRebind=off}, and they differ only in this adapter's
 * switch. A third run with every stage on shows the order in production: this adapter moves the selector first and
 * the stub rebind has nothing left to do.
 *
 * <p>Only this route is woven. The adapter's other route, {@code create} on {@code loadDefault}, is pinned by an
 * instruction fingerprint of fabric-api's own multi-line handler body.
 */
class FabricServerLanguageMixinAdapterWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/fabricserverlanguage");
	private static final String CONFIG = "fabricserverlanguage.mixins.json";
	private static final String MOD = "fabricserverlanguage";
	private static final String LANGUAGE = "net/minecraft/locale/Language";
	private static final String GAME_FILE = "{greeting=from the game's own language file}";
	private static final String JAR_MISS = "{missing=/assets/minecraft/lang/en_us.json}";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result adapted;
	private static WeaveHarness.Result off;
	private static WeaveHarness.Result everyStage;

	@BeforeAll static void weaveBoth() throws Exception {
		fixture = WeaveHarness.fixture(work, MOD, sources(), Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		adapted = run("adapted", Map.of(FabricServerLanguageMixinAdapter.PROPERTY, "on", MixinStubRebind.PROPERTY, "off"));
		off = run("off", Map.of(FabricServerLanguageMixinAdapter.PROPERTY, "off", MixinStubRebind.PROPERTY, "off"));
		everyStage = run("every-stage", Map.of());
	}

	@Test void theRedirectReadsTheGamesOwnFile() throws Exception {
		assertTrue(adaptedHolds(adapted), adapted.describe() + "\nfindings: " + adapted.findings());
		assertTrue(WeaveHarness.hasMergedMethod(adapted.defined(LANGUAGE)), adapted.describe());
		WeaveHarness.assertWovenAndVerified(adapted, LANGUAGE, fixture);
	}

	@Test void withTheSwitchOffTheRedirectIsAReportedLoss() throws Exception {
		assertTrue(offHolds(off), off.describe() + "\nfindings: " + off.findings());
		WeaveHarness.assertWovenAndVerified(off, LANGUAGE, fixture);
	}

	@Test void inProductionThisAdapterMovesTheSelectorBeforeTheStubRebindSeesIt() throws Exception {
		assertTrue(adaptedHolds(everyStage), everyStage.describe() + "\nfindings: " + everyStage.findings());
		assertFalse(everyStage.printed("readCorrectVanillaResource now targets"), everyStage.describe());
		// The control is not vacuous: with this adapter off, the stub rebind alone does move it.
		WeaveHarness.Result rebindOnly = run("rebind-only", Map.of(FabricServerLanguageMixinAdapter.PROPERTY, "off"));
		assertTrue(adaptedHolds(rebindOnly), rebindOnly.describe());
		assertTrue(rebindOnly.printed("readCorrectVanillaResource now targets net.minecraft.locale.Language.parseTranslations("
				+ "Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;Ljava/lang/String;)V"), rebindOnly.describe());
	}

	/** The run and its control must be told apart by the very predicates the test uses on them. */
	@Test void theControlFlipsEveryAssertion() {
		assertTrue(adaptedHolds(adapted) && !adaptedHolds(off), "adapted predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(adapted), "control predicate does not separate the runs");
	}

	private static boolean adaptedHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.DONE + " " + GAME_FILE) && losses(run).isEmpty();
	}

	private static boolean offHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printed(WeaveHarnessMain.DONE + " " + JAR_MISS) && losses.size() == 1
				&& losses.get(0).id().contains("readCorrectVanillaResource");
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.confirmedRequired()).toList();
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.fabricserverlanguage.Probe", "run", properties);
	}

	private static List<Path> sources() throws Exception {
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
	}
}
