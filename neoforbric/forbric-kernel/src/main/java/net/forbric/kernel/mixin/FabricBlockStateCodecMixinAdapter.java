/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import net.forbric.kernel.util.ForbricLog;

/**
 * Lets fabric-model-loading's block-state codec redirects keep the codec they replace.
 *
 * <p>{@code BlockStateModelUnbakedMixin} redirects the two {@code Codec.flatComapMap} calls in
 * {@code BlockStateModel$Unbaked.<clinit>} and returns fabric's own codecs instead. On the merged base those calls
 * build NeoForge's codecs, the only readers of a variant's {@code "type"} key, so every NeoForge custom block-state
 * model parsed as an empty plain variant while fabric-api was installed (issue #16: Sophisticated Backpacks' placed
 * backpack drew only its outline). Each handler now makes the redirected call itself and returns
 * {@code KernelBlockStateModelFormats.either(that, fabric's)}: Fabric's codec for what names {@code "fabric:type"},
 * NeoForge's for the rest.
 *
 * <p>Only the exact shape is adapted: two {@code @Redirect}s on {@code <clinit>} at ordinals 0 and 1 whose bodies
 * return one registry field each, against a target whose {@code <clinit>} makes exactly two such calls through
 * NeoForge's {@code BlockStateModelHooks}. Anything else is left as fabric-api wrote it.
 * {@code -Dforbric.blockStateModelFormats=off} turns it off.
 */
public final class FabricBlockStateCodecMixinAdapter {
	public static final String PROPERTY = "forbric.blockStateModelFormats";
	static final String MIXIN = "net/fabricmc/fabric/mixin/client/model/loading/BlockStateModelUnbakedMixin";
	static final String TARGET = "net/minecraft/client/renderer/block/dispatch/BlockStateModel$Unbaked";
	static final String REGISTRY = "net/fabricmc/fabric/impl/client/model/loading/CustomUnbakedBlockStateModelRegistry";
	static final String RUNTIME = "net/forbric/kernel/runtime/KernelBlockStateModelFormats";
	static final String NEOFORGE_HOOKS = "net/neoforged/neoforge/client/model/block/BlockStateModelHooks";
	static final String CODEC = "com/mojang/serialization/Codec";
	static final String FLAT_COMAP = "(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;";
	static final String HANDLER = "(Lcom/mojang/serialization/Codec;Ljava/util/function/Function;Ljava/util/function/Function;)"
			+ "Lcom/mojang/serialization/Codec;";
	static final String EITHER = "(Lcom/mojang/serialization/Codec;Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/Codec;";
	private static final String TARGET_ANCHOR = "L" + CODEC + ";flatComapMap" + FLAT_COMAP;

	private FabricBlockStateCodecMixinAdapter() { }

	public static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	/** @return the number of handlers adapted: 2, or 0 when anything differs from the shape this was written for */
	public static int adapt(ClassNode mixin, Function<String, ClassNode> targets) {
		if (!enabled() || !MIXIN.equals(mixin.name)) return 0;
		ClassNode target = targets.apply(TARGET);
		if (target == null || !neoForgeBuildsBoth(target)) return 0;
		List<MethodNode> handlers = new ArrayList<>();
		for (int ordinal = 0; ordinal < 2; ordinal++) {
			MethodNode handler = handler(mixin, ordinal);
			if (handler == null) return 0;
			handlers.add(handler);
		}
		if (mixin.methods.stream().filter(m -> MixinFit.injectorOf(m) != null).count() != 2) return 0;
		for (MethodNode handler : handlers) {
			FieldInsnNode fabric = soleReturnedField(handler);
			InsnList call = new InsnList();
			for (int slot = 0; slot < 3; slot++) call.add(new VarInsnNode(Opcodes.ALOAD, slot));
			call.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, CODEC, "flatComapMap", FLAT_COMAP, true));
			handler.instructions.insertBefore(fabric, call);
			handler.instructions.insert(fabric, new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "either", EITHER, false));
			handler.maxStack = Math.max(handler.maxStack, 3);
		}
		ForbricLog.info("[Forbric/ModelFormats] fabric-model-loading's block-state codec redirects keep NeoForge's codec "
				+ "for variants without \"fabric:type\" — NeoForge's \"type\" models (Sophisticated Backpacks' backpack) "
				+ "were parsed as empty plain variants");
		return handlers.size();
	}

	/** Exactly two {@code flatComapMap} calls in {@code <clinit>}, built by NeoForge's hooks — the codecs worth keeping. */
	static boolean neoForgeBuildsBoth(ClassNode target) {
		MethodNode clinit = target.methods.stream().filter(m -> "<clinit>".equals(m.name)).findFirst().orElse(null);
		if (clinit == null) return false;
		int calls = 0;
		boolean neoForge = false;
		for (AbstractInsnNode insn : clinit.instructions) {
			if (!(insn instanceof MethodInsnNode call)) continue;
			if (CODEC.equals(call.owner) && "flatComapMap".equals(call.name) && FLAT_COMAP.equals(call.desc)) calls++;
			if (NEOFORGE_HOOKS.equals(call.owner)) neoForge = true;
		}
		return calls == 2 && neoForge;
	}

	private static MethodNode handler(ClassNode mixin, int ordinal) {
		for (MethodNode method : mixin.methods) {
			if (!HANDLER.equals(method.desc) || (method.access & Opcodes.ACC_STATIC) == 0) continue;
			AnnotationNode redirect = MixinFit.injectorOf(method);
			if (redirect == null || !"Lorg/spongepowered/asm/mixin/injection/Redirect;".equals(redirect.desc)
					|| MixinFit.value(redirect, "slice") != null
					|| !MixinFit.stringList(MixinFit.value(redirect, "method")).equals(List.of("<clinit>()V"))) continue;
			List<AnnotationNode> at = MixinFit.atNodes(redirect);
			if (at.size() != 1 || !"INVOKE".equals(MixinFit.value(at.getFirst(), "value"))
					|| !TARGET_ANCHOR.equals(MixinFit.value(at.getFirst(), "target"))
					|| !Integer.valueOf(ordinal).equals(MixinFit.value(at.getFirst(), "ordinal"))) continue;
			if (soleReturnedField(method) == null) return null;
			return method;
		}
		return null;
	}

	/** The registry field the handler returns, when its whole body is {@code GETSTATIC; ARETURN}; else null. */
	static FieldInsnNode soleReturnedField(MethodNode handler) {
		List<AbstractInsnNode> code = new ArrayList<>();
		for (AbstractInsnNode insn : handler.instructions) if (insn.getOpcode() >= 0) code.add(insn);
		if (code.size() != 2 || code.get(1).getOpcode() != Opcodes.ARETURN
				|| !(code.get(0) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC
				|| !REGISTRY.equals(field.owner) || !("L" + CODEC + ";").equals(field.desc)) return null;
		return field;
	}
}
