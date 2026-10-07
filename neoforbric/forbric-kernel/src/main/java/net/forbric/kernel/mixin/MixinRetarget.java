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
	 * delegation, and this dispatcher is neither. What makes the rewrite safe instead is the IDENTICAL descriptor
	 * together with a WHOLE body: the handler's parameters and its {@code CallbackInfo} bind as they did, and the
	 * body it was written against — its locals, the points a slice or a {@code @Share} spans — is all in the renamed
	 * method.
	 *
	 * <p>Demanded, all of it: every resolvable {@code @At} member of the injector absent from the method the selector
	 * names, present in the renamed one, exactly ONE method in the class fitting that description, and that one proven
	 * the carrier's rename of this body: {@link CarrierRenames} rows for the mod's ecosystem, every anchor one of the
	 * calls or field accesses they say moved there (an anchor the reference's method never made misses on the mod's own
	 * game too) and bound no more often than the reference's method made it (an ordinal only where the count is the
	 * same), and the live class still calling it. The bytes alone are not proof. An audit of the moves they made over 655 mod jars
	 * found seven of 15 wrong: a vanilla method of the same shape that happened to carry the anchor (text_styles'
	 * {@code Style.withColor(I)} into {@code withShadowColor(I)}, which {@code withColor} never calls), and a renamed
	 * body nothing calls (malilib's tooltip hook into NeoForge's {@code addDetailsToTooltipComponents}, where it bound,
	 * never ran, and read as fitting). The rows' ecosystems also keep a NeoForge mod where it is: it was compiled against
	 * the rename, and misses natively the same way.
	 *
	 * <p>A renamed body nothing calls is still where the mod's body is, and a required injector that binds nowhere stops
	 * a strict launch: kept out of it, malilib's last tooltip hook stopped every strict client with malilib (Litematica,
	 * MiniHUD, Tweakeroo) and made the default policy ask. So a pair the census marks {@link CarrierRenames#UNCALLED}
	 * moves too, when the method is private and the live class does not call it, and the move says the injector never
	 * runs there, as MixinFit and the final-class check then report it (a never-running injector marks its mod's row and
	 * stops nothing). {@code -Dforbric.mixinRetarget.renameCensus.uncalled=off} leaves it unbound.
	 *
	 * <p>A {@link CarrierRenames.Kind#PIECE PIECE} pair carries only part of the body — NeoForge's
	 * {@code addDetailsToTooltipTail} is the advanced tail of vanilla's tooltip, {@code lambda$startSleepInBed$0} the
	 * checks before the sleep — and there the descriptor is not enough: cancelling returns from the piece where vanilla
	 * returned from the method, a captured local or a slice may lie in another piece, and a {@code @Share} no longer
	 * reaches the injectors that stayed. Its handler must depend on nothing but the call and the target's arguments, as
	 * R4's must ({@link #movableWhole}), and the arguments must be the method's: a handler that takes any, plainly or
	 * as an {@code @Local(argsOnly = true)}, moves only when the method hands the piece its own
	 * ({@link CarrierRenames#handsOwnArguments}) — NeoForge's {@code extractEntityInInventoryFollowsMouse} hands its
	 * piece angles where it was given the mouse position; a method that stores into a parameter before handing it on
	 * does not hand on its own. fabric-item-api's {@code postTooltipsAdvanced} shares an index with the injectors left in
	 * {@code addDetailsToTooltip}, and stays.
	 *
	 * <p>A handler that can cancel moves into a piece only where the cancel still leaves the method with the value the
	 * handler gave: a {@link CarrierRenames.Exit#LEFT LEFT} pair, whose method returns the piece's result whenever it is
	 * an {@code Either} left (re-checked on the live bytes, {@link CarrierRenames#returnsLefts}), and a handler whose
	 * every cancel is {@code setReturnValue(Either.left(..))} ({@link #cancelsOnlyWithLefts}). NeoForge's
	 * {@code startSleepInBed} hands its lambda's answer to {@code EventHooks.canPlayerStartSleeping} and returns it when it
	 * names a problem, so apoli's {@code preventAvianSleep} and fabric-entity-events' {@code @Cancellable}
	 * {@code redirectSleepDirection} cancel the lambda the way vanilla's own checks in it say no: NeoForge's
	 * {@code CanPlayerSleepEvent} sees the problem, as it sees vanilla's, and the method returns it. apoli cancels with
	 * {@code Either.left(null)}, which DFU's {@code left()} cannot read ({@code Optional.of}): NeoForge's hook throws on it
	 * inside {@code startSleepInBed}. On vanilla's {@code BedBlock} path that is no change — on apoli's own game
	 * {@code BedBlock} throws on {@code problem.message()} — but a caller that checks for a null problem (another mod's
	 * sleeping bag or bed) fails here too, where on apoli's own game it would not. That is no regression: before the
	 * census R3 moved the handler there on the bytes alone. {@code -Dforbric.mixinRetarget.renameCensus.leftExit=off}
	 * keeps every handler that can cancel out of a piece.
	 *
	 * <p>On a whole {@link CarrierRenames.Kind#RENAME RENAME} a handler that shares a value moves only with every handler
	 * of its mixin that shares it in the same method ({@link #shareGroupMoves}).
	 *
	 * <p>A second candidate and the rule declines — a rewrite to the wrong body is an injection running somewhere the
	 * mod did not ask for, silently, which is worse than the anchors simply missing — unless R4 can tell which of them
	 * is a piece of the method the mod named. {@code -Dforbric.mixinRetarget.renameCensus=off} moves on the bytes alone
	 * again, as before the census.
	 */
	private static List<Rewrite> renamedBodies(ClassNode mixin, boolean oneTarget, MethodNode handler,
			AnnotationNode injector, List<String> selectors, ClassNode target, Function<String, byte[]> resolver, boolean shares) {
		String mixinName = mixin.name;
		List<AnnotationNode> ats = MixinFit.atNodes(injector);
		if (ats.isEmpty()) return List.of();

		List<Rewrite> out = new ArrayList<>();
		for (String selector : selectors) {
			List<MethodNode> named = resolveSelector(target, selector, resolver);
			// Mixin injects into the target's OWN method; a superclass method of the same name and descriptor is
			// the one it overrides, not a second candidate. apoli-legacy selects "startSleepInBed" by bare name
			// and ServerPlayer overrides Player's, which made this rule decline a body NeoForge moved into a lambda.
			List<MethodNode> own = named.stream().filter(target.methods::contains).toList();
			if (!own.isEmpty()) named = own;
			if (named.size() != 1) continue;    // an overload set is R1's ambiguity, not this rule's business
			MethodNode selected = named.get(0);

			List<String> wanted = resolvableMembers(ats);
			if (wanted.isEmpty()) continue;
			for (String member : wanted) {
				// One anchor still here means the body did not move; there is nothing to retarget.
				if (MixinFit.containsMember(selected, member)) { wanted = List.of(); break; }
			}
			if (wanted.isEmpty()) continue;

			List<MethodNode> fits = new ArrayList<>();
			for (MethodNode candidate : target.methods) {
				// Same descriptor AND same static-ness: an instance handler cannot bind into a static body.
				if (candidate == selected || !candidate.desc.equals(selected.desc)
						|| (candidate.access & Opcodes.ACC_STATIC) != (selected.access & Opcodes.ACC_STATIC)) continue;
				boolean all = true;
				for (String member : wanted) {
					if (!MixinFit.containsMember(candidate, member)) { all = false; break; }
				}
				if (all) fits.add(candidate);
			}
			if (fits.size() > 1) {
				// Two fits: refuse, unless the method is a carrier's split of vanilla's body and exactly one of them is
				// the piece it dispatches to (R4).
				Rewrite split = oneTarget ? splitHelper(mixinName, handler, injector, selector, target, selected, fits, wanted) : null;
				if (split != null) out.add(split);
				continue;
			}
			if (fits.isEmpty()) continue;
			MethodNode renamed = fits.get(0);
			boolean census = !"off".equalsIgnoreCase(System.getProperty(RENAME_CENSUS_PROPERTY, "on"));
			String why = "a carrier renamed the vanilla body to " + renamed.name + " and left a dispatcher of the same shape behind";
			if (census) {
				Carried carried = renamedByCarrier(mixin, handler, injector, target, selected, renamed, ats);
				if (carried == null) continue;
				// A value the handler shares lives per method: its partners in selected must all move with it.
				if (shares && !shareGroupMoves(mixin, oneTarget, handler, target, selected, renamed, resolver)) continue;
				why = !carried.called()
						? "a carrier renamed the vanilla body to " + renamed.name + ", which nothing in the merged game calls: bound "
								+ "there as in the body, the injector never runs (carrier-renames.txt)"
						: carried.kind() == CarrierRenames.Kind.RENAME
						? "a carrier renamed the vanilla body to " + renamed.name + ", which the class still calls (carrier-renames.txt)"
						: "a carrier renamed the vanilla body to pieces, and " + renamed.name + ", which the class still calls, "
								+ "is the one that makes the call (carrier-renames.txt)";
			}
			out.add(new Rewrite(handler.name, Element.SELECTOR, selector, renamed.name + renamed.desc, why));
		}
		return out;
	}

	/** How a renamed method carries a body for one injector: whole or a piece, and whether anything calls it. */
	private record Carried(CarrierRenames.Kind kind, boolean called) {
	}

	/**
	 * {@code -Dforbric.mixinRetarget.renameCensus.uncalled=off}: no injector moves into a renamed body nothing calls
	 * ({@link CarrierRenames#UNCALLED}); it stays where its anchors are gone, and a required one is a loss again.
	 */
	static final String UNCALLED_PROPERTY = "forbric.mixinRetarget.renameCensus.uncalled";

	/**
	 * How {@code renamed} carries the body of {@code selected} that a carrier renamed, as far as this injector of a mod
	 * of the mixin's ecosystem is concerned; null when it is not proven to. {@link CarrierRenames} rows say so, every
	 * anchor is one of the calls they say moved, {@code target} still calls it, and for a piece the handler is one that
	 * means the same in a piece of the method as in the whole.
	 *
	 * <p>Or the rows mark the renamed method {@link CarrierRenames#UNCALLED}, and the live class still declares it
	 * private and never calls it: NeoForge keeps vanilla's tooltip body as {@code addDetailsToTooltipComponents} and
	 * draws tooltips from its own appenders. There the injector binds where the body it was written against is, and never
	 * runs — which MixinFit and the final-class check then say, instead of a required injector that found no anchor.
	 * What it would mean there does not matter, only that it binds as it would in the body: nothing that could make the
	 * bind itself fail, as R4 asks ({@link #movableWhole}: no captured locals, no slice or {@code @Group}, no sugar but
	 * the method's arguments and a cancel).
	 */
	private static Carried renamedByCarrier(ClassNode mixin, MethodNode handler, AnnotationNode injector,
			ClassNode target, MethodNode selected, MethodNode renamed, List<AnnotationNode> ats) {
		List<CarrierRenames.Row> rows = CarrierRenames.find(target.name, selected.name + selected.desc,
				renamed.name + renamed.desc, MixinStubRebind.ecosystemOf(mixin.name));
		if (rows.isEmpty()) return null;
		boolean called = CarrierRenames.called(rows);
		if (called ? !CarrierRenames.called(target, renamed)
				: "off".equalsIgnoreCase(System.getProperty(UNCALLED_PROPERTY, "on")) || CarrierRenames.called(target, renamed)
						|| (renamed.access & Opcodes.ACC_PRIVATE) == 0) return null;
		for (AnnotationNode at : ats) {
			String value = MixinFit.asString(MixinFit.value(at, "value"));
			String anchor = MixinFit.asString(MixinFit.value(at, "target"));
			if (value == null || anchor == null || !MixinFit.RESOLVABLE_AT.contains(value)) continue;
			int ordinal = MixinFit.value(at, "ordinal") instanceof Integer n ? n : -1;
			if (!CarrierRenames.carries(rows, renamed, anchor, ordinal)) return null;
		}
		CarrierRenames.Kind kind = CarrierRenames.kind(rows);
		if (!called) return movableWhole(handler, injector, true, true) ? new Carried(kind, false) : null;
		if (kind == CarrierRenames.Kind.PIECE) {
			// The method's arguments are the piece's when it has none, or hands the piece its own.
			boolean sameArguments = Type.getArgumentTypes(selected.desc).length == 0
					|| CarrierRenames.handsOwnArguments(target, selected, renamed);
			// A cancel returns from the piece. It means what it meant in the method only when the method returns what the
			// handler cancels with: the table and the live bytes say the method returns the piece's lefts, and every value
			// the handler can cancel with is a left.
			boolean cancelsAsBefore = leftsReturned(rows, target, selected, renamed) && cancelsOnlyWithLefts(mixin, handler, injector);
			if (!movableWhole(handler, injector, sameArguments, cancelsAsBefore)
					|| !sameArguments && takesArguments(handler, injector, renamed)) return null;
		}
		return new Carried(kind, true);
	}

	/** {@code -Dforbric.mixinRetarget.renameCensus.leftExit=off}: no handler that can cancel moves into a piece. */
	static final String LEFT_EXIT_PROPERTY = "forbric.mixinRetarget.renameCensus.leftExit";

	/** The rows mark the pair {@link CarrierRenames.Exit#LEFT} and the live method still returns the piece's lefts. */
	private static boolean leftsReturned(List<CarrierRenames.Row> rows, ClassNode target, MethodNode selected, MethodNode renamed) {
		return !"off".equalsIgnoreCase(System.getProperty(LEFT_EXIT_PROPERTY, "on"))
				&& CarrierRenames.exit(rows) == CarrierRenames.Exit.LEFT && CarrierRenames.returnsLefts(target, selected, renamed);
	}

	private static final String CANCELLABLE_SUGAR = "Lcom/llamalad7/mixinextras/sugar/Cancellable;";
	private static final String CALLBACKS = "org/spongepowered/asm/mixin/injection/callback/";

	/**
	 * Whether every value the handler can cancel its target with is an {@code Either.left(..)}: the callback of a
	 * cancellable {@code @Inject} and every {@code @Cancellable} one is only ever handed a value made by
	 * {@code Either.left} right there ({@code setReturnValue}), read, or passed on to a method of the mixin's own — a
	 * lambda it creates, a private helper — that does the same; never {@code cancel()}ed, stored in a field, returned, or
	 * given to anything else. A handler that cannot cancel passes. apoli's {@code preventAvianSleep} passes its callback
	 * to a lambda over its powers that sets {@code Either.left(null)}; fabric-entity-events' {@code redirectSleepDirection}
	 * sets {@code Either.left(OTHER_PROBLEM)} itself.
	 */
	static boolean cancelsOnlyWithLefts(ClassNode mixin, MethodNode handler, AnnotationNode injector) {
		Type[] params = Type.getArgumentTypes(handler.desc);
		List<Integer> callbacks = new ArrayList<>();
		if (INJECT.equals(injector.desc) && Boolean.TRUE.equals(MixinFit.value(injector, "cancellable"))) {
			for (int i = 0; i < params.length; i++) {
				String desc = params[i].getDescriptor();
				if (CALLBACK_INFO.equals(desc) || CALLBACK_INFO_RETURNABLE.equals(desc)) { callbacks.add(i); break; }
			}
			if (callbacks.isEmpty()) return false;
		}
		for (int i = 0; i < params.length; i++) if (MixinStubRebind.sugar(handler, i, CANCELLABLE_SUGAR) != null) callbacks.add(i);
		for (int callback : callbacks) if (!onlyLefts(mixin, handler, callback, new java.util.HashSet<>(), 0)) return false;
		return true;
	}

	/** Whether {@code method}'s parameter {@code index}, a callback, is used only as {@link #cancelsOnlyWithLefts} allows. */
	private static boolean onlyLefts(ClassNode mixin, MethodNode method, int index, Set<String> seen, int depth) {
		if (depth > 4 || method.instructions == null || method.instructions.size() == 0) return false;
		if (!seen.add(method.name + method.desc + "#" + index)) return true;
		boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
		Type[] params = Type.getArgumentTypes(method.desc);
		if (index >= params.length) return false;
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < index; i++) slot += params[i].getSize();
		CallbackUses uses = new CallbackUses(mixin, slot);
		try {
			new org.objectweb.asm.tree.analysis.Analyzer<>(uses).analyze(mixin.name, method);
		} catch (org.objectweb.asm.tree.analysis.AnalyzerException unreadable) {
			return false;
		}
		if (uses.misused) return false;
		for (Map.Entry<MethodNode, Integer> passed : uses.passed.entrySet()) {
			if (!onlyLefts(mixin, passed.getKey(), passed.getValue(), seen, depth + 1)) return false;
		}
		return true;
	}

	/** A value in {@link CallbackUses}: whether it may be the callback, and whether it is surely an {@code Either.left}. */
	private record Tracked(org.objectweb.asm.tree.analysis.BasicValue basic, boolean callback, boolean left)
			implements org.objectweb.asm.tree.analysis.Value {
		@Override
		public int getSize() {
			return basic.getSize();
		}
	}

	/** Follows a callback through a method and records every use {@link #cancelsOnlyWithLefts} does not allow. */
	private static final class CallbackUses extends org.objectweb.asm.tree.analysis.Interpreter<Tracked> {
		private final org.objectweb.asm.tree.analysis.BasicInterpreter basic = new org.objectweb.asm.tree.analysis.BasicInterpreter();
		private final ClassNode mixin;
		private final int slot;
		boolean misused;
		final Map<MethodNode, Integer> passed = new LinkedHashMap<>();

		CallbackUses(ClassNode mixin, int slot) {
			super(Opcodes.ASM9);
			this.mixin = mixin;
			this.slot = slot;
		}

		private static Tracked plain(org.objectweb.asm.tree.analysis.BasicValue value) {
			return value == null ? null : new Tracked(value, false, false);
		}

		@Override
		public Tracked newValue(Type type) {
			return plain(basic.newValue(type));
		}

		@Override
		public Tracked newParameterValue(boolean isInstanceMethod, int local, Type type) {
			return new Tracked(basic.newParameterValue(isInstanceMethod, local, type), local == slot, false);
		}

		@Override
		public Tracked newOperation(AbstractInsnNode insn) throws org.objectweb.asm.tree.analysis.AnalyzerException {
			return plain(basic.newOperation(insn));
		}

		@Override
		public Tracked copyOperation(AbstractInsnNode insn, Tracked value) throws org.objectweb.asm.tree.analysis.AnalyzerException {
			return new Tracked(basic.copyOperation(insn, value.basic()), value.callback(), value.left());
		}

		@Override
		public Tracked unaryOperation(AbstractInsnNode insn, Tracked value) throws org.objectweb.asm.tree.analysis.AnalyzerException {
			org.objectweb.asm.tree.analysis.BasicValue out = basic.unaryOperation(insn, value.basic());
			// A cast keeps what the value is; anything else done to the callback is a use this cannot follow.
			if (insn.getOpcode() == Opcodes.CHECKCAST) return out == null ? null : new Tracked(out, value.callback(), value.left());
			if (value.callback()) misused = true;
			return plain(out);
		}

		@Override
		public Tracked binaryOperation(AbstractInsnNode insn, Tracked one, Tracked two) throws org.objectweb.asm.tree.analysis.AnalyzerException {
			if (one.callback() || two.callback()) misused = true;
			return plain(basic.binaryOperation(insn, one.basic(), two.basic()));
		}

