/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.kernel.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.kernel.util.ForbricLog;

/**
 * Rebinds a guest injector from a merge-added DELEGATING STUB to the method that carries the body it wants.
 *
 * <p>NeoForge's patch of {@code SimpleContainer.setItem(int, ItemStack)} moved the body — including the
 * {@code setChanged()} call — into a new {@code setItem(int, ItemStack, boolean)} and left the vanilla-shaped
 * method as {@code aload/iload/aload/iconst_0/invokevirtual setItem(…Z)V/return}. fabric-transfer-api-v1's
 * {@code SimpleContainerMixin} selects the vanilla shape by explicit descriptor and {@code @Redirect}s the
 * {@code setChanged()} inside it; on the merged base the call is not there, the mixin reads PARTIAL, and every
 * hopper or pipe transfer through a Fabric mod spams {@code setChanged} for each intermediate step. The same
 * shape hits {@code BaseContainerBlockEntity.setItem}.
 *
 * <p>Rule R1: an injector whose selector carries an explicit descriptor and resolves to a method that is a pure
 * delegating stub — loads, constants, argument construction ({@code NEW/DUP/INVOKESPECIAL <init>/CHECKCAST}),
 * exactly one call to a same-owner same-name method with a different descriptor, and a return; no branch —
 * whose {@code @At} member is absent from the stub but present in that delegate, has its selector rewritten to
 * the delegate, provided the handler does not depend on the stub's parameter list: {@code @At}-driven kinds
 * ({@code @Redirect}, {@code @WrapOperation}, {@code @ModifyArg(s)}, {@code @ModifyExpressionValue},
 * {@code @ModifyReturnValue}, {@code @ModifyConstant}, {@code @WrapWithCondition}) whose captures of the target's
 * arguments, if any, the stub passes to the delegate in place (MixinStubRebind's rule, shared), or an {@code @Inject}
 * that captures nothing or exactly the delegate's parameters; and every {@code @Local} sugar parameter must name a
 * type the delegate's own parameters carry (fabric-content-registries' {@code FuelValuesMixin} captures the
 * {@code HolderLookup.Provider} and {@code FeatureFlagSet} that only the stub has, so it is left alone).
 *
 * <p>The rewrite is applied to the {@link ClassNode} Mixin receives from the bytecode provider
 * ({@code ForbricMixinService.getClassNode}), never to jar bytes: the plan is computed once by
 * {@link KernelGuestMixinAdapter} when it sees the PARTIAL verdict, kept only if the rewritten mixin re-evaluates
 * better, and remembered by the mixin's internal name. {@code -Dforbric.mixinRetarget=off} computes no plan and
 * edits nothing — the PARTIAL lines return exactly as before.
 */
public final class MixinRetarget {
	public static final String PROPERTY = "forbric.mixinRetarget";
	/** {@code -Dforbric.mixinRetarget.split=off}: R3 refuses two fits again, dispatcher or not (R4 off). */
	static final String SPLIT_PROPERTY = "forbric.mixinRetarget.split";
	/**
	 * {@code -Dforbric.mixinRetarget.renameCensus=off}: R3 moves to the one same-shaped method that carries every anchor on
	 * the bytes alone again, with no {@link CarrierRenames} row and no live call to prove a carrier renamed the body to it.
	 */
	static final String RENAME_CENSUS_PROPERTY = "forbric.mixinRetarget.renameCensus";
	/**
	 * {@code -Dforbric.mixinRetarget.extractedHelper=off}: no {@code @Inject} point follows a call into a carrier's
	 * helper along a census row (R5); the reviewed rows have {@code -Dforbric.mixinAbsorbedCall=off}.
	 */
	static final String EXTRACTED_HELPER_PROPERTY = "forbric.mixinRetarget.extractedHelper";
	/**
	 * {@code -Dforbric.mixinRetarget.substitutedCall=off}: no {@code @Inject} point follows a call the carrier
	 * substituted along a {@link MergedBaseCalleeSwaps#SUBSTITUTED} row (R6).
	 */
	static final String SUBSTITUTED_CALL_PROPERTY = "forbric.mixinRetarget.substitutedCall";
	/**
	 * {@code -Dforbric.mixinRetarget.substitutedCall.guard=off}: an R6 move keeps the handler as the mod wrote it, so a
	 * {@code LinkageError} from it propagates into the method it was moved into, as it would natively.
	 */
	static final String SUBSTITUTED_CALL_GUARD_PROPERTY = "forbric.mixinRetarget.substitutedCall.guard";
	/**
	 * The suffix an R6-guarded handler's own body moves to, under the guard that keeps its name and annotation, before
	 * the mixin's mark ({@link MixinHandlerShim#asideName}).
	 */
	static final String GUARDED_SUFFIX = "$forbricguard";
	/**
	 * {@code -Dforbric.mixinRetarget.replacedCall=off}: no {@code @Inject} follows a vanilla method the carrier replaced,
	 * along a {@link MergedBaseCalleeSwaps#REPLACED} row (R7).
	 */
	static final String REPLACED_CALL_PROPERTY = "forbric.mixinRetarget.replacedCall";
	/**
	 * The suffix an R7-moved handler's own body moves to, under the method that reads its arguments off the replacement's,
	 * before the mixin's mark ({@link MixinHandlerShim#asideName}).
	 */
	static final String PROJECTED_SUFFIX = "$forbricreplaced";
	private static final String SELF = "net/forbric/kernel/mixin/MixinRetarget";
	private static final String LINKAGE_ERROR = "java/lang/LinkageError";
	/** Handlers whose {@code LinkageError} R6's guard has reported: once each, however many models they skip. */
	private static final Set<String> SKIPPED = java.util.concurrent.ConcurrentHashMap.newKeySet();

