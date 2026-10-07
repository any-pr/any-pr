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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Decides whether a guest mixin still FITS the merged base, by resolving every anchor it names against the merged
 * target's actual bytecode.
 *
 * <h2>Why resolution and not provenance</h2>
 *
 * <p>The merged base IS NeoForge's patched Minecraft — {@code MergedBaseBuilder} builds it from NeoForge's patched
 * jar. "The target is a class
 * NeoForge owns" therefore describes ~93% of every class in the jar: it is the normal state, not a hazard
 * signal. Measured over the 163 suppressions the previous owned-target rule actually made, 108 (66%) targeted a
 * class BYTE-IDENTICAL to NeoForge's own patched jar.
 *
 * <p>Nor does provenance answer the question even when it is exact. Iris's {@code MixinLevelRenderer} anchors on
 * {@code lambda$addSkyPass$8}, {@code lambda$addCloudsPass$3}, {@code lambda$addMainPass$1} — none of which exist in
 * the merged {@code LevelRenderer}, and none of which exist in the VANILLA jar either, because lambda numbering is
 * an artifact of the remap toolchain rather than of the merge. "Neo-identical" does not imply "vanilla-equivalent".
 * The only question that predicts breakage is the direct one: <em>do the members this mixin names still exist?</em>
 *
 * <h2>Why the decision is atomic per mixin</h2>
 *
 * <p>A partially applied mixin is worse than either extreme. {@code relax}'s {@code injectors.defaultRequire=0}
 * silently skips individual non-matching injections, which today leaves Iris's {@code MixinLevelRenderer} with 16 of
 * its 25 injections installed — frame-graph entry hooks bound, the lambda-side exits dropped, i.e. a shader pipeline
 * that binds render targets it never unbinds. So this returns one verdict for the whole mixin and the caller drops
 * all of it or none of it. {@code relax} stays as a second-chance net for what cannot be resolved statically
 * ({@code @At(value="CONSTANT")}, MixinExtras expressions, {@code ordinal}/{@code shift}).
 *
 * <h2>Conservative by construction</h2>
 *
 * <p>Anything this cannot parse counts as RESOLVED. A weak parser must never be the reason a working mixin is
 * dropped; the cost of a false negative is a mixin that misbehaves as it does today, while the cost of a false
 * positive is silently deleting behaviour that worked.
 *
 * <h2>What native drops as well</h2>
 *
 * <p>A miss is the merge's only when the mod's own platform had the member: vanilla 26.2 for a Fabric mod, its own
 * patched game for a NeoForge mod. An injector whose every target that platform lacks too, and which
 * nothing requires to inject, is one native Mixin drops without a word; it is counted neither way and listed in
 * {@link Result#nativeAbsent}, so the mixin is judged on the rest. See {@link NativeAbsentTargets}.
 */
public final class MixinFit {
	private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String SHADOW_DESC = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String OVERWRITE_DESC = "Lorg/spongepowered/asm/mixin/Overwrite;";
	private static final String AT_DESC = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String ACCESSOR_DESC = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
	private static final String INVOKER_DESC = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
	private static final String OPERATION_DESC = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
	private static final String WRAP_OPERATION_DESC = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
	private static final String REDIRECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String GROUP_DESC = "Lorg/spongepowered/asm/mixin/injection/Group;";
	private static final String INJECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String COERCE_DESC = "Lorg/spongepowered/asm/mixin/injection/Coerce;";
	private static final String SURROGATE_DESC = "Lorg/spongepowered/asm/mixin/injection/Surrogate;";
	private static final String CALLBACK_INFO_DESC = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	private static final String CALLBACK_INFO_RETURNABLE_DESC =
			"Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";

	/** Injector annotations whose {@code method} value names one or more target methods on the mixin's target. */
	static final Set<String> INJECTOR_DESCS = Set.of(
			"Lorg/spongepowered/asm/mixin/injection/Inject;",
			"Lorg/spongepowered/asm/mixin/injection/Redirect;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
			"Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
			"Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
			"Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
			"Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

	/** {@code @At} values whose {@code target} names a member that must appear INSIDE the injected method. */
	static final Set<String> RESOLVABLE_AT = Set.of("INVOKE", "INVOKE_ASSIGN", "FIELD");

	public enum Verdict {
		/** Every anchor resolved; apply the mixin unmodified. */
		FIT,
		/** Some anchors resolved and some did not — the half-application case. Drop it. */
		PARTIAL,
		/** No anchor resolved; the mixin was going to be dead weight. Drop it. */
		UNFIT,
		/** Applies cleanly but would misbehave: it {@code @Shadow}s a field the merge orphaned. Drop it. */
		HAZARD
	}

	/**
	 * One {@code @Inject} Mixin will certainly reject with "Invalid descriptor": in a target where its selectors bind
	 * exactly one method, that method is not one the handler was written for, the handler captures no locals, and one of
	 * its {@code @At}s is sure to find a point in that method ({@link #findsAPoint}). Mixin checks the handler at each
	 * point it finds and throws {@code InvalidInjectionException} at the first, whatever {@code require} says (when the
	 * selectors bind two or more methods it skips the one that does not fit instead; where it finds no point it injects
	 * nothing and {@code require} counts that as any other miss), and the exception fails the mixin's application to that
	 * class, every injector after it included -- in a config that stays required, the game. A PARTIAL verdict alone said
	 * nothing about that.
	 *
	 * @param handler    the handler method's name
	 * @param desc       its descriptor
	 * @param reason     the refused binding, as the unresolved anchor names it
	 * @param everywhere whether no target binds it to a method it fits, so removing it loses nothing that would run
	 */
	public record Rejection(String handler, String desc, String reason, boolean everywhere) {
		String key() {
			return handler + desc;
		}
	}

	/**
	 * @param verdict       the decision
	 * @param unresolved    human-readable anchors that did not resolve, for the log
	 * @param resolved      how many anchors resolved
	 * @param total         how many anchors were checked
	 * @param nativeAbsent  injector targets the mod's own platform lacks too, which native Mixin drops without a word
	 *                      ({@link NativeAbsentTargets}); counted in none of the above, so they decide nothing
	 * @param soft          how many of {@code unresolved} are soft: an injector bound only where nothing runs, a drifted
	 *                      anonymous target — reasons for PARTIAL, never for UNFIT
	 * @param rejected      the injectors among the misses that Mixin will reject outright ({@link Rejection})
	 */
	public record Result(Verdict verdict, List<String> unresolved, int resolved, int total,
			List<String> foreign, List<String> nativeAbsent, int soft, List<Rejection> rejected) {
		public Result(Verdict verdict, List<String> unresolved, int resolved, int total, List<String> foreign) {
			this(verdict, unresolved, resolved, total, foreign, List.of(), 0, List.of());
		}

		/** The anchors that did not resolve at all: {@code unresolved} without the soft ones. */
		public int hardUnresolved() {
			return unresolved.size() - soft;
		}

		/** Whether every rejection in {@code later} was already one of this result's, by handler. */
		boolean coversRejectionsOf(Result later) {
			Set<String> mine = new java.util.HashSet<>();
			for (Rejection r : rejected) mine.add(r.key());
			for (Rejection r : later.rejected) if (!mine.contains(r.key())) return false;
			return true;
		}

		/**
		 * Whether the caller should drop this mixin.
		 *
		 * <p>{@code HAZARD} and {@code UNFIT} always drop: those are the silent cases nothing downstream detects.
		 *
		 * <p>{@code PARTIAL} drops only under {@link MixinFit#strict()}, and the default is deliberately NOT strict.
		 * Measured on the real client set, suppressing PARTIAL would newly drop 53 mixins that work today (e.g.
		 * {@code fabric-entity-events-v1:LivingEntityMixin}, where 23 of 26 anchors resolve — losing 23 working
		 * event hooks to avoid 3 dead ones). Keeping PARTIAL makes this change MONOTONIC against the old
		 * owned-target rule: 149 mixins are restored and nothing that works today stops working. Half-application
		 * is a real hazard, but it is the hazard we already ship, and trading it for a 53-mixin regression
		 * unmeasured is how the two previous over-broad generalisations in this package went wrong.
		 *
		 * <p>The report always lists PARTIAL, so the half-applied set is now visible instead of silent — which is
		 * what makes it possible to promote individual entries to {@link MergedBaseMixinCompat#SUPPRESSED_MIXINS}
		 * on evidence, one measured mixin at a time.
		 */
		public boolean shouldSuppress() {
			return switch (verdict) {
				case FIT -> false;
				case PARTIAL -> strict();
				case UNFIT, HAZARD -> true;
			};
		}

		/** A compact "why" for one log line. */
		public String reason() {
			String missing = String.join(", ", new LinkedHashSet<>(unresolved));
			return switch (verdict) {
				case FIT -> "all " + total + " anchor(s) resolve";
				case PARTIAL -> resolved + "/" + total + " anchors resolve, missing: " + missing;
				case UNFIT -> "no anchor resolves (" + missing + ")";
				case HAZARD -> "orphaned @Shadow field(s): " + missing;
			};
		}
	}

	/** {@code -Dforbric.mixinFit=strict} also drops PARTIAL mixins; see {@link Result#shouldSuppress()}. */
	public static boolean strict() {
		return "strict".equalsIgnoreCase(System.getProperty("forbric.mixinFit", "default"));
	}

	/**
	 * {@code -Dforbric.mixinFit.anchorMovers=off}: an {@code @At(INVOKE)}/{@code @At(NEW)} point on a call the carrier
	 * widened reads as resolved for any injector kind again, as before the verdict asked the adapters that move points.
	 */
	static final String ANCHOR_MOVERS_PROPERTY = "forbric.mixinFit.anchorMovers";

	static boolean asksAnchorMovers() {
		return !"off".equalsIgnoreCase(System.getProperty(ANCHOR_MOVERS_PROPERTY, "on"));
	}

	/**
	 * {@code -Dforbric.mixinFit.liveness=off}: an injector bound only to a merged-base method nothing in the merged game
	 * calls ({@link MergedBaseUncalledMethods}) reads as resolved again, and the final-class ledger counts a handler
	 * called only from such a method as attached, as before.
	 */
	static final String LIVENESS_PROPERTY = "forbric.mixinFit.liveness";

	static boolean asksLiveness() {
		return !"off".equalsIgnoreCase(System.getProperty(LIVENESS_PROPERTY, "on"));
	}

	/**
	 * {@code -Dforbric.mixinFit.handlerFit=off}: a name-only {@code @Inject} selector reads as resolved whenever the name
	 * binds, as before the verdict asked whether the method it binds is one the handler was written for.
	 */
	static final String HANDLER_FIT_PROPERTY = "forbric.mixinFit.handlerFit";

	static boolean asksHandlerFit() {
		return !"off".equalsIgnoreCase(System.getProperty(HANDLER_FIT_PROPERTY, "on"));
	}

	/**
	 * {@code -Dforbric.mixinFit.rejectionPoint=off}: a refused binding is a {@link Rejection} whatever its {@code @At} finds
	 * in the method it binds, as before the verdict asked whether Mixin would meet the handler at a point there at all.
	 */
	static final String REJECTION_POINT_PROPERTY = "forbric.mixinFit.rejectionPoint";

	static boolean asksRejectionPoint() {
		return !"off".equalsIgnoreCase(System.getProperty(REJECTION_POINT_PROPERTY, "on"));
	}

	private MixinFit() {
	}

	/**
	 * Resolve every anchor {@code mixinBytes} names against its {@code @Mixin} target(s).
	 *
	 * <p>{@code targetResolver} maps an internal class name ({@code net/minecraft/Foo}) to its MERGED bytes — it must
	 * serve POST-transform-chain bytes, because the chain both adds members (the {@code KeyMapping.MAP} initializer)
	 * and deletes them (the interface-default shadowing overrides). Resolving against raw jar bytes gives wrong
	 * answers. A null return means "not a merged-base class", which counts as resolved.
	 */
	public static Result evaluate(byte[] mixinBytes, Function<String, byte[]> targetResolver) {
		// Everything is the game's unless a caller says otherwise, which is the pre-existing behaviour: no target
		// is foreign, so no mixin is reported as a cross-mod break. Callers that can classify pass the predicate.
		return evaluate(mixinBytes, targetResolver, name -> true);
	}

	/**
	 * @param gameClass whether a binary class name belongs to the game or a carrier rather than to a guest mod.
	 *                  {@code DelegationPolicy::alwaysGame} is the production answer — it already knows which
	 *                  packages are the game, and reusing it keeps this from becoming a second prefix rule that
	 *                  drifts from the first
	 */
	public static Result evaluate(byte[] mixinBytes, Function<String, byte[]> targetResolver,
			java.util.function.Predicate<String> gameClass) {
		return evaluate(mixinBytes, targetResolver, gameClass, MixinAddedMembers.View.NONE);
	}

	/**
	 * @param added what other mixins add to the targets before this one is applied; a {@code @Shadow} of such a member
	 *              resolves, as it does on Fabric. {@link MixinAddedMembers#before} is the production answer
	 */
	public static Result evaluate(byte[] mixinBytes, Function<String, byte[]> targetResolver,
			java.util.function.Predicate<String> gameClass, MixinAddedMembers.View added) {
		return evaluate(mixinBytes, targetResolver, gameClass, added, NativeAbsentTargets.Context.NONE);
	}

	/**
	 * @param nativeView what lets an injector target the mod's own platform lacks too be told from one the merge lost;
	 *                   such an injector is left out of the verdict and named in {@link Result#nativeAbsent}.
	 *                   {@code Context.NONE} asks nothing and counts it as a miss, as before
