/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.transform;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.forbric.kernel.util.ForbricLog;

/**
 * Gives {@code BuiltInRegistries} the point where a Fabric game with fabric-api freezes its registries: two empty
 * public static methods, {@link #HEAD_HOOK} and {@link #TAIL_HOOK}, which the kernel calls after the Fabric main and
 * client entrypoints, around the freeze that closes their registration window.
 *
 * <p>On Fabric, {@code fabric-registry-sync-v0} moves {@code BuiltInRegistries.freeze()} out of {@code Bootstrap} and
 * runs it after every mod initialiser, and mods build on that. Create Fly's {@code BuiltInRegistriesMixin} decides who
 * creates its registries by asking whether fabric-api is loaded: without it, a HEAD injector on {@code freeze()}
 * creates them while the root is still open; with it, {@code onInitialize} does, and the TAIL injector only reads them.
 * The kernel keeps the vanilla freeze in {@code Bootstrap} (it owns registration and freezes there, before any
 * {@code Minecraft} exists), so with fabric-api installed that TAIL injector was the first thing to touch
 * {@code CreateRegistries}, its initialiser wrote a new registry into a frozen root, and the game died in
 * {@code Bootstrap.bootStrap()}: "Registry is already frozen (minecraft:root / create:arm_interaction_point_type)"
 * (issue #52). Polymer's state-id reorder (its own TAIL injector) and fabric-object-builder's attribute {@code MODIFY}
 * event (its own HEAD injector) hang on the same two points of {@code freeze()} and ran there too, before any mod had
 * registered a block or a listener.
 *
 * <p>This class only adds the hooks; {@code FabricFreezeHookMixinAdapter} moves a Fabric mixin's HEAD and TAIL
 * injectors on {@code freeze()} onto them, and {@code KernelLifecycle} calls them. With
 * {@code -Dforbric.fabricFreezePoint=off} neither hook exists and those injectors stay on the bootstrap freeze.
 */
public final class FabricFreezePointInjector implements ClassTransformer {
	public static final String PROPERTY = "forbric.fabricFreezePoint";
	public static final String TARGET = "net.minecraft.core.registries.BuiltInRegistries";
	/** Called before the freeze that closes the window the Fabric entrypoints registered in. */
	public static final String HEAD_HOOK = "forbric$fabricFreezeHead";
	/** Called after that freeze. */
	public static final String TAIL_HOOK = "forbric$fabricFreezeTail";
	public static final String HOOK_DESC = "()V";

	public static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	@Override
	public String name() {
		return "forbric-fabric-freeze-point";
	}

	@Override
	public AnchorSet anchors() {
		if (!enabled()) return AnchorSet.scanned("switched off by -D" + PROPERTY);
		return AnchorSet.of(new AnchorSet.Anchor(TARGET, AnchorSet.Severity.REQUIRED,
				"with fabric-api installed, a Fabric mod's HEAD/TAIL injectors on BuiltInRegistries.freeze() run in "
						+ "Bootstrap, before every Fabric main entrypoint, instead of after them (Create Fly cannot boot)"));
	}

	@Override
	public byte[] transform(String className, byte[] bytes, TransformContext context) {
		if (!enabled() || bytes == null || bytes.length == 0 || !TARGET.equals(className)) return bytes;
		ClassNode node = new ClassNode();
		new ClassReader(bytes).accept(node, 0);
		int added = addHooks(node);
		if (added == 0) return bytes;
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		ForbricLog.debug("[Forbric/RegistrySync] BuiltInRegistries carries Fabric's registry freeze point (%d hook(s))", added);
		return writer.toByteArray();
	}

	/** Adds whichever hook is missing; idempotent. */
	static int addHooks(ClassNode node) {
		int added = 0;
		for (String hook : new String[] { HEAD_HOOK, TAIL_HOOK }) {
			if (node.methods.stream().anyMatch(m -> m.name.equals(hook) && m.desc.equals(HOOK_DESC))) continue;
			MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, hook, HOOK_DESC, null, null);
			method.instructions.add(new InsnNode(Opcodes.RETURN));
			method.maxStack = 0;
			method.maxLocals = 0;
			node.methods.add(method);
			added++;
		}
		return added;
	}
}