	static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	static final String LOCAL_SUGAR = "Lcom/llamalad7/mixinextras/sugar/Local;";
	static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";
	static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	static final String CALLBACK_INFO_RETURNABLE = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";

	/** Injector kinds whose handler signature is derived from the {@code @At} member, not the target method. */
	static final Set<String> AT_DRIVEN = Set.of(
			"Lorg/spongepowered/asm/mixin/injection/Redirect;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
			"Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
			"Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
			"Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
			"Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;");

	/**
	 * What a rewrite edits: the injector's {@code method} selector, one of its {@code @At.target}s, or the handler itself:
	 * (R6) put behind a guard, {@code from} the handler and {@code to} where its body moves; or (R7) given a method of its
	 * name that takes the replacement's arguments, {@code from} the handler and {@code to} the replacement it selects.
	 */
	public enum Element { SELECTOR, AT_TARGET, GUARD, PROJECT }

	/** One rewrite inside one handler's injector annotation. */
	public record Rewrite(String handler, Element element, String from, String to, String why) {
	}

	public record Plan(String mixin, List<Rewrite> rewrites) {
		public boolean isEmpty() {
			return rewrites.isEmpty();
		}

		public String describe() {
			List<String> parts = new ArrayList<>();
			for (Rewrite r : rewrites) parts.add(r.from() + " → " + r.to() + " (" + r.why() + ")");
			return String.join("; ", parts);
		}
	}

	private static final Map<String, Plan> PLANS = Collections.synchronizedMap(new LinkedHashMap<>());

	private MixinRetarget() {
	}

	public static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	/** Computes R1 for {@code mixin} (parsed with code) against its targets, resolved through {@code resolver}. */
	static Plan plan(ClassNode mixin, Function<String, byte[]> resolver) {
		if (!enabled() || mixin.methods == null) return new Plan(mixin.name, List.of());
		List<Rewrite> rewrites = new ArrayList<>();
		List<String> targets = MixinFit.mixinTargets(mixin);
		// R4 and R5 write one target's piece or helper into the annotation every target shares: with a second target,
		// a move that helps one could break an anchor that resolves on the other, and the adapter only counts the total.
		boolean oneTarget = targets.size() == 1;
		for (String targetName : targets) {
			byte[] targetBytes = resolver.apply(targetName + ".class");
			if (targetBytes == null) continue;
			ClassNode target = MixinFit.parse(targetBytes);
			for (MethodNode handler : mixin.methods) {
				AnnotationNode injector = MixinFit.injectorOf(handler);
				if (injector == null) continue;
				List<String> selectors = MixinFit.stringList(MixinFit.value(injector, "method"));
				List<Rewrite> own = new ArrayList<>();
				for (String selector : selectors) {
					Rewrite rewrite = rewriteFor(handler, injector, selector, target, resolver);
					if (rewrite != null) own.add(rewrite);
				}
				own.addAll(swappedCallees(handler, injector, selectors, target, resolver));
				own.addAll(renamedBodies(mixin, oneTarget, handler, injector, selectors, target, resolver, true));
				// The selector moves when the method is a stub, a rename or a split; the point moves only when the method
				// keeps a body of its own and the call went one level down. Never both for one handler.
				if (oneTarget && own.stream().noneMatch(r -> r.element() == Element.SELECTOR)) {
					own.addAll(movedCalls(mixin.name, handler, injector, selectors, target, resolver));
				}
				// Neither moved nor split: the method kept its body and the call its place, and only the callee changed.
				if (oneTarget && own.isEmpty()) own.addAll(substitutedCalls(mixin.name, handler, injector, selectors, target, resolver));
				// …or the method the mod names, or the one it anchors in, is a vanilla private the carrier replaced outright.
				if (oneTarget && own.isEmpty()) own.addAll(replacedCalls(mixin.name, handler, injector, selectors, target));
				if (oneTarget && own.isEmpty()) {
					Rewrite blockUpdate = C2meBlockUpdateRetarget.plan(mixin.name, handler, injector, selectors, target);
					if (blockUpdate != null) own.add(blockUpdate);
				}
				rewrites.addAll(own);
			}
		}
		return new Plan(mixin.name, List.copyOf(rewrites));
	}

