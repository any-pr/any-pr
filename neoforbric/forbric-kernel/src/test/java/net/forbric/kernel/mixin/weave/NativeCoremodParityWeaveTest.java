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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.transform.NativeCoremodParity;

/**
 * NativeCoremodParity's flower-pot rule through the real weave: NeoForge's coremod routes reads of
 * {@code FlowerPotBlock.potted} through {@code getPotted()}, here after Mixin, where NeoForge runs it.
 *
 * <p>The pot's constructor stores null in the field and keeps the plant in a supplier, as NeoForge's does. A Fabric
 * mod's mixin anchors a hook on vanilla's read of the field and adds a method of its own that reads it. With the rule
 * on, Mixin still finds the vanilla read to anchor on (the rewrite comes after it), and then both that read and the
 * mod's own are rewritten: picking the pot answers the poppy and so does the mod's method. With
 * {@code -Dforbric.flowerPotRepair=off} both read the null field and throw; the hook is anchored the same either way.
 */
class NativeCoremodParityWeaveTest {
	private static final Path SOURCES = Path.of("src/test/resources/weave/coremodparity");
	private static final String CONFIG = "coremodparity.mixins.json";
	private static final String MOD = "flowerpots";
	private static final String POT = "net/minecraft/world/level/block/FlowerPotBlock";
	private static final String READS_PLANT = WeaveHarnessMain.DONE + " pick=pick:poppy describe=pot of poppy hooks=[anchor]";
	private static final String READS_NULL = WeaveHarnessMain.DONE
			+ " pick=NullPointerException describe=NullPointerException hooks=[anchor]";
	private static final String REWRITTEN = "[Forbric/Coremod] FlowerPotBlock: 2 field read(s) now go through NeoForge's getter";

	@TempDir static Path work;
	private static Path fixture;
	private static WeaveHarness.Result parity, off;

	@BeforeAll static void weaveBothWays() throws Exception {
		List<Path> sources;
		try (Stream<Path> walk = Files.walk(SOURCES)) {
			sources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
		}
		assertEquals(6, sources.size(), "the fixture's sources changed; update this test with it: " + sources);
		fixture = WeaveHarness.fixture(work, "coremodparity", sources, Map.of(CONFIG, SOURCES.resolve(CONFIG)));
		parity = run("parity", Map.of());
		off = run("flowerpot-off", Map.of(NativeCoremodParity.FLOWER_POT, "off"));
	}

	@Test void thePotAndTheModsOwnReadAnswerThePlant() throws Exception {
		assertTrue(parity.printed(READS_PLANT), parity.describe());
		assertTrue(parity.printed(REWRITTEN), parity.describe());
		assertEquals(0, fieldReads(parity), parity.describe());
		assertEquals(2, getterCalls(parity), parity.describe());
		assertEquals(List.of(), unsettled(parity), parity.findings().toString());
		WeaveHarness.assertWovenAndVerified(parity, POT, fixture);
	}

	@Test void switchedOffBothReadsHitTheNullFieldWhileTheHookStillAnchors() throws Exception {
		assertTrue(off.printed(READS_NULL), off.describe());
		assertFalse(off.printed("[Forbric/Coremod]"), off.describe());
		assertEquals(2, fieldReads(off), off.describe());
		assertEquals(0, getterCalls(off), off.describe());
		assertEquals(List.of(), unsettled(off), off.findings().toString());
		WeaveHarness.assertWovenAndVerified(off, POT, fixture);
	}

	/** The switch is the runs' only difference, so each run's predicate must reject the other. */
	@Test void theControlFlipsEveryParityAssertion() throws Exception {
		assertTrue(plantHolds(parity) && !plantHolds(off), "the parity predicate does not separate the runs");
		assertTrue(nullHolds(off) && !nullHolds(parity), "the control predicate does not separate the runs");
	}

	private static boolean plantHolds(WeaveHarness.Result run) throws Exception {
		return run.printed(READS_PLANT) && run.printed(REWRITTEN) && fieldReads(run) == 0 && getterCalls(run) == 2;
	}

	private static boolean nullHolds(WeaveHarness.Result run) throws Exception {
		return run.printed(READS_NULL) && !run.printed(REWRITTEN) && fieldReads(run) == 2 && getterCalls(run) == 0;
	}

	/** GETFIELD potted outside the constructor in the defined pot: vanilla's read and the mod's own. */
	private static long fieldReads(WeaveHarness.Result run) throws Exception {
		return woven(run).methods.stream().filter(m -> !m.name.equals("<init>")).flatMap(m -> Stream.of(m.instructions.toArray()))
				.filter(i -> i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD && f.name.equals("potted")).count();
	}

	/** Calls of getPotted() from any other method of the defined pot. */
	private static long getterCalls(WeaveHarness.Result run) throws Exception {
		return woven(run).methods.stream().filter(m -> !m.name.equals("getPotted")).flatMap(m -> Stream.of(m.instructions.toArray()))
				.filter(i -> i instanceof MethodInsnNode c && c.owner.equals(POT) && c.name.equals("getPotted")).count();
	}

	private static ClassNode woven(WeaveHarness.Result run) throws Exception {
		ClassNode node = new ClassNode();
		new ClassReader(run.defined(POT)).accept(node, 0);
		return node;
	}

	private static List<WeaveHarness.Finding> unsettled(WeaveHarness.Result run) {
		return run.findings().stream().filter(f -> f.required() && !"RESOLVED".equals(f.confidence())).toList();
	}

	private static WeaveHarness.Result run(String label, Map<String, String> properties) throws Exception {
		return WeaveHarness.run(work, label, fixture, CONFIG, MOD, Ecosystem.FABRIC, EnvType.SERVER,
				"fixture.coremodparity.Probe", "probe", properties);
	}
}
