package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

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
 * MixinRelocatedCall through the real weave: a Fabric mod's {@code @WrapOperation} of {@code Item.useOn} inside
 * {@code ItemStack.useOn}, written against vanilla, on an ItemStack whose carrier moved that call out of
 * {@code useOn} and whose kernel relay {@code forbric$useOnItem} now makes it.
 *
 * <ul>
 *   <li>relocated — the stage points the wrap at the relay, so using the item runs the guest's wrap;</li>
 *   <li>relocated-off — {@code -Dforbric.mixinRelocatedCall=off}: the wrap stays on {@code useOn}, finds no call
 *       there and never runs. Under {@code defaultRequire 1} the audit reports the unattached injector as a required
 *       loss — SUSPECTED, not CONFIRMED, because FinalMixinApplications keeps every MixinExtras miss SUSPECTED.</li>
 * </ul>
 * The relay is hand-written in the fixture (see its ItemStack): the harness installs no pre-Mixin transform chain,
 * so ItemUseOnInjector's output is given rather than produced. What is proven here is the stage, the real Mixin
 * binding the moved selector, and the woven relay running.
 */
class MixinRelocatedCallWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/relocatedcall");
	private static final String CONFIG = "relocatedcall.mixins.json";
	private static final String MOD = "relocatedcall";
	private static final String STACK = "net/minecraft/world/item/ItemStack";
	private static final String RELAY = "forbric$useOnItem";
	private static final String MOVED = "ItemStackMixin: wrapUseOn now wraps Item.useOn in " + RELAY;
	private static final String CLASS_ROW = "mixin:" + CONFIG + ":fixture.relocatedcall.mixin.ItemStackMixin";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result relocated;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveBothWays() throws Exception {
		fixture = WeaveHarness.fixture(work, "relocatedcall", List.of(
				SOURCES.resolve("net/minecraft/world/InteractionResult.java"),
				SOURCES.resolve("net/minecraft/world/item/context/UseOnContext.java"),
				SOURCES.resolve("net/minecraft/world/item/Item.java"),
				SOURCES.resolve("net/minecraft/world/item/ItemStack.java"),
				SOURCES.resolve("fixture/relocatedcall/CarrierHooks.java"),
				SOURCES.resolve("fixture/relocatedcall/Probe.java"),
				SOURCES.resolve("fixture/relocatedcall/mixin/ItemStackMixin.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		relocated = run("relocated", "on");
		off = run("relocated-off", "off");
	}

	@Test void theWrapFollowsTheCallIntoTheRelayAndRuns() throws Exception {
		assertTrue(relocated.printed(WeaveHarnessMain.DONE + " wrapped(item)"), relocated.describe());
		assertTrue(relocated.printed("[RelocatedCall] guest wrap ran"), relocated.describe());
		assertTrue(relocated.printed("[RelocatedCall] Item.useOn ran"), "the wrap must still make the original call — "
				+ relocated.describe());
		assertTrue(relocated.printed(MOVED), relocated.describe());
		// Nothing open for the mod: the mixin's own row is resolved because every injector attached.
		assertEquals(List.of(), open(relocated), relocated.describe());
		assertEquals("RESOLVED", classRow(relocated).confidence(), relocated.findings().toString());

		// The woven relay calls the merged handler instead of making the bare Item.useOn call.
		byte[] woven = relocated.defined(STACK);
		assertTrue(WeaveHarness.hasMergedMethod(woven), relocated.describe());
		assertFalse(callsItemUseOn(woven, RELAY), "the relay still makes the bare call: the wrap did not bind there");
		assertTrue(callsMergedHandler(woven, RELAY), "the relay does not reach the guest's handler");
		WeaveHarness.assertWovenAndVerified(relocated, STACK, fixture);
	}

	@Test void theSwitchLeavesTheWrapOnUseOnWhereItBindsNothing() throws Exception {
		assertTrue(off.printed(WeaveHarnessMain.DONE + " item"), off.describe());
		assertFalse(off.printed("guest wrap ran"), off.describe());
		assertTrue(off.printed("[RelocatedCall] Item.useOn ran"), off.describe());
		assertFalse(off.printed(MOVED), off.describe());
		List<WeaveHarness.Finding> losses = losses(off);
		assertEquals(1, losses.size(), "findings " + off.findings() + "\n" + off.describe());
		assertTrue(losses.get(0).id().contains("#wrapUseOn("), losses.toString());
		assertTrue(losses.get(0).detail().contains("no attachment"), losses.toString());
		WeaveHarness.Finding row = classRow(off);
		assertEquals("SUSPECTED", row.confidence(), row.toString());
		assertTrue(row.detail().contains("missing: @At(INVOKE) net.minecraft.world.item.Item.useOn in ItemStack.useOn"),
				row.toString());

		byte[] woven = off.defined(STACK);
		assertTrue(callsItemUseOn(woven, RELAY), "with the stage off nothing may touch the relay");
		assertFalse(callsMergedHandler(woven, RELAY), off.describe());
		WeaveHarness.assertWovenAndVerified(off, STACK, fixture);
	}

	/** Each run's predicate must fail on the other run, or the control proves nothing about the stage. */
	@Test void theControlFlipsEveryRelocationAssertion() throws Exception {
		assertTrue(relocatedHolds(relocated) && !relocatedHolds(off), "relocation predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(relocated), "control predicate does not separate the runs");
	}

	private static boolean relocatedHolds(WeaveHarness.Result run) throws Exception {
		byte[] woven = run.defined(STACK);
		return run.printed(WeaveHarnessMain.DONE + " wrapped(item)") && run.printed(MOVED) && open(run).isEmpty()
				&& "RESOLVED".equals(classRow(run).confidence()) && callsMergedHandler(woven, RELAY) && !callsItemUseOn(woven, RELAY);
	}

	private static boolean offHolds(WeaveHarness.Result run) throws Exception {
		byte[] woven = run.defined(STACK);
		return run.printed(WeaveHarnessMain.DONE + " item") && !run.printed("guest wrap ran") && losses(run).size() == 1
				&& "SUSPECTED".equals(classRow(run).confidence()) && callsItemUseOn(woven, RELAY) && !callsMergedHandler(woven, RELAY);
	}

	private static WeaveHarness.Result run(String label, String stage) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.relocatedcall.Probe", "use", Map.of("forbric.mixinRelocatedCall", stage));
	}

	/** The wrap reported as required and unattached. MixinExtras kinds never reach CONFIRMED in this audit. */
	private static List<WeaveHarness.Finding> losses(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().startsWith("mixin-injector:") && f.modId().equals(MOD)
				&& f.required() && "SUSPECTED".equals(f.confidence())).toList();
	}

	/** Every finding for the mod that is not resolved. */
	private static List<WeaveHarness.Finding> open(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.modId().equals(MOD) && !"RESOLVED".equals(f.confidence())).toList();
	}

	private static WeaveHarness.Finding classRow(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.id().equals(CLASS_ROW)).findFirst()
				.orElseThrow(() -> new AssertionError("no " + CLASS_ROW + " row: " + run.findings()));
	}

	private static boolean callsItemUseOn(byte[] woven, String method) {
		return calls(woven, method, call -> call.owner.equals("net/minecraft/world/item/Item") && call.name.equals("useOn"));
	}

	/** A call from {@code method} to a method Mixin merged into the class (the wrap's generated bridge or handler). */
	private static boolean callsMergedHandler(byte[] woven, String method) {
		ClassNode node = node(woven);
		List<String> merged = node.methods.stream().filter(m -> m.visibleAnnotations != null && m.visibleAnnotations.stream()
				.anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")))
				.map(m -> m.name + m.desc).toList();
		return calls(woven, method, call -> call.owner.equals(node.name) && merged.contains(call.name + call.desc));
	}

	private static boolean calls(byte[] woven, String method, java.util.function.Predicate<MethodInsnNode> which) {
		for (MethodNode m : node(woven).methods) {
			if (!m.name.equals(method)) continue;
			for (AbstractInsnNode insn : m.instructions) if (insn instanceof MethodInsnNode call && which.test(call)) return true;
		}
		return false;
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}
}
