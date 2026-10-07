package net.forbric.kernel.mixin.weave;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.mixin.MixinShearsRelay;

/**
 * MixinShearsRelay through the real weave: BCLib's tag-based shears wrap of vanilla's {@code stack.is(Items.SHEARS)}
 * on a pumpkin whose merged {@code useItemOn} asks NeoForge's {@code canPerformAction(ItemAbilities.SHEARS_CARVE)}
 * instead and makes no {@code is} call at all.
 *
 * <p>The probe uses vanilla shears, a modded shears item that carries only the common shears tag, and a stick. Relayed,
 * the wrap answers the carrier's question with the carrier's answer as its original, through the kernel's real
 * {@code KernelShears.relay}: vanilla shears carve because the carrier says so, the tagged shears carve because the wrap
 * extends that answer, the stick does not. With {@code -Dforbric.shearsRelay=off} the wrap names a call the body never
 * makes: the tagged shears carve nothing, and the final audit names the unbound handler.
 */
class MixinShearsRelayWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/shearsrelay");
	private static final String CONFIG = "shearsrelay.mixins.json";
	private static final String MOD = "bclib";
	private static final String PUMPKIN = "net/minecraft/world/level/block/PumpkinBlock";
	private static final String RELAY = "net/forbric/kernel/runtime/KernelShears";
	private static final String RELAYED = WeaveHarnessMain.DONE + " shears=carved tagged=carved stick=pass";
	private static final String CARRIER_ONLY = WeaveHarnessMain.DONE + " shears=carved tagged=pass stick=pass";
	private static final String RELAY_LOG = "org.betterx.bclib.mixin.common.shears.PumpkinBlockMixin: bclib_isShears now also "
			+ "answers PumpkinBlock.useItemOn's ItemAbilities.SHEARS_CARVE check";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result relayed, off;

	@BeforeAll static void weaveBothWays() throws Exception {
		List<Path> sources;
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			sources = new ArrayList<>(walk.filter(p -> p.toString().endsWith(".java")).sorted().toList());
		}
		assertEquals(10, sources.size(), "the fixture's sources changed; update this test with it: " + sources);
		// The outer handler calls the kernel's game-side KernelShears, which ForbricClassLoader must define from a jar it
		// owns; a fresh clone has no compiled runtime source set, so the production source is compiled in here.
		sources.add(Path.of("src/runtime/java/net/forbric/kernel/runtime/KernelShears.java"));
		fixture = WeaveHarness.fixture(work, "shearsrelay", sources, Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		relayed = run("relayed", Map.of());
		off = run("relay-off", Map.of(MixinShearsRelay.PROPERTY, "off"));
	}

	@Test void aTagOnlyShearsItemCarvesThroughTheCarriersQuestion() throws Exception {
		assertTrue(relayed.printed(RELAYED), relayed.describe());
		assertTrue(relayed.printed(RELAY_LOG), relayed.findings() + "\n" + relayed.describe());
		assertTrue(relayed.printed("(moved)"), relayed.describe());
		assertEquals(List.of(), unbound(relayed), relayed.findings() + "\n" + relayed.describe());
		// The fit check reads the mixin before the relay and calls it partial; the defined class settles that.
		assertEquals("RESOLVED", fitVerdict(relayed), relayed.findings().toString());
		// The carrier call is wrapped now, and the wrap's Operation is the kernel's relay.
		assertFalse(useItemOnCalls(relayed, "canPerformAction"), relayed.describe());
		assertTrue(anyMethodCalls(relayed, RELAY, "relay"), relayed.describe());
		WeaveHarness.assertWovenAndVerified(relayed, PUMPKIN, fixture);
	}

	@Test void switchedOffTheWrapIsUnboundAndOnlyDeclaredShearsCarve() throws Exception {
		assertTrue(off.printed(CARRIER_ONLY), off.describe());
		assertFalse(off.printed(RELAY_LOG), off.describe());
		assertEquals(1, unbound(off).size(), off.findings() + "\n" + off.describe());
		assertNotEquals("RESOLVED", fitVerdict(off), off.findings().toString());
		assertTrue(useItemOnCalls(off, "canPerformAction"), off.describe());
		assertFalse(anyMethodCalls(off, RELAY, "relay"), off.describe());
		WeaveHarness.assertWovenAndVerified(off, PUMPKIN, fixture);
	}

	/** The switch is the runs' only difference, so each run's predicate must reject the other. */
	@Test void theControlFlipsEveryRelayAssertion() throws Exception {
		assertTrue(relayedHolds(relayed) && !relayedHolds(off), "the relay predicate does not separate the runs");
		assertTrue(carrierOnlyHolds(off) && !carrierOnlyHolds(relayed), "the control predicate does not separate the runs");
	}

	private static boolean relayedHolds(WeaveHarness.Result run) throws Exception {
		return run.printed(RELAYED) && run.printed(RELAY_LOG) && unbound(run).isEmpty() && "RESOLVED".equals(fitVerdict(run))
				&& !useItemOnCalls(run, "canPerformAction") && anyMethodCalls(run, RELAY, "relay");
	}

	private static boolean carrierOnlyHolds(WeaveHarness.Result run) throws Exception {
		return run.printed(CARRIER_ONLY) && !run.printed(RELAY_LOG) && unbound(run).size() == 1 && !"RESOLVED".equals(fitVerdict(run))
				&& useItemOnCalls(run, "canPerformAction") && !anyMethodCalls(run, RELAY, "relay");
	}

	/** Whether the woven useItemOn itself calls ItemStack.{@code name}, i.e. nothing wraps that call. */
	private static boolean useItemOnCalls(WeaveHarness.Result run, String name) throws Exception {
		MethodNode body = woven(run).methods.stream().filter(m -> m.name.equals("useItemOn")).findFirst().orElseThrow();
		return calls(body, "net/minecraft/world/item/ItemStack", name);
	}

	private static boolean anyMethodCalls(WeaveHarness.Result run, String owner, String name) throws Exception {
		return woven(run).methods.stream().anyMatch(m -> calls(m, owner, name));
	}

	private static boolean calls(MethodNode method, String owner, String name) {
		for (var insn : method.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
		}
		return false;
	}

	private static ClassNode woven(WeaveHarness.Result run) throws Exception {
		ClassNode node = new ClassNode();
		new ClassReader(run.defined(PUMPKIN)).accept(node, 0);
		return node;
	}

	/** The final audit's rows for the mod's required injectors that attached nowhere and are not settled either way. */
	private static List<WeaveHarness.Finding> unbound(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.modId().equals(MOD) && f.id().startsWith("mixin-injector:")
				&& f.id().contains("#bclib_isShears(") && f.required() && !"RESOLVED".equals(f.confidence())).toList();
	}

	/** The confidence of the fit check's row for the mixin, as the final audit left it. */
	private static String fitVerdict(WeaveHarness.Result run) {
		List<WeaveHarness.Finding> rows = run.findings().stream()
				.filter(f -> f.id().equals("mixin:" + CONFIG + ":org.betterx.bclib.mixin.common.shears.PumpkinBlockMixin")).toList();
		assertEquals(1, rows.size(), run.findings().toString());
		return rows.get(0).confidence();
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.shearsrelay.Probe", "probe", properties);
	}
}
