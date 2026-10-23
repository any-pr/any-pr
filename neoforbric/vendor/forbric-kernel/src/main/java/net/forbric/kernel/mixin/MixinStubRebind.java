/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.Ecosystem;
import net.forbric.kernel.util.ForbricLog;

/**
 * Moves a mod's injector off a merge-added delegating stub onto the method that carries the body, when the mod was
 * compiled against a class where that signature was the body.
 *
 * <p>Mixin binds a selector without a descriptor to the FIRST declared method of that name (the selector's default
 * quantifier is one match; {@code TargetSelectors} stops there), and one with a descriptor to exactly that method. A
 * carrier that widened a vanilla method usually kept vanilla's signature in place as a stub —
 * {@code Player.getDestroySpeed(BlockState)} is {@code return getDestroySpeed(state, null)} on the merged base — and
 * put the body in the new overload after it. A Fabric mod was compiled against vanilla, where that one method IS the
 * body, so its injector lands on the stub: an anchor inside the body is simply missing, and a {@code HEAD} or
 * {@code RETURN} injection runs only when something calls the stub. Nothing on the merged base calls
 * {@code getDestroySpeed(BlockState)}: architectury's and Collective's break-speed events never fired.
 *
 * <p>The injector's selector moves to the delegate. A handler that captures the target's arguments is wrapped: the
 * outer takes the delegate's parameters and hands the original the stub's, read off the stub's own delegation call —
 * each stub parameter must reach the call unchanged, or nothing moves. Kept exactly:
 * <ul>
 *   <li>only along a row of {@code carrier-stubs.txt}: the stub's signature is vanilla's and the overload is the
 *       carrier's. Vanilla keeps stub-and-overload pairs of its own ({@code Minecraft.disconnect(Screen, boolean)}
 *       forwarding to the three-argument one) and a mod that chose the short one there meant it;</li>
 *   <li>only for a mod whose own platform ran that selector on code. A Fabric mod was compiled against vanilla, so
 *       it moves along every row: fusion's sprite capture on {@code ModelManager.loadModels} sat on the forwarding
 *       stub nothing calls, its static stayed null, and every block and item model on the client failed to bake. A
 *       NeoForge mod moves only where its carrier's own patched class has vanilla's signature as the body (or, for
 *       a name-only selector, only the widened overload) — the row's {@code neo=} column — and where NeoForge keeps
 *       the same stub itself gets what it would get natively.
 *       {@code -Dforbric.mixinStubRebind.forgeFamily=off} moves Fabric mods' injectors only, as before;</li>
 *   <li>only a PURE stub: loads, constants, fields, lambdas and method references (a constant when they capture
 *       nothing — NeoForge's {@code Language.loadFromJson(InputStream, BiConsumer)} passes a no-op component consumer;
 *       {@code EntityFluidInteraction.update(Entity, boolean)} wraps its flag in the {@code Predicate} its body takes)
 *       and argument construction — objects, and calls whose result is an argument: what vanilla's body computed
 *       inline and the carrier now computes first, {@code Player.doSweepAttack}'s
 *       {@code target.getBoundingBox().inflate(1, 0.25, 1)} or {@code Entity.restituteMovementAfterCollisions}'
 *       {@code getOnPosLegacy()} — then one call to a same-name overload of the same class and static-ness, returning
 *       its result unchanged; the overload must have a body (an interface default forwarding to an abstract overload
 *       is no stub). A call whose result is not passed on is the stub doing something else, and makes it none;</li>
 *   <li>every {@code INVOKE}/{@code FIELD}/{@code NEW} anchor absent from the stub and present in the delegate
 *       ({@code HEAD}, {@code RETURN} and {@code TAIL} are equivalent on both: the stub returns what the delegate
 *       returns);</li>
 *   <li>{@code @Inject} capturing nothing or exactly the stub's arguments, no locals capture; a MixinExtras
 *       {@code @Local} by a name the delegate's local variable table has in that type, or by its type alone (no name,
 *       ordinal, index or argsOnly) when, at every {@code INVOKE}/{@code FIELD} anchor in the delegate, exactly one
 *       local slot is of that type as Mixin's own walk down the method types it, the table names it there and that
 *       walk still holds it — MixinExtras then picks that one and nothing else. fusion's overlay-model hook takes the {@code ModelDiscovery} of
 *       {@code ModelManager.discoverModelDependencies} that way: the body is NeoForge's four-argument overload, where
 *       {@code result} is the only one ({@code -Dforbric.mixinStubRebind.typedLocal=off} leaves these where they
 *       are); an argsOnly {@code @Local} (by type, or type and ordinal) when the stub argument it picks is passed
 *       through as the delegate argument the same rule picks; a {@code @Share} in the mixin's own namespace only when
 *       every handler sharing that key on the stub moves to the same body, since MixinExtras allocates one value per
 *       target method. owo's lang hooks (a de-nesting wrap on {@code JsonObject.entrySet}, a rich-text wrap on
 *       {@code GsonHelper.convertToString} and a skip on {@code BiConsumer.accept}) pass three flags between them that
 *       way on {@code Language.loadFromJson}, whose body is NeoForge's three-argument overload; left on the stub,
 *       none of them attached and NeoForge rejected owo's nested keys, dropping every owo-based mod's whole lang file
 *       ({@code -Dforbric.mixinStubRebind.shared=off} leaves these where they are). No {@code @Group}, and never more
 *       anchors in the body than the injector's {@code allow};</li>
 *   <li>the {@code @At}-driven kinds only when every parameter past the injector's own contract — the value it
 *       modifies, or the receiver and arguments of the call it replaces or wraps — is a capture of the stub's LEADING
 *       arguments that the stub passes to the delegate at the same positions. Mixin lets any of these kinds take a
 *       prefix of the target's arguments after its own, and the rule used to read every one of them as part of the
 *       call: torrential's {@code @ModifyReturnValue} on {@code FuelValues.vanillaBurnTimes(Provider, FeatureFlagSet,
 *       int)} captures all three, the stub feeds the first two into a {@code Builder}, and moving it to
 *       {@code (Builder, int)} made MixinExtras reject the handler and the whole required mixin with it. It now
 *       stays on the stub, where it binds, and is reported SUSPECTED: it then runs only where something calls the
 *       stub. The merged dedicated server builds its fuel without calling it; the kernel's fuel bridge
 *       ({@code KernelFabricFuel.throughVanillaReturnHooks}) now calls it there, so this one runs on both sides unless
 *       {@code -Dforbric.fabricFuel.returnHooks=off} ({@code -Dforbric.mixinStubRebind.stubFinding=off} drops the
 *       finding); puzzleslib's {@code getDestroySpeed(float, BlockState)} still moves,
 *       because the stub passes its {@code BlockState} straight through as the delegate's first argument. When the
 *       contract's size cannot be told, nothing moves;</li>
 *   <li>a {@code @ModifyVariable} only by one {@code name} (never {@code ordinal}/{@code index}, which count locals by
 *       type across a body the carrier widened), at {@code LOAD}/{@code STORE} with no slice, when the stub has no
 *       local of that name and the delegate's table has it in one slot of the handler's type, accessed while live more
 *       times than the {@code @At}'s ordinal. torrential's Conduit Power mining bonus names {@code speed} in
 *       {@code Player.getDestroySpeed}; the merged stub has no {@code speed}, the body is the overload the game calls,
 *       and the bonus silently never applied. {@code -Dforbric.mixinStubRebind.modifyVariable=off} leaves these where
 *       they are.</li>
 * </ul>
 * {@code -Dforbric.mixinStubRebind=off} leaves every selector as compiled.
 */
