package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;

/**
 * {@code MixinLocalsCapture} through the real weave: a guest {@code @Inject(locals = CAPTURE_FAILHARD)} written against
 * an unpatched body, meeting a merged body with one extra local between the two it captures.
 *
 * <p>Natively Mixin throws {@code InjectionError} for that, an Error no error handler or {@code required:false} sees,
 * and abandons the target class for every mod. Softened, only that one injection is skipped (Mixin's own warning, and
 * the audit's confirmed loss for its mod); the class is defined, the mixin's other injectors run, and a capture that
 * fits the merged body still receives the real values. The control is the stage's own switch,
 * {@code -Dforbric.localsFailSoft=off}.
 */
class MixinLocalsCaptureWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/localscapture");
	private static final String CONFIG = "localscapture.mixins.json";
	private static final String MOD = "localscapture";
	private static final String TARGET = "fixture/localscapture/BlockBreaker";
	private static final String SKIPPED = "Injection warning: LVT in " + TARGET + "::destroyBlock()Ljava/lang/String; has incompatible changes";
	private static final String FATAL = "Critical injection failure: LVT in " + TARGET + "::destroyBlock()Ljava/lang/String; has incompatible changes";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result soft;
	private static WeaveHarness.Result hard;

	@BeforeAll static void weave() throws Exception {
		fixture = WeaveHarness.fixture(work, "localscapture", List.of(
				SOURCES.resolve("fixture/localscapture/BlockBreaker.java"),
				SOURCES.resolve("fixture/localscapture/mixin/BlockBreakerMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		soft = run("softened", Map.of());
		hard = run("failhard-control", Map.of("forbric.localsFailSoft", "off"));
	}

	@Test void failSoftSkipsOnlyTheCaptureThatNoLongerFits() throws Exception {
		assertTrue(softHolds(soft), soft.describe() + "\nfindings " + soft.findings());
		// The fitting capture is the evidence that softening changed nothing for an injector that attaches.
		assertTrue(soft.printed("[LocalsCapture] fitting capture saw stone,5,broken"), soft.describe());
		assertFalse(soft.printed("mismatched capture ran"), soft.describe());

		byte[] woven = soft.defined(TARGET);
		assertTrue(WeaveHarness.hasMergedMethod(woven), soft.describe());
		assertEquals(0, calls(woven, "onDestroy"), "the mismatched handler is called from the woven body");
		assertEquals(1, calls(woven, "fits"), "the fitting capture is not attached exactly once");
		assertEquals(1, calls(woven, "tagged"), "the mixin's plain injector is not attached exactly once");
		WeaveHarness.assertWovenAndVerified(soft, TARGET, fixture);
	}

	@Test void failHardControlLosesTheWholeTargetClass() throws Exception {
		assertTrue(hardHolds(hard), hard.describe() + "\nfindings " + hard.findings());
		assertFalse(hard.printed("[LocalsCapture]"), "a handler ran in a class that was never defined — " + hard.describe());
		assertFalse(defines(hard, TARGET), "the target class was defined despite the critical injection failure — " + hard.describe());
	}

	/** Each run's predicate must fail on the other run, or the control proves nothing about the stage. */
	@Test void theFailHardControlFlipsEveryAssertion() {
		assertTrue(softHolds(soft) && !softHolds(hard), "the softened predicate does not separate the runs");
		assertTrue(hardHolds(hard) && !hardHolds(soft), "the control predicate does not separate the runs");
		assertNotEquals(soft.printed("fitting capture saw"), hard.printed("fitting capture saw"));
	}

	private static boolean softHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printed(WeaveHarnessMain.DONE + " stone:broken|mixin") && run.printed(SKIPPED) && !run.printed(FATAL)
				&& losses.size() == 1 && losses.get(0).id().contains("#onDestroy(");
	}

	/** Natively: the Error escapes the class load, nothing of the mixin runs, and nothing reports the mod. */
	private static boolean hardHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.THREW + "org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError")
				&& run.printed("Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError")
				&& run.printed(FATAL) && !run.printed(WeaveHarnessMain.DONE) && losses(run).isEmpty();
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.confirmedRequired()).toList();
	}

	/** Calls from the woven destroyBlock to a merged handler whose name ends with {@code handler}. */
	private static int calls(byte[] woven, String handler) {
		ClassNode node = new ClassNode();
		new ClassReader(woven).accept(node, 0);
		MethodNode body = node.methods.stream().filter(m -> m.name.equals("destroyBlock")).findFirst().orElseThrow();
		int calls = 0;
		for (AbstractInsnNode insn : body.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(TARGET) && call.name.endsWith(handler)) calls++;
		}
		return calls;
	}

	private static boolean defines(WeaveHarness.Result run, String internalName) throws IOException {
		try {
			run.defined(internalName);
			return true;
		} catch (AssertionError absent) {
			if (!String.valueOf(absent.getMessage()).contains("was not defined")) throw absent;
			return false;
		}
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.localscapture.BlockBreaker", "destroyBlock", properties);
	}
}
