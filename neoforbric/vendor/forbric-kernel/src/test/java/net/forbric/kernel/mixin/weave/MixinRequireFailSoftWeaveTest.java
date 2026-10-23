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
 * {@code MixinLocalsCapture.softenRequirements} through the real weave: a guest injector with its own
 * {@code require = 1}, in a relaxed guest config, whose call the merged body no longer makes.
 *
 * <p>The config-level relaxation cannot reach it: Mixin takes the injector's own require over {@code defaultRequire},
 * and the missed count is an {@code InjectionError}, which neither {@code required:false} nor an error handler sees.
 * Natively Mixin abandons the whole target class. Softened, the requirement is lowered to 0 after
 * {@code FinalMixinApplications} has the author's number: the class is defined, the mixin's fitting injector runs, and
 * the unattached one is the mod's confirmed, required loss. Two controls throw: the stage's own switch
 * ({@code -Dforbric.requireFailSoft=off}), and a config the kernel does not relax ({@code -Dforbric.relaxGuestMixins=off}),
 * because only a relaxed guest's injectors are softened.
 */
class MixinRequireFailSoftWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/requirefailsoft");
	private static final String CONFIG = "requirefailsoft.mixins.json";
	private static final String MOD = "requirefailsoft";
	private static final String TARGET = "net/minecraft/fixture/requirefailsoft/Furnace";
	private static final String SOFTENED = "[Forbric/Mixin] fixture.requirefailsoft.mixin.FurnaceMixin.onFuel: require 1 → 0";
	private static final String FATAL = "Critical injection failure: Callback method onFuel(Lorg/spongepowered/asm/mixin/injection/"
			+ "callback/CallbackInfoReturnable;)V in requirefailsoft.mixins.json:FurnaceMixin from mod requirefailsoft failed "
			+ "injection check, (0/1) succeeded";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result soft;
	private static WeaveHarness.Result switchedOff;
	private static WeaveHarness.Result unrelaxed;

	@BeforeAll static void weave() throws Exception {
		fixture = WeaveHarness.fixture(work, "requirefailsoft", List.of(
				SOURCES.resolve("net/minecraft/fixture/requirefailsoft/Furnace.java"),
				SOURCES.resolve("fixture/requirefailsoft/mixin/FurnaceMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		soft = run("softened", Map.of());
		switchedOff = run("require-control", Map.of("forbric.requireFailSoft", "off"));
		unrelaxed = run("unrelaxed-control", Map.of("forbric.relaxGuestMixins", "off"));
	}

	@Test void aRequiredInjectorThatCannotAttachCostsItsModNotTheClass() throws Exception {
		assertTrue(softHolds(soft), soft.describe() + "\nfindings " + soft.findings());
		assertFalse(soft.printed("absent site executed"), soft.describe());

		byte[] woven = soft.defined(TARGET);
		assertTrue(WeaveHarness.hasMergedMethod(woven), soft.describe());
		assertEquals(0, calls(woven, "onFuel"), "the unattached handler is called from the woven body");
		assertEquals(1, calls(woven, "lit"), "the fitting injector is not attached exactly once");
		WeaveHarness.assertWovenAndVerified(soft, TARGET, fixture);
	}

	/** Natively: the Error escapes the class load, nothing of the mixin runs, and the audit never sees the class. */
	@Test void withoutSofteningMixinAbandonsTheTargetClass() throws Exception {
		for (WeaveHarness.Result control : List.of(switchedOff, unrelaxed)) {
			assertTrue(hardHolds(control), control.describe() + "\nfindings " + control.findings());
			assertFalse(defines(control, TARGET), "the target was defined despite the critical failure — " + control.describe());
		}
	}

	/** Each run's predicate must fail on the others, or the controls prove nothing about the stage. */
	@Test void everyControlFlipsEverySoftenedAssertion() {
		assertTrue(softHolds(soft) && !softHolds(switchedOff) && !softHolds(unrelaxed), "softened predicate");
		assertTrue(hardHolds(switchedOff) && hardHolds(unrelaxed) && !hardHolds(soft), "control predicate");
	}

	private static boolean softHolds(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> losses = losses(run);
		return run.printed(WeaveHarnessMain.DONE + " burning|mixin") && run.printed(SOFTENED) && !run.printed(FATAL)
				&& losses.size() == 1 && losses.get(0).id().contains("#onFuel(");
	}

	private static boolean hardHolds(WeaveHarness.Result run) {
		return run.printed(WeaveHarnessMain.THREW + "org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError")
				&& run.printed("Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError: " + FATAL)
				&& !run.printed(SOFTENED) && !run.printed(WeaveHarnessMain.DONE) && losses(run).isEmpty();
	}

	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.confirmedRequired()).toList();
	}

	/** Calls from the woven burn to a merged handler whose name ends with {@code handler}. */
	private static int calls(byte[] woven, String handler) {
		ClassNode node = new ClassNode();
		new ClassReader(woven).accept(node, 0);
		MethodNode body = node.methods.stream().filter(m -> m.name.equals("burn")).findFirst().orElseThrow();
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
				"net.minecraft.fixture.requirefailsoft.Furnace", "burn", properties);
	}
}
