/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.kernel.util.ForbricLog;

/**
 * Wraps a reviewed {@code @WrapOperation} handler whose call the surviving carrier reordered or widened.
 *
 * <p>{@link MixinAtWidenedCall} moves an injection point onto a carrier's longer call only for injectors whose handler
 * does not mirror the call's arguments; this one does, and Mixin rejects it outright on any other shape. NeoForge's
 * {@code ServerGamePacketListenerImpl.handlePickItemFromBlock} calls
 * {@code BlockState.getCloneItemStack(BlockPos, LevelReader, boolean, Player)} where vanilla calls
 * {@code getCloneItemStack(LevelReader, BlockPos, boolean)} — the same operation, NeoForge's form carrying the player to
 * its block hook — so fabric-api's pick-block wrap ({@code PlayerPickItemEvents.BLOCK}) matched nothing and the event
 * never fired.
 *
 * <p>The handler is renamed aside and a new one takes the merged call's shape: it carries the annotation with the
 * {@code @At} moved to the merged call, and hands the original exactly the arguments it was written for, with an
 * {@code Operation} of the old shape ({@code KernelWrapOperations.reordered}): what it passes goes to the merged call in
 * the merged order, the extra arguments as the merged call had them. Arguments it changes are changed; the call it skips
 * is skipped; the carrier's own call, with whatever its extra arguments feed, still runs whenever the handler calls it.
 *
 * <p>Not {@code @Redirect}. A redirect REPLACES the call, and on the merged base that call is the carrier's extended
 * one: fabric-renderer-api's {@code hasMaterialFlag} redirects would have replaced NeoForge's context-aware
 * {@code hasMaterialFlag(BlockAndTintGetter, BlockPos, BlockState, int)} — and every NeoForge model's override of it —
 * with Fabric's own lookup. On vanilla that redirect replaces a vanilla call; here it would silently take a feature
 * from the other ecosystem's mods, so it is left unbound, as it was.
 *
 * <h2>When</h2>
 *
 * <p>Only for a handler in {@link #REVIEWED}, each row carrying why binding it is right. The structure below proves
 * the call is the same operation; it cannot prove that attaching a handler the merged game has been running WITHOUT is
 * wanted. fabric-networking's codec wraps have exactly this shape on NeoForge's four-argument
 * {@code CustomPacketPayload.codec}, and binding them would put Fabric's payload lookup in front of the kernel's
 * {@code PayloadInterop}, which already serves Fabric payloads and settles the ids both registries claim. A handler
 * whose loss was never replaced is a row here; one that was replaced is not.
 *
 * <p>And then only on evidence the injection is dead and the operation is there: the named call is in none of the bodies the
 * injector selects; exactly one other descriptor of the same owner and name, with the same return type, is; and each
 * argument the mod named has exactly one argument of the same type in that call, no two sharing one. A repeated type
 * would make the mapping a guess, and a guess here hands a handler the wrong object silently; it is refused, like a
 * {@code @Group}, a slice, an ordinal, a static-ness the handler does not match, or a handler whose parameters are not
 * (receiver, arguments, Operation, trailing captures). {@code -Dforbric.wrapOperationShim=off} wraps nothing.
 */
public final class MixinWrapOperationShim {
	public static final String PROPERTY = "forbric.wrapOperationShim";
	static final String RUNTIME = "net/forbric/kernel/runtime/KernelWrapOperations";
	static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
	static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
	private static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";

	/** {@code mixin#handler} → why binding it to the merged call is right. */
	static final Map<String, String> REVIEWED = Map.ofEntries(
			Map.entry("net/fabricmc/fabric/mixin/event/interaction/ServerGamePacketListenerImplMixin#onPickItemFromBlock",
			"fabric-api's PlayerPickItemEvents.BLOCK never fired: nothing else posts it, and NeoForge's getCloneItemStack "
					+ "only adds the player its block hook reads; the handler's own call still runs NeoForge's form"),
			Map.entry("com/zurrtum/create/mixin/BlockItemMixin#checkSound", "Create placement sound context must receive the live context-aware sound query"),
			Map.entry("com/zurrtum/create/mixin/BlockItemMixin#getGroup", "Create placement sounds must preserve the native world, position and player arguments"),
			Map.entry("com/zurrtum/create/mixin/LivingEntityMixin#getBlockFallSound", "Create landing sounds must wrap the native contextual sound query"),
			Map.entry("com/zurrtum/create/mixin/EntityMixin#getStepSound", "Create step sounds must wrap the native contextual sound query"),
			Map.entry("com/zurrtum/create/client/mixin/MultiPlayerGameModeMixin#getHitSound", "Create hit sounds must wrap the native contextual sound query"),
			Map.entry("com/zurrtum/create/client/mixin/LevelEventHandlerMixin#getBreakSound", "Create break sounds must wrap the native contextual sound query"),
			Map.entry("com/zurrtum/create/client/mixin/ModelBlockRendererMixin#getLuminance", "Create block luminance must preserve native world and position"),
			Map.entry("com/zurrtum/create/client/mixin/LightCoordsUtilMixin#getLuminance", "Create light coordinates must preserve native world and position"),
			Map.entry("com/zurrtum/create/client/mixin/ModelBlockRendererMixin#collectParts", "Create model part collection must retain the native block rendering context"));

