/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.forbric.api.Ecosystem;
import net.forbric.api.ForeignType;
import net.forbric.kernel.transform.AnchorSet;
import net.forbric.kernel.transform.MethodBodyNeuter;
import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The neuter KernelBoot registers, as {@link KernelBoot#neuters} builds it for each side. MinecraftForge's
 * {@code FluidInteractionRegistry.canInteract} must not be on it with the fluid repair on: a MinecraftForge mod's fluid
 * rules run through that method, and a neuter there silenced them while every other test and gate stayed green.
 */
class KernelBootNeutersTest {
	private static final String REGISTRY = ForeignType.FLUID_INTERACTION_REGISTRY.binary(Ecosystem.NEOFORGE);
	private static final String CAN_INTERACT = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z";
	private static final Path FORGE_CARRIER = TestFixtures.stagedRoot().resolve("forge-runtime/forge-runtime.jar");

	@Test void withTheRepairOnNoSideNeutersMinecraftForgesFluidRegistry() {
		for (KernelBoot.Side side : KernelBoot.Side.values()) {
			assertFalse(owners(KernelBoot.neuters(side)).contains(REGISTRY), side + ": " + owners(KernelBoot.neuters(side)));
		}
	}

	@Test void withTheRepairOffEverySideNeutersItAgain() {
		for (KernelBoot.Side side : KernelBoot.Side.values()) {
			assertTrue(owners(KernelBoot.neuters(side)).contains(REGISTRY), side.toString());
		}
	}

	@Test void onTheRealRegistryTheRepairOnLeavesCanInteractWholeAndOffEmptiesIt() throws Exception {
		byte[] registry = read(FORGE_CARRIER, REGISTRY.replace('.', '/'));
		for (KernelBoot.Side side : KernelBoot.Side.values()) {
			assertSame(registry, KernelBoot.neuters(side).transform(REGISTRY, registry, null), side + ": the repair on leaves it alone");
			MethodNode emptied = canInteract(KernelBoot.neuters(side).transform(REGISTRY, registry, null));
			assertEquals(List.of(Opcodes.ICONST_0, Opcodes.IRETURN), real(emptied), side + ": off, it answers false as before the repair");
			assertTrue(real(canInteract(registry)).size() > 2, "premise: the carrier's canInteract walks its interactions");
		}
	}

	private static List<String> owners(MethodBodyNeuter neuter) {
		AnchorSet anchors = neuter.anchors();
		return anchors.anchors() == null ? List.of() : anchors.anchors().stream().map(AnchorSet.Anchor::binaryName).toList();
	}

	private static MethodNode canInteract(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node.methods.stream().filter(m -> m.name.equals("canInteract") && m.desc.equals(CAN_INTERACT)).findFirst().orElseThrow();
	}

	private static List<Integer> real(MethodNode method) {
		return java.util.Arrays.stream(method.instructions.toArray()).map(AbstractInsnNode::getOpcode).filter(op -> op >= 0).toList();
	}

	private static byte[] read(Path jar, String internalName) throws Exception {
		TestFixtures.require(Fixture.STAGED, Files.isRegularFile(jar), jar + " absent");
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry(internalName + ".class");
			assertNotNull(entry, internalName + " in " + jar);
			return zip.getInputStream(entry).readAllBytes();
		}
	}
}
