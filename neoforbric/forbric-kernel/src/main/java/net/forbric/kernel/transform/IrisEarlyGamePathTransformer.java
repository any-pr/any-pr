/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import net.forbric.kernel.util.ForbricLog;

/** A config plugin needs the game directory, not Iris' game-linked platform provider, during Mixin selection. */
public final class IrisEarlyGamePathTransformer implements ClassTransformer {
	public static final String PROPERTY = "forbric.irisEarlyGamePath";
	private static final String PLUGIN = "net.irisshaders.iris.mixin.IrisMixinPlugin";
	private static final String PLATFORM = "net/irisshaders/iris/platform/IrisPlatformHelpers";
	private static final String FABRIC = "net/fabricmc/loader/api/FabricLoader";

	@Override public AnchorSet anchors() {
		return AnchorSet.scanned("reads reviewed directory access in a guest Iris config plugin; the class "
				+ "and its bytecode are supplied by the installed mod, not by the staged game");
	}

	@Override public byte[] transform(String name, byte[] bytes, TransformContext context) {
		if (!PLUGIN.equals(name) || "off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"))) return bytes;
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		int changed = 0;
		for (MethodNode method : node.methods) {
			if (!"<clinit>".equals(method.name)) continue;
			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (!(insn instanceof MethodInsnNode instance) || instance.getOpcode() != Opcodes.INVOKESTATIC
						|| !PLATFORM.equals(instance.owner) || !"getInstance".equals(instance.name)
						|| !("()L" + PLATFORM + ";").equals(instance.desc)) continue;
				AbstractInsnNode next = instance.getNext();
				while (next != null && next.getOpcode() < 0) next = next.getNext();
				if (!(next instanceof MethodInsnNode directory) || directory.getOpcode() != Opcodes.INVOKEINTERFACE
						|| !PLATFORM.equals(directory.owner) || !"getGameDir".equals(directory.name)
						|| !"()Ljava/nio/file/Path;".equals(directory.desc)) continue;
				instance.owner = FABRIC;
				instance.desc = "()L" + FABRIC + ";";
				instance.itf = true;
				directory.owner = FABRIC;
				changed++;
			}
		}
		if (changed == 0) return bytes;
		ClassWriter out = new ClassWriter(0);
		node.accept(out);
		ForbricLog.info("[Forbric/Iris] config plugin reads its game directory from the kernel (%d site(s)) "
				+ "without constructing the renderer provider before Mixin configurations are ready", changed);
		return out.toByteArray();
	}
}