	private MixinWrapOperationShim() {
	}

	/** A call rename reviewed by a specific adapter; the old argument contract is still checked here. */
	static int adaptExplicit(ClassNode mixin, MethodNode handler, MethodInsnNode live) {
		AnnotationNode injector=MixinFit.injectorOf(handler);
		if(injector==null||!WRAP_OPERATION.equals(injector.desc)||MixinFit.atNodes(injector).size()!=1)return 0;
		AnnotationNode at=MixinFit.atNodes(injector).getFirst();
		MixinAtWidenedCall.Member old=MixinAtWidenedCall.parse(MixinFit.asString(MixinFit.value(at,"target")));
		if(old==null||!old.owner().equals(live.owner)||!Type.getReturnType(old.descriptor()).equals(Type.getReturnType(live.desc)))return 0;
		if(old.name().equals(live.name)&&old.descriptor().equals(live.desc))return 0;
		Type[] wanted=Type.getArgumentTypes(old.descriptor()), available=Type.getArgumentTypes(live.desc);
		if(Arrays.equals(wanted,available)){CarpetMixinAdapter.set(at,"target","L"+live.owner+";"+live.name+live.desc);return 1;}
		int[] mapping=embedding(wanted,available);if(mapping==null)return 0;
		Type[] params=Type.getArgumentTypes(handler.desc);int receiver=live.getOpcode()==Opcodes.INVOKESTATIC?0:1;
		if(params.length<receiver+wanted.length+1||!params[receiver+wanted.length].equals(Type.getObjectType(OPERATION)))return 0;
		for(int i=0;i<wanted.length;i++)if(!params[receiver+i].equals(wanted[i]))return 0;
		var renamed=new MixinAtWidenedCall.Member(live.owner,live.name,old.descriptor());
		mixin.methods.add(wrap(mixin,handler,new Plan(injector,at,renamed,live.desc,mapping,receiver==0)));return 1;
	}

	static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	/** Wraps every eligible handler in {@code mixin}; returns how many. {@code targets} must return nodes WITH code. */
	public static int adapt(ClassNode mixin, Function<String, ClassNode> targets) {
		if (!enabled() || mixin == null || mixin.methods == null || targets == null) return 0;
		List<MethodNode> declared = new ArrayList<>();
		for (String targetName : MixinOverloadPin.targetsOf(mixin)) {
			ClassNode target = targets.apply(targetName);
			if (target != null && target.methods != null) declared.addAll(target.methods);
		}
		if (declared.isEmpty()) return 0;
		int wrapped = 0;
		for (MethodNode handler : new ArrayList<>(mixin.methods)) {
			Plan plan = plan(mixin.name, handler, declared);
			if (plan == null) continue;
			mixin.methods.add(wrap(mixin, handler, plan));
			wrapped++;
		}
		return wrapped;
	}

	/**
	 * The merged call {@link #adapt} will point this handler's {@code @At} at, as an {@code @At} target, or null when it
	 * wraps nothing — the same decision, nothing changed. {@link MixinFit} asks this so a reviewed wrap's anchor reads as
	 * resolved exactly when the wrap will make it so. {@code declared}: the target's methods WITH instructions.
	 */
	public static String wouldWrap(String mixinName, MethodNode handler, List<MethodNode> declared) {
		if (!enabled() || mixinName == null || handler == null || declared == null) return null;
		Plan plan = plan(mixinName, handler, declared);
		return plan == null ? null : "L" + plan.named().owner() + ";" + plan.named().name() + plan.merged();
	}

	/** One wrap, decided: the injector and its point, the call it named, the merged call, and the argument mapping. */
	private record Plan(AnnotationNode injector, AnnotationNode at, MixinAtWidenedCall.Member named, String merged,
			int[] mapping, boolean staticCall) {
	}

