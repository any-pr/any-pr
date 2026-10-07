/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Name Fabric mixin-added server listeners without removing them from NeoForge's dependency graph. */
public final class ServerReloadListenerNamesInjector implements ClassTransformer {
	public static final String TARGET = "net.neoforged.neoforge.event.AddServerReloadListenersEvent";
	@Override public AnchorSet anchors() {
		return AnchorSet.of(new AnchorSet.Anchor(TARGET, AnchorSet.Severity.REQUIRED,
				"a Fabric server reload listener added by mixin aborts world loading"));
	}
	@Override public byte[] transform(String name, byte[] bytes, TransformContext context) {
		if (!TARGET.equals(name)) return bytes;
		ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
		boolean changed = false;
		for (var method : node.methods) {
			if (!method.name.equals("lookupName")) continue;
			for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
					&& call.getOpcode() == Opcodes.INVOKESTATIC
					&& call.owner.equals("net/neoforged/neoforge/resource/VanillaServerListeners")
					&& call.name.equals("getNameForClass")
					&& call.desc.equals("(Ljava/lang/Class;)Lnet/minecraft/resources/Identifier;")) {
				call.owner = "net/forbric/kernel/runtime/KernelServerReloadNames";
				call.name = "nameFor";
				changed = true;
			}
		}
		if (!changed) return bytes;
		ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
	}
}