	private static Rewrite rewriteFor(MethodNode handler, AnnotationNode injector, String selector, ClassNode target,
			Function<String, byte[]> resolver) {
		String s = selector.trim();
		if (s.indexOf('*') >= 0 || s.indexOf('/') == 0 || s.indexOf(' ') >= 0 || s.indexOf('=') >= 0) return null;
		int semi = s.indexOf(';');
		if (s.startsWith("L") && semi > 0) s = s.substring(semi + 1);
		int paren = s.indexOf('(');
		if (paren <= 0) return null;    // a name-only selector on a stub is MixinStubRebind's (Fabric mods, carrier-added stubs)
		String name = s.substring(0, paren);
		String desc = s.substring(paren);

		// The stub and its owner, walking the hierarchy the way Mixin resolves a selector.
		ClassNode owner = null;
		MethodNode stub = null;
		ClassNode current = target;
		for (int guard = 0; current != null && guard < 32 && stub == null; guard++) {
			for (MethodNode m : current.methods) {
				if (m.name.equals(name) && m.desc.equals(desc)) { owner = current; stub = m; break; }
			}
			if (stub != null) break;
			if (current.superName == null || "java/lang/Object".equals(current.superName)) break;
			byte[] bytes = resolver.apply(current.superName + ".class");
			current = bytes == null ? null : MixinFit.parse(bytes);
		}
		if (stub == null) return null;
		MethodNode delegate = delegateOf(owner, stub);
		if (delegate == null) return null;

		// At least one @At member is absent from the stub and present in the delegate — the merge moved it.
		boolean moved = false;
		for (AnnotationNode at : MixinFit.atNodes(injector)) {
			String atValue = MixinFit.asString(MixinFit.value(at, "value"));
			String atTarget = MixinFit.asString(MixinFit.value(at, "target"));
			if (atValue == null || atTarget == null || !MixinFit.RESOLVABLE_AT.contains(atValue)) continue;
			if (!MixinFit.containsMember(stub, atTarget) && MixinFit.containsMember(delegate, atTarget)) moved = true;
		}
		if (!moved) return null;
		if (!handlerFits(handler, injector, owner, stub, delegate)) return null;

		return new Rewrite(handler.name, Element.SELECTOR, selector, name + delegate.desc, "merge-added delegating stub");
	}

	/**
	 * Rule R2: an {@code @At(INVOKE)} whose member misses in every method the injector bound to, where a
	 * {@link MergedBaseCalleeSwaps} row names the callee the merged body calls instead — present there, the vanilla
	 * name absent — is rewritten to the merged callee. Only {@code @At}-driven kinds: the handler's shape is the
	 * callee's, which is identical on both sides by construction (same descriptor).
	 */
	private static List<Rewrite> swappedCallees(MethodNode handler, AnnotationNode injector, List<String> selectors,
			ClassNode target, Function<String, byte[]> resolver) {
		if (!AT_DRIVEN.contains(injector.desc)) return List.of();
		List<MethodNode> hits = new ArrayList<>();
		for (String selector : selectors) hits.addAll(resolveSelector(target, selector, resolver));
		if (hits.isEmpty()) return List.of();
		List<Rewrite> out = new ArrayList<>();
		for (AnnotationNode at : MixinFit.atNodes(injector)) {
			String atValue = MixinFit.asString(MixinFit.value(at, "value"));
			String atTarget = MixinFit.asString(MixinFit.value(at, "target"));
			if (!"INVOKE".equals(atValue) || atTarget == null) continue;
			MixinFit.Member want = MixinFit.parseMember(atTarget);
			if (want == null || want.owner() == null || want.desc() == null) continue;
			boolean anywhere = false;
			for (MethodNode hit : hits) if (MixinFit.containsMember(hit, atTarget)) anywhere = true;
			if (anywhere) continue;
			for (MethodNode hit : hits) {
				MergedBaseCalleeSwaps.Swap swap = MergedBaseCalleeSwaps.find(target.name, hit.name + hit.desc, want.owner(),
						want.name(), want.desc());
				if (swap == null) continue;
				if (!MixinFit.containsMember(hit, swap.mergedMember())) continue;
				out.add(new Rewrite(handler.name, Element.AT_TARGET, atTarget, swap.mergedMember(),
						"callee the merge swapped: " + swap.vanillaName() + " → " + swap.mergedName()));
				break;
			}
		}
		return out;
	}

	/**
	 * Rule R3: a selector whose method has lost its body to a carrier's RENAME, where the class still declares the
	 * renamed body under the SAME descriptor and still calls it.
	 *
	 * <p>NeoForge's patch of {@code PersistentEntitySectionManager.addEntity} posts its entity-join event and hands the
	 * rest to a new {@code addEntityWithoutEvent} with the identical descriptor: vanilla's body, renamed. Carpet's scarpet
	 * events and architectury's entity-add event anchor inside that body; on the merged base every one of their anchors
	 * is in the renamed method, and the mixin reads PARTIAL.
	 *
	 * <p>R1 cannot take this: it needs an explicit descriptor in the selector and a body that is nothing but a
