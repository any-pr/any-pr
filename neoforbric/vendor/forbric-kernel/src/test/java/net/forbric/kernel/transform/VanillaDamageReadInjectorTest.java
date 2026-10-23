/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;

/**
 * The merged actuallyHurt reads its damage before armour again, ahead of MinecraftForge's Hurt seam — on the real
 * LivingEntity and Player, with the seams applied first as the chain applies them.
 */
@ResourceLock("system-properties")
class VanillaDamageReadInjectorTest {
	private static final Path MERGED = TestFixtures.stagedRoot()
			.resolve("neoforge-base/patched-mc-neoforge-26.2.jar");

	@AfterEach void reset() { System.clearProperty(VanillaDamageReadInjector.PROPERTY); }

	@Test void livingEntityReadsBeforeArmourAndBeforeTheForgeSeam() throws Exception {
		TestFixtures.require(Fixture.STAGED, Files.isRegularFile(MERGED), "merged base not staged: " + MERGED);
		byte[] original = NativeCoremodParityTest.read(MERGED, "net/minecraft/world/entity/LivingEntity");
		byte[] out = new VanillaDamageReadInjector().transform("net.minecraft.world.entity.LivingEntity", original, null);
		MethodNode hurt = hurt(out);
		List<AbstractInsnNode> code = code(hurt);
		int firstRead = firstIndex(code, i -> i instanceof VarInsnNode v && v.getOpcode() == Opcodes.FLOAD && v.var == 3);
		int armour = firstIndex(code, i -> i instanceof MethodInsnNode c && c.name.equals("getDamageAfterArmorAbsorb"));
		int read = firstIndex(code, i -> i instanceof MethodInsnNode c && c.name.equals("vanillaRead"));
		assertTrue(firstRead >= 0 && firstRead < armour, "first read " + firstRead + " vs armour " + armour);
		assertTrue(read > firstRead && read < armour, "vanillaRead " + read + " must run before armour is applied");
		assertEquals(Opcodes.IFNE, code.get(4).getOpcode());
		assertTrue(code.get(5) instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == 0, "placed right after the invulnerability check");
		new Analyzer<>(new BasicVerifier()).analyze("net/minecraft/world/entity/LivingEntity", hurt);
		assertSame(out, new VanillaDamageReadInjector().transform("net.minecraft.world.entity.LivingEntity", out, null), "a second pass adds nothing");
		System.setProperty(VanillaDamageReadInjector.PROPERTY, "off");
		assertSame(out, new VanillaDamageReadInjector().transform("net.minecraft.world.entity.LivingEntity", out, null));
	}

	@Test void playerOverridesTheSameWayAndGetsTheSameRead() throws Exception {
		TestFixtures.require(Fixture.STAGED, Files.isRegularFile(MERGED), "merged base not staged: " + MERGED);
		byte[] original = NativeCoremodParityTest.read(MERGED, "net/minecraft/world/entity/player/Player");
		byte[] out = new VanillaDamageReadInjector().transform("net.minecraft.world.entity.player.Player", original, null);
		assertNotSame(original, out);
		MethodNode hurt = hurt(out);
		List<AbstractInsnNode> code = code(hurt);
		assertTrue(firstIndex(code, i -> i instanceof VarInsnNode v && v.getOpcode() == Opcodes.FLOAD && v.var == 3)
				< firstIndex(code, i -> i instanceof MethodInsnNode c && c.name.equals("getDamageAfterArmorAbsorb")));
		new Analyzer<>(new BasicVerifier()).analyze("net/minecraft/world/entity/player/Player", hurt);
		assertSame(original, new VanillaDamageReadInjector().transform("net.minecraft.world.entity.monster.Zombie", original, null));
	}

	private static MethodNode hurt(byte[] bytes) {
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		return node.methods.stream().filter(m -> m.name.equals("actuallyHurt") && m.desc.equals(VanillaDamageReadInjector.HURT_DESC))
				.findFirst().orElseThrow();
	}

	private static List<AbstractInsnNode> code(MethodNode method) {
		List<AbstractInsnNode> out = new ArrayList<>();
		for (AbstractInsnNode insn : method.instructions) if (insn.getOpcode() >= 0) out.add(insn);
		return out;
	}

	private static int firstIndex(List<AbstractInsnNode> code, java.util.function.Predicate<AbstractInsnNode> test) {
		for (int i = 0; i < code.size(); i++) if (test.test(code.get(i))) return i;
		return -1;
	}
}
