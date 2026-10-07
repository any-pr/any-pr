package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;

/**
 * MixinOverloadPin through the real weave, on the shape of issue #56: carpet-org-addition's name-only
 * {@code @Inject(method = "handleCustomClickAction")}, written for vanilla's {@code (Identifier, Optional)}, on a merged
 * {@code MinecraftServer} that declares NeoForge's {@code (Identifier, Optional, ServerPlayer, GameProfile)} first.
 *
 * <p>The fixture's {@code ClickServer} keeps that order and that relationship -- the carrier's overload fires its
 * event and calls vanilla's -- and the probe delivers a click the way the network handler does, through the
 * carrier's overload.
 * <ul>
 *   <li>pinned -- the selector is pinned to the two-argument overload, Mixin binds it, and the click runs the mod's
 *       hook inside vanilla's body, after the carrier's event; the mixin's other injection runs too;</li>
 *   <li>pin-off ({@code -Dforbric.mixinOverloadPin=off}, {@code -Dforbric.guestInjectorPruner.refused=off}) -- Mixin
 *       binds the bare name to the FIRST overload, rejects the descriptor ("Expected" names the four arguments), and
 *       fails the whole class: the tick hook, which has nothing to do with overloads, is gone as well, and the mod has a
 *       confirmed, required finding;</li>
 *   <li>pin-off-pruned ({@code -Dforbric.mixinOverloadPin=off} alone) -- what the kernel does with the binding the pin
 *       left: MixinFit reads it as one Mixin rejects outright, and as the fixture's class is not the game's, the miss is
 *       one on "another mod's class" -- the adapter answers the rejection there too, the pruner takes {@code onClick} out
 *       before Mixin reads the mixin, and the tick hook lives.</li>
 * </ul>
 */
class MixinOverloadOrderWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/overloadorder");
	private static final String CONFIG = "overloadorder.mixins.json";
	private static final String MOD = "overloadorder";
	private static final String TARGET = "fixture/overloadorder/ClickServer";
	private static final String MIXIN = "fixture.overloadorder.mixin.ClickServerMixin";
	private static final String VANILLA = "(Ljava/lang/String;Ljava/util/Optional;)V";
	private static final String CARRIER = "(Ljava/lang/String;Ljava/util/Optional;Ljava/lang/StringBuilder;Ljava/lang/Integer;)V";

	private static final String WOVEN = WeaveHarnessMain.DONE
			+ " [mod tick] ticked | [carrier event] [mod saw dialog] vanilla handled dialog=ok";
	private static final String UNWOVEN = WeaveHarnessMain.DONE + " ticked | [carrier event] vanilla handled dialog=ok";
	private static final String TICK_ONLY = WeaveHarnessMain.DONE + " [mod tick] ticked | [carrier event] vanilla handled dialog=ok";
	private static final String PIN_WARNING = "ClickServerMixin.onClick selects handleCustomClickAction by name";
	private static final String MIXIN_REJECTS = "Invalid descriptor on " + CONFIG + ":ClickServerMixin";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result pinned;
	private static WeaveHarness.Result off;
	private static WeaveHarness.Result offPruned;

	@BeforeAll static void weaveBothWays() throws Exception {
		fixture = WeaveHarness.fixture(work, "overloadorder", List.of(
				SOURCES.resolve("fixture/overloadorder/ClickServer.java"),
				SOURCES.resolve("fixture/overloadorder/ClickProbe.java"),
				SOURCES.resolve("fixture/overloadorder/mixin/ClickServerMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		pinned = run("pinned", Map.of("forbric.mixinOverloadPin", "on"));
		off = run("overload-pin-off", Map.of("forbric.mixinOverloadPin", "off", "forbric.guestInjectorPruner.refused", "off"));
		offPruned = run("overload-pin-off-pruned", Map.of("forbric.mixinOverloadPin", "off"));
	}

	@Test void theFixtureKeepsTheCarriersOverloadFirst() throws Exception {
		// The whole scenario is the declaration order; if javac ever reordered, both runs would bind and prove nothing.
		ClassNode node = node(off.defined(TARGET));
		List<String> order = node.methods.stream().filter(m -> m.name.equals("handleCustomClickAction")).map(m -> m.desc).toList();
		assertEquals(List.of(CARRIER, VANILLA), order);
	}

	@Test void thePinnedSelectorBindsVanillasOverloadAndTheRestOfTheMixinLives() throws Exception {
		assertTrue(wovenHolds(pinned), pinned.describe());
		assertTrue(pinned.printed(PIN_WARNING), pinned.describe());
		assertTrue(pinned.printed("Pinned to handleCustomClickAction" + VANILLA), pinned.describe());
		assertFalse(pinned.printed("InvalidInjectionException"), pinned.describe());
		assertEquals(List.of(), mixinLosses(pinned), "findings: " + pinned.findings());

		ClassNode node = node(pinned.defined(TARGET));
		assertTrue(callsHandler(method(node, VANILLA), "onClick"), "vanilla's overload does not call the hook");
		assertFalse(callsHandler(method(node, CARRIER), "onClick"), "the carrier's overload got the hook as well");
		WeaveHarness.assertWovenAndVerified(pinned, TARGET, fixture);
	}

	@Test void withThePinOffMixinBindsTheFirstOverloadAndFailsTheWholeClass() throws Exception {
		assertTrue(unwovenHolds(off), off.describe());
		assertTrue(off.printed("Expected " + CARRIER.replace(")V", "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V")),
				"Mixin's own message must name the overload it bound, the first: " + off.describe());
		List<WeaveHarness.Finding> losses = mixinLosses(off);
		assertEquals(1, losses.size(), "findings: " + off.findings());
		assertTrue(losses.get(0).confirmedRequired(), losses.toString());
	}

	/** With the pin off and the pruner on, the binding Mixin would reject is taken out and the rest of the mixin lives. */
	@Test void withThePinOffThePrunerTakesTheRejectedHookOutAndTheTickHookLives() {
		assertTrue(prunedHolds(offPruned), offPruned.describe() + "\nfindings: " + offPruned.findings());
	}

	/** The run and its controls must be told apart by the very predicates the test uses on them. */
	@Test void theOffControlFlipsEveryAssertion() {
		for (WeaveHarness.Result run : List.of(pinned, off, offPruned)) {
			assertEquals(run == pinned, wovenHolds(run), "woven predicate does not separate the runs: " + run.label());
			assertEquals(run == off, unwovenHolds(run), "unwoven predicate does not separate the runs: " + run.label());
			assertEquals(run == offPruned, prunedHolds(run), "pruned predicate does not separate the runs: " + run.label());
		}
	}

	private static boolean wovenHolds(WeaveHarness.Result run) {
		return run.printed(WOVEN) && !run.printed(MIXIN_REJECTS);
	}

	private static boolean unwovenHolds(WeaveHarness.Result run) {
		return run.printed(UNWOVEN) && run.printed(MIXIN_REJECTS) && !run.printed(PIN_WARNING);
	}

	private static boolean prunedHolds(WeaveHarness.Result run) {
		return run.printed(TICK_ONLY) && !run.printed(MIXIN_REJECTS) && !run.printed(PIN_WARNING)
				&& run.printed("[Forbric/GuestInjectorPruner] pruned " + MIXIN + ".onClick")
				&& mixinLosses(run).isEmpty() && run.findings().stream().anyMatch(f -> f.modId().equals(MOD)
						&& f.confirmedRequired() && f.id().startsWith("mixin-injector:" + CONFIG + ":" + MIXIN + "#onClick"));
	}

	private static List<WeaveHarness.Finding> mixinLosses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().equals("mixin:" + CONFIG + ":" + MIXIN)
				&& f.modId().equals(MOD)).toList();
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	private static MethodNode method(ClassNode node, String desc) {
		return node.methods.stream().filter(m -> m.name.equals("handleCustomClickAction") && m.desc.equals(desc))
				.findFirst().orElseThrow();
	}

	private static boolean callsHandler(MethodNode method, String handler) {
		return Arrays.stream(method.instructions.toArray())
				.anyMatch(insn -> insn instanceof MethodInsnNode call && call.name.endsWith(handler));
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.overloadorder.ClickProbe", "click", properties);
	}
}