	private static Plan plan(String mixinName, MethodNode handler, List<MethodNode> declared) {
		if (handler.name.endsWith(MixinHandlerShim.INNER_SUFFIX) || !REVIEWED.containsKey(mixinName + "#" + handler.name)) return null;
		List<AnnotationNode> annotations = new ArrayList<>();
		if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
		if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
		if (annotations.stream().anyMatch(a -> GROUP.equals(a.desc))) return null;
		AnnotationNode injector = MixinFit.injectorOf(handler);
		if (injector == null || !WRAP_OPERATION.equals(injector.desc)) return null;
		if (MixinFit.value(injector, "slice") != null || MixinFit.value(injector, "target") != null) return null;
		List<AnnotationNode> points = MixinFit.atNodes(injector);
		if (points.size() != 1) return null;
		AnnotationNode at = points.getFirst();
		if (!"INVOKE".equals(MixinFit.asString(MixinFit.value(at, "value"))) || MixinFit.value(at, "ordinal") != null
				|| MixinFit.value(at, "slice") != null) return null;
		MixinAtWidenedCall.Member named = MixinAtWidenedCall.parse(MixinFit.asString(MixinFit.value(at, "target")));
		if (named == null) return null;

		List<MethodNode> bodies = selected(injector, declared);
		if (bodies.isEmpty()) return null;
		Set<String> candidates = new LinkedHashSet<>();
		Boolean staticCall = null;
		for (MethodNode body : bodies) {
			if (body.instructions == null) return null;
			for (AbstractInsnNode insn : body.instructions) {
				if (!(insn instanceof MethodInsnNode call) || !call.owner.equals(named.owner()) || !call.name.equals(named.name())) continue;
				if (call.desc.equals(named.descriptor())) return null;   // the named call is here: it binds as written
				if (!Type.getReturnType(call.desc).equals(Type.getReturnType(named.descriptor()))) continue;
				boolean isStatic = call.getOpcode() == Opcodes.INVOKESTATIC;
				if (staticCall != null && staticCall != isStatic) return null;
				staticCall = isStatic;
				candidates.add(call.desc);
			}
		}
		if (candidates.size() != 1) return null;
		String merged = candidates.iterator().next();
		Type[] wanted = Type.getArgumentTypes(named.descriptor());
		Type[] available = Type.getArgumentTypes(merged);
		int[] mapping = embedding(wanted, available);
		if (mapping == null) return null;

		// The handler must be (receiver?, the named arguments, Operation, trailing captures) and return the call's type.
		Type[] params = Type.getArgumentTypes(handler.desc);
		int receiver = staticCall ? 0 : 1;
		int head = receiver + wanted.length + 1;
		if (params.length < head || !Type.getReturnType(handler.desc).equals(Type.getReturnType(named.descriptor()))) return null;
		if (receiver == 1 && !params[0].equals(Type.getObjectType(named.owner()))) return null;
		for (int i = 0; i < wanted.length; i++) if (!params[receiver + i].equals(wanted[i])) return null;
		if (!params[receiver + wanted.length].equals(Type.getObjectType(OPERATION))) return null;
		for (int i = 0; i < head; i++) if (annotated(handler, i)) return null;   // sugar only after the call's shape
		boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
		for (MethodNode body : bodies) {
			if (handlerStatic != ((body.access & Opcodes.ACC_STATIC) != 0)) return null;
		}
		return new Plan(injector, at, named, merged, mapping, staticCall);
	}