public final class MixinStubRebind {
	public static final String PROPERTY = "forbric.mixinStubRebind";
	/** {@code -Dforbric.mixinStubRebind.modifyVariable=off}: no {@code @ModifyVariable} moves; everything else still does. */
	public static final String MODIFY_VARIABLE_PROPERTY = "forbric.mixinStubRebind.modifyVariable";
	/**
	 * {@code -Dforbric.mixinStubRebind.captures=off}: the {@code @At}-driven kinds move (here and in MixinRetarget's R1)
	 * as they did before trailing captures were told apart — an A/B switch; with it torrential's fuel hook fails again.
	 */
	public static final String CAPTURES_PROPERTY = "forbric.mixinStubRebind.captures";
	/**
	 * {@code -Dforbric.mixinStubRebind.sugarBoundary=off}: any parameter annotation ends a handler's call part (here and
	 * in MixinRetarget's R1), and one in that part keeps the injector on the stub, as before.
	 */
	public static final String SUGAR_BOUNDARY_PROPERTY = "forbric.mixinStubRebind.sugarBoundary";
	/** {@code -Dforbric.mixinStubRebind.stubFinding=off}: a handler its captures keep on a stub is not reported. */
	public static final String STUB_FINDING_PROPERTY = "forbric.mixinStubRebind.stubFinding";
	/** {@code -Dforbric.mixinStubRebind.forgeFamily=off}: only Fabric mods' injectors move, as before the carrier columns. */
	public static final String FORGE_FAMILY_PROPERTY = "forbric.mixinStubRebind.forgeFamily";
	/** {@code -Dforbric.mixinStubRebind.typedLocal=off}: a handler with a by-type-only {@code @Local} stays on the stub. */
	public static final String TYPED_LOCAL_PROPERTY = "forbric.mixinStubRebind.typedLocal";
	/** {@code -Dforbric.mixinStubRebind.shared=off}: a handler with a {@code @Share} or an argsOnly {@code @Local} stays on the stub. */
	public static final String SHARED_PROPERTY = "forbric.mixinStubRebind.shared";
	/** {@code -Dforbric.mixinStubRebind.allow=off}: an injector moves whatever its {@code allow} says, as before (A/B only). */
	public static final String ALLOW_PROPERTY = "forbric.mixinStubRebind.allow";

	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	private static final String CALLBACK_INFO_RETURNABLE = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
	private static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";
	private static final String SHARE = "Lcom/llamalad7/mixinextras/sugar/Share;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String GROUP = "Lorg/spongepowered/asm/mixin/injection/Group;";
	private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
	private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
	private static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
	private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
	private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
	/** Kinds whose own contract is ONE value: the one they modify, or {@code @ModifyArgs}' {@code Args}. */
	private static final Set<String> ONE_VALUE = Set.of(
			"Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
			"Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
			MODIFY_VARIABLE,
			MODIFY_ARGS);
	/** Kinds whose own contract is the receiver and arguments of the access they replace or guard. */
	private static final Set<String> CALL_SHAPED = Set.of(
			REDIRECT,
			"Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
			WRAP_OPERATION);
	private static final Set<String> LOCAL_POINTS = Set.of("LOAD", "STORE");
	private static final Set<String> CALL_POINTS = Set.of("INVOKE", "INVOKE_ASSIGN", "INVOKE_STRING", "FIELD", "NEW");
	private static final Set<String> EDGE_POINTS = Set.of("HEAD", "RETURN", "TAIL");

