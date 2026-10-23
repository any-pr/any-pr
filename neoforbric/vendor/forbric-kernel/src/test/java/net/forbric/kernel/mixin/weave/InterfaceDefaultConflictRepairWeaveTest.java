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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.transform.InterfaceDefaultConflictRepair;

/**
 * A mod's mixin adds an interface whose default the merged base already supplies through another: the post-Mixin
 * {@code InterfaceDefaultConflictRepair} must give the woven class the override the JVM demands, or the first CALL
 * throws {@code IncompatibleClassChangeError: Conflicting default methods}.
 *
 * <p>Two shapes, both woven by the real Mixin from mixins written against vanilla:
 * <ul>
 *   <li>EntityCulling's: an interface mixin puts the mod's renderer extension on {@code BlockEntityRenderer}, which
 *       already extends the base's. The repair lands on the INTERFACE, so an implementor that overrides neither
 *       ({@code CampfireRenderer}) gets the mod's default rather than either throwing or getting the base's.</li>
 *   <li>The crafting-remainder one: a mixin adds the mod's {@code FabricItem} to {@code Item}, which already has
 *       MinecraftForge's extension. {@code CHAINED} routes the stack-typed call through NeoForge's overload, so a
 *       NeoForge item's override of THAT is what answers — the plain pick would have answered the field instead.</li>
 * </ul>
 * The control is the same weave with {@code -Dforbric.defaultConflictRepair=off}: Mixin adds the same interfaces,
 * and every contested call throws. {@code Item} also branches, so a repair that wrote it back without its stack map
 * makes the probe throw {@code VerifyError} instead of answering.
 */
class InterfaceDefaultConflictRepairWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/defaultconflict");
	private static final String CONFIG = "defaultconflict.mixins.json";
	private static final String MOD = "defaultconflict";
	private static final String RENDERER = "net/minecraft/fixture/defaultconflict/BlockEntityRenderer";
	private static final String ITEM = "net/minecraft/world/item/Item";
	private static final String FABRIC_RENDER_EXT = "fixture/defaultconflict/BlockEntityRenderFabricExtension";
	private static final String FABRIC_ITEM = "fixture/defaultconflict/FabricItem";
	private static final String BOUNDS = "getRenderBoundingBox(Ljava/lang/Object;)Ljava/lang/String;";
	private static final String REMAINDER = "getCraftingRemainder(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStackTemplate;";
	private static final String NEOFORGE_REMAINDER = "getCraftingRemainder(Lnet/minecraft/world/item/ItemInstance;)Lnet/minecraft/world/item/ItemStackTemplate;";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result repaired;
	private static WeaveHarness.Result off;

	@BeforeAll static void weaveWithAndWithoutTheRepair() throws Exception {
		List<Path> sources;
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			sources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
		assertEquals(15, sources.size(), "the fixture's sources changed; update this test with it: " + sources);
		fixture = WeaveHarness.fixture(work, "defaultconflict", sources, Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		repaired = run("repaired", Map.of());
		off = run("repair-off", Map.of(InterfaceDefaultConflictRepair.SWITCH, "off"));
	}

	@Test void theRepairedWeaveAnswersEveryContestedCall() throws Exception {
		assertTrue(repaired.printed(WeaveHarnessMain.DONE + " "), "the probe itself threw — " + repaired.describe());
		// The mod's default, not the base's: a mod that adds an interface with a default added it for its behaviour.
		assertTrue(repaired.printed("renderer=fabric:campfire "), repaired.describe());
		assertTrue(repaired.printed(" item=bowl "), repaired.describe());
		// Only CHAINED reaches this: the plain pick would have asked FabricItem's default, which answers "bucket".
		assertTrue(repaired.printed(" neoforgeItem=neoforge-override:milk "), repaired.describe());
		assertTrue(repaired.printed(" frames=true"), repaired.describe());
		// The probe's own format: the repair's log line names the error it prevented.
		assertFalse(repaired.printed("IncompatibleClassChangeError["), repaired.describe());

		assertTrue(repaired.printed("[Forbric/DefaultConflict] net.minecraft.fixture.defaultconflict.BlockEntityRenderer inherits "
				+ BOUNDS), repaired.describe());
		assertTrue(repaired.printed("gave it one that delegates to fixture.defaultconflict.BlockEntityRenderFabricExtension"),
				repaired.describe());
		assertTrue(repaired.printed("[Forbric/DefaultConflict] net.minecraft.world.item.Item inherits " + REMAINDER), repaired.describe());
		assertTrue(repaired.printed("gave it one that asks " + NEOFORGE_REMAINDER), repaired.describe());

		// The bytes ForbricClassLoader defined: the override sits on the class Mixin wove, calling what the log says.
		MethodInsnNode bounds = soleCall(declared(repaired, RENDERER, BOUNDS));
		assertEquals(org.objectweb.asm.Opcodes.INVOKESPECIAL, bounds.getOpcode());
		assertEquals(FABRIC_RENDER_EXT, bounds.owner);
		assertTrue(bounds.itf);
		MethodInsnNode remainder = soleCall(declared(repaired, ITEM, REMAINDER));
		assertEquals(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, remainder.getOpcode());
		assertEquals(ITEM, remainder.owner);
		assertEquals(NEOFORGE_REMAINDER, remainder.name + remainder.desc);
		WeaveHarness.assertWovenAndVerified(repaired, RENDERER, fixture);
		WeaveHarness.assertWovenAndVerified(repaired, ITEM, fixture);
		assertTrue(modFindings(repaired).isEmpty(), repaired.findings().toString());
	}

	@Test void withTheRepairOffTheSameWeaveThrowsAtEveryContestedCall() throws Exception {
		assertTrue(off.printed(WeaveHarnessMain.DONE + " "), "the probe itself threw — " + off.describe());
		assertTrue(off.printed("renderer=IncompatibleClassChangeError[Conflicting default methods"), off.describe());
		assertTrue(off.printed(" item=IncompatibleClassChangeError[Conflicting default methods"), off.describe());
		assertTrue(off.printed(" neoforgeItem=IncompatibleClassChangeError[Conflicting default methods"), off.describe());
		// The class still loaded and verified: the conflict is raised at the call, never at definition.
		assertTrue(off.printed(" frames=true"), off.describe());
		assertFalse(off.printed("[Forbric/DefaultConflict]"), off.describe());

		// Mixin wove the same interfaces in: the switch turned off the repair, not the weave.
		assertNull(declared(off, RENDERER, BOUNDS), "nothing settled the renderer with the repair off");
		assertNull(declared(off, ITEM, REMAINDER), "nothing settled Item with the repair off");
		WeaveHarness.assertWovenAndVerified(off, RENDERER, fixture);
		WeaveHarness.assertWovenAndVerified(off, ITEM, fixture);
		assertTrue(modFindings(off).isEmpty(), off.findings().toString());
	}

	/** Both runs are woven alike; the switch is the only difference, so each run's predicate must reject the other. */
	@Test void theControlFlipsEveryRepairAssertion() throws Exception {
		for (WeaveHarness.Result run : List.of(repaired, off)) {
			assertTrue(interfaces(run, RENDERER).contains(FABRIC_RENDER_EXT), "Mixin did not weave the renderer — " + run.describe());
			assertTrue(interfaces(run, ITEM).contains(FABRIC_ITEM), "Mixin did not weave Item — " + run.describe());
		}
		assertTrue(repairHolds(repaired) && !repairHolds(off), "the repair predicate does not separate the runs");
		assertTrue(conflictHolds(off) && !conflictHolds(repaired), "the conflict predicate does not separate the runs");
	}

	private static boolean repairHolds(WeaveHarness.Result run) throws Exception {
		return run.printed("renderer=fabric:campfire ") && run.printed(" item=bowl ")
				&& run.printed(" neoforgeItem=neoforge-override:milk ") && run.printed("[Forbric/DefaultConflict]")
				&& declared(run, RENDERER, BOUNDS) != null && declared(run, ITEM, REMAINDER) != null;
	}

	private static boolean conflictHolds(WeaveHarness.Result run) throws Exception {
		return run.printed("renderer=IncompatibleClassChangeError[Conflicting default methods")
				&& run.printed(" item=IncompatibleClassChangeError[Conflicting default methods")
				&& run.printed(" neoforgeItem=IncompatibleClassChangeError[Conflicting default methods")
				&& !run.printed("[Forbric/DefaultConflict]")
				&& declared(run, RENDERER, BOUNDS) == null && declared(run, ITEM, REMAINDER) == null;
	}

	private static List<WeaveHarness.Finding> modFindings(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> MOD.equals(f.modId())).toList();
	}

	private static ClassNode node(WeaveHarness.Result run, String internalName) throws Exception {
		ClassNode node = new ClassNode();
		new ClassReader(run.defined(internalName)).accept(node, 0);
		return node;
	}

	private static List<String> interfaces(WeaveHarness.Result run, String internalName) throws Exception {
		return node(run, internalName).interfaces;
	}

	private static MethodNode declared(WeaveHarness.Result run, String internalName, String nameAndDesc) throws Exception {
		return node(run, internalName).methods.stream().filter(m -> nameAndDesc.equals(m.name + m.desc)).findFirst().orElse(null);
	}

	private static MethodInsnNode soleCall(MethodNode method) {
		assertNotNull(method, "the repair added no override");
		List<MethodInsnNode> calls = Stream.of(method.instructions.toArray())
				.filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
		assertEquals(1, calls.size(), method.name + method.desc + " " + calls);
		return calls.get(0);
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.defaultconflict.Probe", "run", properties);
	}
}