	/** Builds the outer handler along {@code plan} and turns {@code handler} into its inner one. */
	private static MethodNode wrap(ClassNode mixin, MethodNode handler, Plan plan) {
		AnnotationNode injector = plan.injector();
		AnnotationNode at = plan.at();
		MixinAtWidenedCall.Member named = plan.named();
		String merged = plan.merged();
		int[] mapping = plan.mapping();
		Type[] wanted = Type.getArgumentTypes(named.descriptor());
		Type[] available = Type.getArgumentTypes(merged);
		Type[] params = Type.getArgumentTypes(handler.desc);
		int receiver = plan.staticCall() ? 0 : 1;
		int head = receiver + wanted.length + 1;
		boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;

		// The new handler: (receiver?, merged arguments, Operation, trailing) → the original.
		List<Type> outerParams = new ArrayList<>();
		if (receiver == 1) outerParams.add(params[0]);
		outerParams.addAll(Arrays.asList(available));
		outerParams.addAll(Arrays.asList(params).subList(receiver + wanted.length, params.length));
		MethodNode outer = new MethodNode(Opcodes.ASM9, handler.access, handler.name,
				Type.getMethodDescriptor(Type.getReturnType(handler.desc), outerParams.toArray(Type[]::new)), null, null);
		outer.visibleAnnotations = handler.visibleAnnotations == null ? null : new ArrayList<>(handler.visibleAnnotations);
		outer.invisibleAnnotations = handler.invisibleAnnotations == null ? null : new ArrayList<>(handler.invisibleAnnotations);
		int shift = available.length - wanted.length;
		outer.visibleParameterAnnotations = shifted(handler.visibleParameterAnnotations, params.length, shift, head, outerParams.size());
		outer.invisibleParameterAnnotations = shifted(handler.invisibleParameterAnnotations, params.length, shift, head, outerParams.size());

		int[] slots = new int[outerParams.size()];
		int slot = handlerStatic ? 0 : 1;
		for (int i = 0; i < outerParams.size(); i++) {
			slots[i] = slot;
			slot += outerParams.get(i).getSize();
		}
		if (!handlerStatic) outer.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		if (receiver == 1) outer.instructions.add(new VarInsnNode(Opcodes.ALOAD, slots[0]));
		for (int i = 0; i < wanted.length; i++) {
			int position = receiver + mapping[i];
			outer.instructions.add(new VarInsnNode(outerParams.get(position).getOpcode(Opcodes.ILOAD), slots[position]));
		}
		int trailingFrom = receiver + available.length;
		// KernelWrapOperations.reordered(original, receiver, "i→j,…", extras in merged order)
		outer.instructions.add(new VarInsnNode(Opcodes.ALOAD, slots[trailingFrom]));
		outer.instructions.add(new InsnNode(receiver == 1 ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
		StringJoiner map = new StringJoiner(",");
		for (int position : mapping) map.add(Integer.toString(position));
		outer.instructions.add(new LdcInsnNode(map.toString()));
		outer.instructions.add(new IntInsnNode(Opcodes.BIPUSH, available.length));
		outer.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
		for (int j = 0; j < available.length; j++) {
			if (mapped(mapping, j)) continue;
			outer.instructions.add(new InsnNode(Opcodes.DUP));
			outer.instructions.add(new IntInsnNode(Opcodes.BIPUSH, j));
			outer.instructions.add(new VarInsnNode(available[j].getOpcode(Opcodes.ILOAD), slots[receiver + j]));
			box(outer, available[j]);
			outer.instructions.add(new InsnNode(Opcodes.AASTORE));
		}
		outer.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "reordered", "(L" + OPERATION
				+ ";ZLjava/lang/String;[Ljava/lang/Object;)L" + OPERATION + ";", false));
		trailingFrom++;
		for (int i = trailingFrom; i < outerParams.size(); i++) {
			outer.instructions.add(new VarInsnNode(outerParams.get(i).getOpcode(Opcodes.ILOAD), slots[i]));
		}
		outer.instructions.add(MixinHandlerShim.callOwn(mixin, handlerStatic, handler.name + MixinHandlerShim.INNER_SUFFIX,
				handler.desc));
		outer.instructions.add(new InsnNode(Type.getReturnType(handler.desc).getOpcode(Opcodes.IRETURN)));
		outer.maxLocals = slot;
		outer.maxStack = 8 + slot * 2;

		for (int i = 0; i + 1 < at.values.size(); i += 2) {
			if ("target".equals(at.values.get(i))) at.values.set(i + 1, "L" + named.owner() + ";" + named.name() + merged);
		}
		handler.name = handler.name + MixinHandlerShim.INNER_SUFFIX;
		handler.visibleAnnotations = without(handler.visibleAnnotations, injector.desc);
		handler.invisibleAnnotations = without(handler.invisibleAnnotations, injector.desc);
		handler.visibleParameterAnnotations = null;
		handler.invisibleParameterAnnotations = null;
		ForbricLog.info("[Forbric/Mixin] %s: %s now wraps %s%s — the surviving carrier's form of the call it was written for "
				+ "(%s), and hands the handler its own argument order", mixin.name.replace('/', '.'), outer.name,
				named.name(), merged, named.descriptor());
		return outer;
	}

	/** For each wanted argument, the one available argument of the same type; null when any is absent or shared. */
	static int[] embedding(Type[] wanted, Type[] available) {
		if (available.length <= wanted.length && Arrays.equals(wanted, available)) return null;
		int[] mapping = new int[wanted.length];
		boolean[] used = new boolean[available.length];
		for (int i = 0; i < wanted.length; i++) {
			int found = -1;
			for (int j = 0; j < available.length; j++) {
				if (!available[j].equals(wanted[i])) continue;
				if (found >= 0) return null;
				found = j;
			}
			if (found < 0 || used[found]) return null;
			used[found] = true;