	/** The shipped table of carrier-added stubs; CarrierStubCensusTest pins it to the staged artifacts. */
	static final String TABLE = "/net/forbric/kernel/mixin/carrier-stubs.txt";
	/** {@code owner#stubNameDesc -> delegateDesc} → what a NeoForge mod was compiled against there. */
	private static volatile Map<String, Row> carrierStubs;

	/** Mixin class (internal name) → the ecosystem of the mod whose config declares it; filled as configs are read. */
	private static final Map<String, Ecosystem> ECOSYSTEMS = new ConcurrentHashMap<>();
	/** Mixin class (internal name) → the config that declares it, so a finding can name the mod. */
	private static final Map<String, String> CONFIGS = new ConcurrentHashMap<>();

	private MixinStubRebind() {
	}

	static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	static boolean modifyVariableEnabled() {
		return !"off".equalsIgnoreCase(System.getProperty(MODIFY_VARIABLE_PROPERTY, "on"));
	}

	static boolean capturesGuarded() {
		return !"off".equalsIgnoreCase(System.getProperty(CAPTURES_PROPERTY, "on"));
	}

	static boolean forgeFamilyEnabled() {
		return !"off".equalsIgnoreCase(System.getProperty(FORGE_FAMILY_PROPERTY, "on"));
	}

	static boolean typedLocalEnabled() {
		return !"off".equalsIgnoreCase(System.getProperty(TYPED_LOCAL_PROPERTY, "on"));
	}

	static boolean sharedEnabled() {
		return !"off".equalsIgnoreCase(System.getProperty(SHARED_PROPERTY, "on"));
	}

	/** Records which family's mod declared {@code mixinInternalName}; null when the config's owner is ambiguous. */
	public static void noteEcosystem(String mixinInternalName, Ecosystem ecosystem) {
		if (mixinInternalName != null && ecosystem != null) ECOSYSTEMS.put(mixinInternalName, ecosystem);
	}

	/** Which family's mod declared {@code mixinInternalName}; null when not recorded or the config's owner is ambiguous. */
	static Ecosystem ecosystemOf(String mixinInternalName) {
		return mixinInternalName == null ? null : ECOSYSTEMS.get(mixinInternalName);
	}

	/** {@link #noteEcosystem}, and the config that declared it, which is how a finding about it names the mod. */
	public static void noteEcosystem(String mixinInternalName, Ecosystem ecosystem, String configName) {
		noteEcosystem(mixinInternalName, ecosystem);
		if (mixinInternalName != null && configName != null) CONFIGS.put(mixinInternalName, configName);
	}

