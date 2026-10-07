package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import net.forbric.kernel.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** The ItemStack.useOn and onPlaceItemIntoWorld repairs, on the real classes of the NeoForge-patched base. */
@ResourceLock("system-properties")
class ItemUseOnInjectorTest {
	private static final Path STAGED = Path.of(System.getProperty("forbric.stagedRoot", "../forbric-loader/run"));
	private static final Path BASE = STAGED.resolve("neoforge-base/patched-mc-neoforge-26.2.jar");
	private static final Path NEO_PATCHED = STAGED.resolve("neoforge-patched/patched-mc-neoforge-26.2.jar");
	private static final Path VANILLA = TestFixtures.vanillaJar();
	private static final String STACK = "net/minecraft/world/item/ItemStack";
	private static final Path NEO_RUNTIME = STAGED.resolve("neoforge-runtime/neoforge-runtime.jar");
	private static final String NEO_HOOKS = "net/neoforged/neoforge/common/CommonHooks";

	@AfterEach void reset() { System.clearProperty(ItemUseOnInjector.PROPERTY); }

	@Test void theServersCallGoesThroughTheStackTheItemWasReadFrom() throws Exception {
		byte[] original = NativeCoremodParityTest.read(NEO_RUNTIME, NEO_HOOKS);
		byte[] out = new ItemUseOnInjector().transform(ItemUseOnInjector.NEO_HOOKS, original, null);
		MethodNode place = method(node(out), "onPlaceItemIntoWorld", ItemUseOnInjector.USE_ON_DESC);
		assertFalse(calls(place, ItemUseOnInjector.ITEM, "useOn"), "the call no longer names Item directly");
		assertTrue(calls(place, STACK, ItemUseOnInjector.BRIDGE), "it goes through the stack's static bridge");
		new Analyzer<>(new BasicVerifier()).analyze(NEO_HOOKS, place);
		assertSame(out, new ItemUseOnInjector().transform(ItemUseOnInjector.NEO_HOOKS, out, null), "a second pass changes nothing");
	}

	@Test void neoForgesAndVanillasOwnBodiesAreLeftAlone() throws Exception {
		byte[] neo = NativeCoremodParityTest.read(NEO_PATCHED, STACK);
		assertSame(neo, new ItemUseOnInjector().transform(ItemUseOnInjector.STACK, neo, null), "NeoForge's body already posts its event");
		byte[] vanilla = NativeCoremodParityTest.read(VANILLA, STACK);
		assertSame(vanilla, new ItemUseOnInjector().transform(ItemUseOnInjector.STACK, vanilla, null), "vanilla's body makes the call itself");
	}

	@Test void theSwitchLeavesEverythingAlone() throws Exception {
		System.setProperty(ItemUseOnInjector.PROPERTY, "off");
		byte[] stack = NativeCoremodParityTest.read(BASE, STACK), neo = NativeCoremodParityTest.read(NEO_RUNTIME, NEO_HOOKS);
		assertSame(stack, new ItemUseOnInjector().transform(ItemUseOnInjector.STACK, stack, null));
		assertSame(neo, new ItemUseOnInjector().transform(ItemUseOnInjector.NEO_HOOKS, neo, null));
	}

	private static boolean calls(MethodNode method, String owner, String name) {
		return Arrays.stream(method.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name));
	}

	private static ClassNode node(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node;
	}

	private static MethodNode method(ClassNode node, String name, String desc) {
		return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(() -> new AssertionError(name + desc));
	}
}
