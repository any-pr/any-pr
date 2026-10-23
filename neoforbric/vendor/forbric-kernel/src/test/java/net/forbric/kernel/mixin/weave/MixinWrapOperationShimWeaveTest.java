package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
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
import net.forbric.kernel.mixin.MixinWrapOperationShim;

/**
 * MixinWrapOperationShim through the real weave: a reviewed {@code @WrapOperation} written against vanilla's
 * {@code getCloneItemStack(level, pos, includeData)}, on a body that makes only the carrier's
 * {@code getCloneItemStack(pos, level, includeData, player)}.
 *
 * <p>The probe's return value is built by the woven code, so it says what actually happened: whether the handler ran,
 * whether it received its arguments in its own order with its {@code @Local} capture, whether the argument it changed
 * reached the merged call, and whether the call site's player still did. With {@code -Dforbric.wrapOperationShim=off}
 * the wrap names a call the body never makes: the pick is the carrier's alone, and both the fit check and the final
 * audit name the unbound handler (SUSPECTED, as FinalMixinApplications keeps every unproved MixinExtras miss).
 */
class MixinWrapOperationShimWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/wrapoperationshim");
	private static final String CONFIG = "wrapoperationshim.mixins.json";
	private static final String MOD = "fabric-events-interaction-v0";
	private static final String LISTENER = "net/minecraft/server/network/ServerGamePacketListenerImpl";
	private static final String STATE = "net/minecraft/world/level/block/state/BlockState";
	private static final String RUNTIME = "net/forbric/kernel/runtime/KernelWrapOperations";

	/** level and pos in the handler's own order, its packet capture, its pos.above() and the call site's player. */
	private static final String WRAPPED = WeaveHarnessMain.DONE + " event[level=overworld pos=(1,2,3) data=true pick(1,2,3)]"
			+ " -> clone pos=(1,3,3) level=overworld data=true player=Steve";
	private static final String UNWRAPPED = WeaveHarnessMain.DONE + " clone pos=(1,2,3) level=overworld data=true player=Steve";
	private static final String SHIM_LOG = "onPickItemFromBlock now wraps getCloneItemStack(Lnet/minecraft/core/BlockPos;"
			+ "Lnet/minecraft/world/level/LevelReader;ZLnet/minecraft/world/entity/player/Player;)";
	private static final String FIT_MISS = "missing: @At(INVOKE) net.minecraft.world.level.block.state.BlockState.getCloneItemStack"
			+ " in ServerGamePacketListenerImpl.handlePickItemFromBlock";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result shim, off;

	@BeforeAll static void weaveBothWays() throws Exception {
		fixture = WeaveHarness.fixture(work, "wrapoperationshim", List.of(
				SOURCES.resolve("net/minecraft/core/BlockPos.java"),
				SOURCES.resolve("net/minecraft/world/level/LevelReader.java"),
				SOURCES.resolve("net/minecraft/world/entity/player/Player.java"),
				SOURCES.resolve("net/minecraft/world/item/ItemStack.java"),
				SOURCES.resolve("net/minecraft/world/level/block/state/BlockState.java"),
				SOURCES.resolve("net/minecraft/network/protocol/game/ServerboundPickItemFromBlockPacket.java"),
				SOURCES.resolve("net/minecraft/server/network/ServerGamePacketListenerImpl.java"),
				SOURCES.resolve("net/fabricmc/fabric/mixin/event/interaction/ServerGamePacketListenerImplMixin.java"),
				// The wrap calls the kernel's game-side KernelWrapOperations, which ForbricClassLoader must define
				// from a jar it owns. The harness owns only the fixture, and a fresh clone has no compiled runtime
				// source set (it needs the staged game jars), so the production source is compiled in here.
				Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelWrapOperations.java")),
				Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		shim = run("shim", Map.of());
		off = run("shim-off", Map.of(MixinWrapOperationShim.PROPERTY, "off"));
	}

	@Test void theReviewedWrapBindsToTheCarriersCallAndItsHandlerGetsItsOwnArguments() throws Exception {
		assertTrue(shim.printed(WRAPPED), shim.describe());
		assertTrue(shim.printed(SHIM_LOG), shim.describe());
		assertEquals(List.of(), ours(shim), shim.describe());
		assertFalse(calls(shim, STATE, "getCloneItemStack"), "handlePickItemFromBlock still makes the call itself — " + shim.describe());
		assertTrue(callsAnywhere(shim, RUNTIME, "reordered"), "no reordered Operation in the woven listener — " + shim.describe());
		assertTrue(WeaveHarness.hasMergedMethod(shim.defined(LISTENER)), shim.describe());
		WeaveHarness.assertWovenAndVerified(shim, LISTENER, fixture);
	}

	@Test void switchedOffTheWrapIsUnboundAndThePickIsTheCarriersAlone() throws Exception {
		assertTrue(off.printed(UNWRAPPED), off.describe());
		assertFalse(off.printed("event["), off.describe());
		assertFalse(off.printed(SHIM_LOG), off.describe());
		assertEquals(1, unbound(off).size(), "findings: " + off.findings() + "\n" + off.describe());
		assertEquals(1, fitMisses(off).size(), "findings: " + off.findings() + "\n" + off.describe());
		assertTrue(calls(off, STATE, "getCloneItemStack"), off.describe());
		assertFalse(callsAnywhere(off, RUNTIME, "reordered"), off.describe());
		WeaveHarness.assertWovenAndVerified(off, LISTENER, fixture);
	}

	/** The two runs must be told apart by the very predicates the tests use on them. */
	@Test void theControlFlipsEveryWrapAssertion() throws Exception {
		assertTrue(shimHolds(shim) && !shimHolds(off), "wrap predicate does not separate the runs");
		assertTrue(offHolds(off) && !offHolds(shim), "control predicate does not separate the runs");
	}

	private static boolean shimHolds(WeaveHarness.Result run) throws Exception {
		return run.printed(WRAPPED) && run.printed(SHIM_LOG) && ours(run).isEmpty() && !calls(run, STATE, "getCloneItemStack")
				&& callsAnywhere(run, RUNTIME, "reordered");
	}

	private static boolean offHolds(WeaveHarness.Result run) throws Exception {
		return run.printed(UNWRAPPED) && !run.printed(SHIM_LOG) && unbound(run).size() == 1 && fitMisses(run).size() == 1
				&& calls(run, STATE, "getCloneItemStack") && !callsAnywhere(run, RUNTIME, "reordered");
	}

	/** Whether the woven handlePickItemFromBlock itself invokes owner.name, i.e. nothing wraps that call. */
	private static boolean calls(WeaveHarness.Result run, String owner, String name) throws Exception {
		MethodNode body = woven(run).methods.stream().filter(m -> m.name.equals("handlePickItemFromBlock")).findFirst()
				.orElseThrow(() -> new AssertionError("no handlePickItemFromBlock in the woven listener"));
		return invokes(body, owner, name);
	}

	private static boolean callsAnywhere(WeaveHarness.Result run, String owner, String name) throws Exception {
		return woven(run).methods.stream().anyMatch(m -> invokes(m, owner, name));
	}

	private static boolean invokes(MethodNode method, String owner, String name) {
		for (var insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
		}
		return false;
	}

	private static ClassNode woven(WeaveHarness.Result run) throws Exception {
		ClassNode node = new ClassNode();
		new ClassReader(run.defined(LISTENER)).accept(node, 0);
		return node;
	}

	private static List<WeaveHarness.Finding> ours(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.modId().equals(MOD)).toList();
	}

	/** The final audit's row for the handler: required, and not settled either way (no RESOLVED). */
	private static List<WeaveHarness.Finding> unbound(WeaveHarness.Result run) {
		return ours(run).stream().filter(f -> f.id().startsWith("mixin-injector:") && f.id().contains("#onPickItemFromBlock(")
				&& f.required() && !"RESOLVED".equals(f.confidence())).toList();
	}

	private static List<WeaveHarness.Finding> fitMisses(WeaveHarness.Result run) {
		return ours(run).stream().filter(f -> f.id().startsWith("mixin:") && f.detail().contains(FIT_MISS)).toList();
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"net.minecraft.server.network.ServerGamePacketListenerImpl", "probe", properties);
	}
}