	/** Test seam. */
	static void forget() {
		ECOSYSTEMS.clear();
		CONFIGS.clear();
	}

	/** Moves every eligible injector of {@code mixin}; returns how many. {@code targets} must return nodes WITH code. */
	public static int adapt(ClassNode mixin, Function<String, ClassNode> targets) {
		if (!enabled() || mixin == null || mixin.methods == null || targets == null) return 0;
		Ecosystem ecosystem = ECOSYSTEMS.get(mixin.name);
		if (ecosystem == null) return 0;   // no known owner: nothing says what it was compiled against
		List<String> targetNames = MixinOverloadPin.targetsOf(mixin);
		if (targetNames.size() != 1) return 0;   // one target: a selector means one method
		ClassNode target = targets.apply(targetNames.getFirst());
		if (target == null || target.methods == null) return 0;
		int moved = 0;
		for (Map.Entry<MethodNode, Plan> planned : plans(mixin, target, ecosystem,
				(handler, stub, delegate) -> staysOnStub(mixin, handler, target, stub, delegate)).entrySet()) {
			if (planned.getValue() == null) continue;
			MethodNode handler = planned.getKey();
			MethodNode outer = move(mixin, handler, target, ecosystem, planned.getValue());
			if (outer != handler) mixin.methods.add(outer);
			moved++;
		}
		return moved;
	}

	/**
	 * The body an injector of {@code mixin} bound to a carrier stub will move to, or null when it will not move: every
	 * check {@link #adapt} makes — the handler's own, and its {@code @Share} group's — and nothing changed. MixinFit asks
	 * this so its verdict and the rebind cannot disagree.
	 */
	public static MethodNode destination(ClassNode mixin, MethodNode handler, ClassNode target) {
		if (!enabled() || mixin == null || mixin.methods == null || handler == null || target == null || target.methods == null) return null;
		Ecosystem ecosystem = ECOSYSTEMS.get(mixin.name);
		if (ecosystem == null) return null;
		if (MixinOverloadPin.targetsOf(mixin).size() != 1) return null;   // adapt moves nothing in a mixin of several targets
		Plan plan = plans(mixin, target, ecosystem, null).get(handler);
		return plan == null ? null : plan.delegation().delegate();
	}

	/**
	 * Every handler's plan, with every {@code @Share} group that cannot move whole dropped. MixinExtras gives each key
	 * one value per target method (in the mixin's own namespace), so handlers sharing a key in the method Mixin bound them
	 * to must all land in the same body, or each would get a value of its own: owo's three lang hooks pass the "skip the
	 * next key" and "rich translations" flags between them that way.
	 *
	 * @param capturesLost told {@code (handler, stub, delegate)} for a handler that would have moved but for its trailing
	 *                     captures of the stub's arguments; {@code null} when the caller only wants the answer
	 */
	private static Map<MethodNode, Plan> plans(ClassNode mixin, ClassNode target, Ecosystem ecosystem,
			CapturesLost capturesLost) {
		Map<MethodNode, Plan> plans = new java.util.LinkedHashMap<>();
		for (MethodNode handler : new ArrayList<>(mixin.methods)) {
			if (handler.name.endsWith(MixinHandlerShim.INNER_SUFFIX)) continue;
			plans.put(handler, plan(handler, target, ecosystem, capturesLost == null ? null
					: (stub, delegate) -> capturesLost.accept(handler, stub, delegate)));
		}
		for (boolean dropped = true; dropped; ) {   // dropping one sharer can leave another group incomplete
			dropped = false;
			for (Map.Entry<MethodNode, Plan> entry : plans.entrySet()) {
				Plan plan = entry.getValue();
				if (plan == null || plan.shares().isEmpty()) continue;
				for (MethodNode other : plans.keySet()) {
					if (other == entry.getKey() || java.util.Collections.disjoint(shareKeys(other), plan.shares())) continue;
					if (!mayBind(other, target, plan.stub())) continue;   // a key is shared within one target method only
					Plan theirs = plans.get(other);
					if (theirs == null || theirs.delegation().delegate() != plan.delegation().delegate()) {
						entry.setValue(null);
						dropped = true;
						break;
					}
				}
			}
		}
		return plans;
	}

	/** The keys {@code handler} shares in its mixin's own namespace. */
	static Set<String> shareKeys(MethodNode handler) {
		Set<String> keys = new java.util.HashSet<>();
		for (int i = 0; i < Type.getArgumentTypes(handler.desc).length; i++) {
			AnnotationNode share = sugar(handler, i, SHARE);
