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

package net.forbric.kernel.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import net.forbric.kernel.util.ForbricLog;

/**
 * Removes named injector methods from a GUEST MIXIN class before Mixin reads it, so that a mixin whose other
 * injectors fit the merged base can apply instead of being pinned whole.
 *
 * <p>The one entry so far is fabric-model-loading-api-v1's {@code ModelManagerMixin}. NeoForge won the byte-merge
 * of {@code ModelManager.lambda$loadBlockModels$2} and replaced vanilla's {@code CuboidModel.fromStream(Reader)}
 * there with its own {@code UnbakedModelParser.parse(Reader)} — the dispatch point for NeoForge {@code "loader"}
 * model formats. Fabric's {@code @Redirect cancelVanillaDeserialize} targets {@code fromStream}, so it cannot
 * bind; its sibling {@code @ModifyArg actuallyDeserializeModel} at {@code Pair.of} DOES bind and hands an
 * already-consumed {@code Reader} to Fabric's deserializer registry. Every one of the 4666 block models then dies
 * on {@code JsonParseException: JSON data was null or empty} and the whole world renders as the missingno
 * checkerboard — the measured PARTIAL that made {@link net.forbric.kernel.mixin.MergedBaseMixinCompat} pin the
 * mixin. Pinning it cost every {@code ModelLoadingPlugin}: block-state resolvers, extra models and per-model
 * modifiers registered by Fabric mods were never called.
 *
 * <p>The two deserializer injectors are the ONLY ones that cannot fit. The other eight — plugin preparation at
 * reload HEAD, the on-load model and block-state modifiers, the thread-local dispatcher around collect and bake,
 * extra-model resolution and the post-upload capture — anchor on instructions the merged {@code ModelManager}
 * still has. Removing the pair from the mixin's bytes lets Mixin apply the rest as written, while NeoForge's
 * parser keeps the call site, so NeoForge {@code "loader"} models keep working too. What the pair did —
 * dispatch Fabric's {@code fabric:type} custom model formats ({@code UnbakedModelDeserializer}) — is done by
 * {@link ModelFormatFunnelInjector} inside NeoForge's own deserializer, so nothing is recorded for them while it is
 * on. With it off, each removed injector is a confirmed finding naming that loss: Traveler's Backpack's backpacks
 * are {@code fabric:type} models, and without the funnel every one of them fails to bake.
 *
 * <p>NeoForge's substitution at that site is a census-pinned row of
 * {@link net.forbric.kernel.mixin.MergedBaseCalleeSwaps#SUBSTITUTED}. MixinRetarget moves an {@code @Inject} along it
 * (fusion's capture of the model id before the parse), because such a handler sees only the point; it never moves
 * this pair, whose handlers are the call and its argument, and {@code parse} is not {@code fromStream}.
 *
 * <p>Guest mixin classes reach the transform chain through {@code ForbricClassLoader.getPreMixinClassBytes},
 * which is also what {@link net.forbric.kernel.mixin.MixinFit} and Mixin itself read, so the pruned bytes are
 * the only bytes anyone judges or applies. Both methods must be present, each carrying an injector annotation
 * whose {@code method} list names {@code lambda$loadBlockModels$2}; a fabric-api that reshapes either leaves the
 * class untouched, with a warning, and the whole mixin then reads PARTIAL as it did before this class existed.
 *
 * <p>{@code -Dforbric.guestInjectorPruner=off} restores the previous behaviour EXACTLY: the pruner stands down and
 * {@code MergedBaseMixinCompat.SUPPRESSED_UNLESS_PRUNED} puts the whole-mixin pin back — never the half-applied
 * state.
 *
 * <p>The second entry is fabric-item-api-v1's {@code ItemStackMixin}. Its five tooltip injectors thread one
 * {@code @Share("index")} through vanilla's {@code addDetailsToTooltip}, which NeoForge turned into a dispatcher over
 * its own appender lists: three bound in a renamed body nothing calls, one drew every Fabric line at once above the
 * item id in advanced tooltips, one bound nowhere. The kernel draws Fabric's component tooltips from NeoForge's
 * appenders instead ({@code KernelNeoTooltips}), so the five go — only while that bridge is on, or they would draw
 * the same lines twice — and {@code hookDamage} (custom damage handlers) applies as written. Nothing is recorded for
 * them: the bridge does their job, and {@code FabricApiModuleLossAudit} names a mod's use of the registry when it
 * is off.
 *
 * <p>The table names injectors the kernel replaces. The other kind of entry is found, not listed: an {@code @Inject}
 * that Mixin will reject outright ({@link net.forbric.kernel.mixin.MixinFit.Rejection} -- the one method its name binds
 * on the merged base is not one its handler was written for, its {@code @At} is sure to find a point there, and Mixin
 * throws "Invalid descriptor" at that point whatever {@code require} says). Kept in a mixin, it failed the mixin's
 * application to that class with every injector still to come, and a config that stays required with it.
 * {@code KernelGuestMixinAdapter} answers it for every mixin it keeps on its verdict -- a PARTIAL one, one kept for its
 * misses on another mod's class, an UNFIT one kept because another mod's mixin targets the class -- and remembers such
 * an injector when nothing else in the mixin calls it and no target binds it as written ({@link #rememberRefused}).
 * {@link #pruneRefused} removes it from the node the bytecode provider hands Mixin -- after every mixin adapter has
 * run, and only while the same rule still says Mixin rejects it there -- so the rest of the mixin applies. Each removal
 * is a confirmed finding naming the binding; it is required when the author's own count for the injector is at least
 * one. {@code -Dforbric.guestInjectorPruner.refused=off} keeps such a mixin whole in front of Mixin, as before; with the
 * whole pruner off the adapter leaves it out instead, never half-applied.
 */
public final class GuestInjectorPruner implements ClassTransformer {
	public static final String PROPERTY = "forbric.guestInjectorPruner";

	/** {@code -Dforbric.guestInjectorPruner.refused=off}: an injector Mixin rejects outright stays in its mixin. */
	public static final String REFUSED_PROPERTY = "forbric.guestInjectorPruner.refused";

	static final String MODEL_MANAGER_MIXIN = "net.fabricmc.fabric.mixin.client.model.loading.ModelManagerMixin";
	static final String MODEL_LAMBDA = "lambda$loadBlockModels$2";
	static final String ITEM_STACK_MIXIN = "net.fabricmc.fabric.mixin.item.ItemStackMixin";
	private static final String SHARED_INDEX = "Lcom/llamalad7/mixinextras/sugar/ref/LocalIntRef;";

	/**
	 * One injector method to remove, and the target-method selector its annotation must carry: a prefix, or with
	 * {@code exact} the whole selector — {@code addDetailsToTooltip} is also the prefix of the two renamed bodies.
	 */
	record Prune(String name, String desc, String selectorPrefix, boolean exact) {
		Prune(String name, String desc, String selectorPrefix) {
			this(name, desc, selectorPrefix, false);
		}

		String key() {
			return name + desc;
		}
	}

	static final Map<String, List<Prune>> TABLE = Map.of(MODEL_MANAGER_MIXIN, List.of(
			new Prune("cancelVanillaDeserialize",
					"(Ljava/io/Reader;)Lnet/minecraft/client/resources/model/cuboid/CuboidModel;", MODEL_LAMBDA),
			new Prune("actuallyDeserializeModel",
					"(Ljava/lang/Object;Ljava/io/Reader;)Ljava/lang/Object;", MODEL_LAMBDA)),
			ITEM_STACK_MIXIN, List.of(
			new Prune("preAppendComponentTooltip", "(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;"
					+ "Lnet/minecraft/world/item/component/TooltipDisplay;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;"
					+ SHARED_INDEX + ")Lnet/minecraft/core/component/DataComponentType;", "addDetailsToTooltip", true),
			new Prune("preShouldDisplay", "(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;"
					+ "Lnet/minecraft/world/item/component/TooltipDisplay;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;"
					+ SHARED_INDEX + ")Lnet/minecraft/core/component/DataComponentType;", "addDetailsToTooltip", true),
			new Prune("preAttributeModifiers", "(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;"
					+ "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;"
					+ "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;" + SHARED_INDEX + ")V", "addDetailsToTooltip", true),
			new Prune("postTooltipsAdvanced", "(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;"
					+ "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;"
					+ "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;" + SHARED_INDEX + ")V", "addDetailsToTooltip", true),
			new Prune("postTooltipsNonAdvanced", "(ZLnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;"
					+ "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;Ljava/util/function/Consumer;"
					+ SHARED_INDEX + ")Z", "addDetailsToTooltip", true)));

	/** The mixin config each entry is declared in, which names the owning mod on the finding. */
	static final Map<String, String> CONFIGS = Map.of(MODEL_MANAGER_MIXIN, "fabric-model-loading-api-v1.mixins.json",
			ITEM_STACK_MIXIN, "fabric-item-api-v1.mixins.json");

	/** Whether an entry applies on this boot, beyond the pruner's own switch. */
	private static final Map<String, BooleanSupplier> ACTIVE = Map.of(MODEL_MANAGER_MIXIN, () -> true,
			ITEM_STACK_MIXIN, GuestInjectorPruner::fabricTooltipBridgeOn);

	/** What is lost when an entry's class loads and is not pruned. */
	private static final Map<String, String> COSTS = Map.of(MODEL_MANAGER_MIXIN,
			"the whole mixin stays pinned, so every Fabric ModelLoadingPlugin -- block-state resolvers, extra "
					+ "models, model modifiers -- is registered and never called",
			ITEM_STACK_MIXIN, "fabric-item-api's tooltip injectors stay in addDetailsToTooltip, where NeoForge's dispatcher "
					+ "makes none of the calls they anchor on (R3 moves none of them: they share an index), so they bind nowhere, "
					+ "the kernel's tooltip bridge stands down, and a Fabric mod's component tooltips are drawn nowhere");

	/** Why an entry's injectors cannot stay, for the log line. */
	private static final Map<String, String> REASONS = Map.of(MODEL_MANAGER_MIXIN,
			"NeoForge replaced CuboidModel.fromStream with UnbakedModelParser.parse at that site, so fabric's @Redirect "
					+ "could not bind while its @ModifyArg did and re-read a consumed Reader (every block model missingno)",
			ITEM_STACK_MIXIN, "NeoForge's ItemStack draws tooltips from its appender lists, where the kernel draws "
					+ "Fabric's component tooltip providers now; these would have drawn them a second time, or nowhere");

	/** What happens to an entry's mixin when a reshaped fabric-api leaves it untouched. */
	private static final Map<String, String> DRIFT = Map.of(MODEL_MANAGER_MIXIN, "it will read PARTIAL and apply half — the state that made every block "
					+ "model missingno",
			ITEM_STACK_MIXIN, "its tooltip injectors bind nowhere on NeoForge's dispatcher and the kernel's tooltip bridge "
					+ "stands down; Fabric component tooltip providers are drawn nowhere");

	/** The finding a removed injector records, or none when a kernel repair does its job. */
	private static final Map<String, String> LOSSES = Map.of(MODEL_MANAGER_MIXIN,
			"the kernel removed this injector: NeoForge's UnbakedModelParser now reads block models at its call site, so "
					+ "Fabric's fabric:type custom model formats (UnbakedModelDeserializer) are not consulted — the "
					+ "kernel's own dispatch of them is off (-D" + ModelFormatFunnelInjector.PROPERTY + "=off)");

	/**
	 * The finding an entry's removed injectors record on this boot, or null when something does their job:
	 * the model pair's {@code fabric:type} dispatch is {@link ModelFormatFunnelInjector}'s while it is on.
	 */
	static String lossOf(String mixin) {
		if (MODEL_MANAGER_MIXIN.equals(mixin) && ModelFormatFunnelInjector.enabled()) return null;
		return LOSSES.get(mixin);
	}

	private static volatile boolean fabricTooltipsPruned;

	/**
	 * fabric-item-api's tooltip injectors go only while the kernel draws Fabric's providers from NeoForge's appenders:
	 * NeoForge's appenders built, and the bridge on.
	 */
	public static boolean fabricTooltipBridgeOn() {
		return !"off".equalsIgnoreCase(System.getProperty("forbric.neoTooltipAppenders", "on"))
				&& !"off".equalsIgnoreCase(System.getProperty(FABRIC_TOOLTIP_BRIDGE, "on"));
	}

	/** {@code -Dforbric.fabricTooltipBridge=off} leaves fabric-item-api's tooltip injectors where they were. */
	public static final String FABRIC_TOOLTIP_BRIDGE = "forbric.fabricTooltipBridge";

	/** Whether fabric-item-api's five tooltip injectors were removed on this boot — the bridge draws only then. */
	public static boolean fabricTooltipInjectorsPruned() {
		return fabricTooltipsPruned;
	}

	/** Every annotation that makes a mixin method an injector: Mixin's own and MixinExtras'. */
	static final Set<String> INJECTOR_DESCS = Set.of(
			"Lorg/spongepowered/asm/mixin/injection/Inject;",
			"Lorg/spongepowered/asm/mixin/injection/Redirect;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
			"Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
			"Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
			"Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
			"Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

	private int pruned;

	public static boolean enabled() {
		return !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "on"));
	}

	@Override
	public String name() {
		return "forbric:guest-injector-pruner";
	}

	@Override
	public AnchorSet anchors() {
		List<AnchorSet.Anchor> anchors = new ArrayList<>();
		for (String mixin : TABLE.keySet()) {
			if (!ACTIVE.get(mixin).getAsBoolean()) continue;
			anchors.add(new AnchorSet.Anchor(mixin, AnchorSet.Severity.REQUIRED, COSTS.get(mixin)));
		}
		return AnchorSet.of(anchors.toArray(new AnchorSet.Anchor[0]));
	}

	@Override
	public byte[] transform(String className, byte[] classBytes, TransformContext context) {
		if (classBytes == null || classBytes.length == 0) return classBytes;
		List<Prune> prunes = TABLE.get(className);
		if (prunes == null || !enabled() || !ACTIVE.get(className).getAsBoolean()) return classBytes;

		ClassNode node = new ClassNode();
		new ClassReader(classBytes).accept(node, 0);
		if (node.methods == null) return classBytes;

		// Both-or-nothing: the pair only makes sense together. Half of it gone is exactly the half-applied state
		// this class exists to avoid, so any drift in either stands the whole edit down.
		List<MethodNode> victims = new ArrayList<>();
		for (Prune prune : prunes) {
			MethodNode found = null;
			for (MethodNode m : node.methods) {
				if (prune.name().equals(m.name) && prune.desc().equals(m.desc)) { found = m; break; }
			}
			if (found == null) {
				// Absent on a second pass is what idempotence looks like; absent on the first is drift.
				if (alreadyPruned(node, prunes)) {
					if (ITEM_STACK_MIXIN.equals(className)) fabricTooltipsPruned = true;
					return classBytes;
				}
				ForbricLog.warn("[Forbric/GuestInjectorPruner] %s has no %s%s — fabric-api reshaped the mixin, leaving "
						+ "it untouched (%s)", className, prune.name(), prune.desc(), DRIFT.get(className));
				return classBytes;
			}
			if (!(prune.exact() ? isInjectorExactlyInto(found, prune.selectorPrefix()) : isInjectorInto(found, prune.selectorPrefix()))) {
				ForbricLog.warn("[Forbric/GuestInjectorPruner] %s.%s no longer injects into %s — fabric-api reshaped "
						+ "the mixin, leaving it untouched (%s)", className, prune.name(), prune.selectorPrefix(), DRIFT.get(className));
				return classBytes;
			}
			victims.add(found);
		}

		node.methods.removeAll(victims);
		pruned += victims.size();
		if (ITEM_STACK_MIXIN.equals(className)) fabricTooltipsPruned = true;
		// Removed, so never run: a confirmed finding for each where nothing does its job, naming what is not
		// covered. The log line below is not the report.
		String loss = lossOf(className);
		for (MethodNode victim : loss == null ? List.<MethodNode>of() : victims) {
			net.forbric.kernel.mixin.MixinCompatibility.recordRemovedInjector(CONFIGS.get(className), className,
					victim.name, victim.desc, loss,
					List.of("kernel pruned " + victim.name + victim.desc + " from " + className,
							"target selector " + prunes.get(0).selectorPrefix(), "source=GuestInjectorPruner"));
		}
		ForbricLog.info("[Forbric/GuestInjectorPruner] pruned %d injector(s) from %s — %s; the other %d injector(s) "
				+ "apply as written", victims.size(), className, REASONS.get(className), countInjectors(node));

		// Only whole methods were removed: no instruction, frame or local changed, so nothing needs recomputing.
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** Whether {@code m} carries an injector annotation whose {@code method} list has a selector starting with {@code prefix}. */
	static boolean isInjectorInto(MethodNode m, String prefix) {
		for (AnnotationNode a : allAnnotations(m)) {
			if (!INJECTOR_DESCS.contains(a.desc)) continue;
			List<Object> values = a.values;
			if (values == null) continue;
			for (int i = 0; i + 1 < values.size(); i += 2) {
				if (!"method".equals(values.get(i))) continue;
				Object v = values.get(i + 1);
				if (v instanceof List<?> list) {
					for (Object s : list) if (s instanceof String str && str.startsWith(prefix)) return true;
				} else if (v instanceof String str && str.startsWith(prefix)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Whether {@code m} carries an injector annotation whose {@code method} list is exactly {@code selector}, and a
	 * {@code @Share} parameter — the shape of fabric-item-api's five, which only move together.
	 */
	static boolean isInjectorExactlyInto(MethodNode m, String selector) {
		boolean shared = false;
		for (List<AnnotationNode> parameter : m.invisibleParameterAnnotations == null ? new List[0] : m.invisibleParameterAnnotations) {
			if (parameter != null) for (AnnotationNode a : parameter) shared |= "Lcom/llamalad7/mixinextras/sugar/Share;".equals(a.desc);
		}
		for (List<AnnotationNode> parameter : m.visibleParameterAnnotations == null ? new List[0] : m.visibleParameterAnnotations) {
			if (parameter != null) for (AnnotationNode a : parameter) shared |= "Lcom/llamalad7/mixinextras/sugar/Share;".equals(a.desc);
		}
		if (!shared) return false;
		for (AnnotationNode a : allAnnotations(m)) {
			if (!INJECTOR_DESCS.contains(a.desc) || a.values == null) continue;
			for (int i = 0; i + 1 < a.values.size(); i += 2) {
				if (!"method".equals(a.values.get(i))) continue;
				Object v = a.values.get(i + 1);
				if (v instanceof List<?> list) return list.size() == 1 && selector.equals(list.get(0));
				return selector.equals(v);
			}
		}
		return false;
	}

	private static List<AnnotationNode> allAnnotations(MethodNode m) {
		List<AnnotationNode> out = new ArrayList<>();
		if (m.visibleAnnotations != null) out.addAll(m.visibleAnnotations);
		if (m.invisibleAnnotations != null) out.addAll(m.invisibleAnnotations);
		return out;
	}

	private static boolean alreadyPruned(ClassNode node, List<Prune> prunes) {
		for (Prune p : prunes) {
			for (MethodNode m : node.methods) {
				if (p.name().equals(m.name) && p.desc().equals(m.desc)) return false;
			}
		}
		return true;
	}

	private static int countInjectors(ClassNode node) {
		int n = 0;
		for (MethodNode m : node.methods) {
			for (AnnotationNode a : allAnnotations(m)) {
				if (INJECTOR_DESCS.contains(a.desc)) { n++; break; }
			}
		}
		return n;
	}

	/** How many injector methods were removed, for the boot summary. */
	public int prunedInjectors() {
		return pruned;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// Injectors Mixin rejects outright

	/**
	 * An {@code @Inject} the adapter found Mixin would reject: in {@code config}, the mixin {@code mixin} (internal name),
	 * the handler {@code name}{@code desc}, the refused binding, and whether its loss is required -- the author's own count
	 * for it ({@code require}, else the config's {@code defaultRequire}) is at least one, as FinalMixinApplications judges
	 * an injector that did not attach.
	 */
	public record Refused(String config, String mixin, String name, String desc, String reason, boolean required) {
	}

	/** Mixin (internal name) → the injectors to take out of it. */
	private static final Map<String, List<Refused>> REFUSED = new java.util.concurrent.ConcurrentHashMap<>();
	/** What was taken out already, so a mixin Mixin reads twice is logged once. */
	private static final Set<String> REFUSED_DONE = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Whether injectors Mixin rejects outright are taken out: this kind's switch, and the pruner's own. */
	public static boolean refusedEnabled() {
		return enabled() && !"off".equalsIgnoreCase(System.getProperty(REFUSED_PROPERTY, "on"));
	}

	/**
	 * Whether {@code name}{@code desc} in {@code mixin} can go on its own: an injector method, in no {@code @Group} (whose
	 * count would then be the group's to fail), and nothing else in the mixin calls it or takes a handle to it.
	 */
	public static boolean prunable(ClassNode mixin, String name, String desc) {
		MethodNode handler = null;
		for (MethodNode m : mixin.methods) if (m.name.equals(name) && m.desc.equals(desc)) handler = m;
		if (handler == null || countInjectors(handler) != 1) return false;
		for (AnnotationNode a : allAnnotations(handler)) if ("Lorg/spongepowered/asm/mixin/injection/Group;".equals(a.desc)) return false;
		for (MethodNode m : mixin.methods) {
			if (m == handler || m.instructions == null) continue;
			for (org.objectweb.asm.tree.AbstractInsnNode insn : m.instructions) {
				if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call && call.owner.equals(mixin.name)
						&& call.name.equals(name) && call.desc.equals(desc)) return false;
				if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode indy) {
					for (Object argument : indy.bsmArgs) {
						if (argument instanceof org.objectweb.asm.Handle h && h.getOwner().equals(mixin.name)
								&& h.getName().equals(name) && h.getDesc().equals(desc)) return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * {@code mixinBytes} without {@code name}{@code desc} and the {@code @Surrogate}s of that name, which stand in only
	 * for it: what Mixin will receive once {@link #pruneRefused} has run, for the verdict to judge.
	 */
	public static byte[] without(byte[] mixinBytes, List<String> handlers) {
		ClassNode node = new ClassNode();
		new ClassReader(mixinBytes).accept(node, 0);
		for (String handler : handlers) {
			int paren = handler.indexOf('(');
			remove(node, handler.substring(0, paren), handler.substring(paren));
		}
		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** Remembers {@code refused} for {@link #pruneRefused}. */
	public static void rememberRefused(Refused refused) {
		REFUSED.computeIfAbsent(refused.mixin(), k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(refused);
	}

	/**
	 * Takes the remembered injectors out of {@code node}, the mixin as Mixin is about to receive it, each only while
	 * {@code stillRejected} -- the verdict's rule asked of this node -- still names a refused binding; one an adapter
	 * already moved where it fits stays. Each one taken out is a confirmed finding.
	 *
	 * @return how many were taken out
	 */
	public static int pruneRefused(ClassNode node, java.util.function.BiFunction<ClassNode, MethodNode, String> stillRejected) {
		List<Refused> entries = node == null || node.methods == null ? null : REFUSED.get(node.name);
		if (entries == null || !refusedEnabled()) return 0;
		int removed = 0;
		for (Refused entry : entries) {
			MethodNode handler = null;
			for (MethodNode m : node.methods) if (m.name.equals(entry.name()) && m.desc.equals(entry.desc())) handler = m;
			if (handler == null) continue;
			String why = stillRejected.apply(node, handler);
			String key = node.name + "." + entry.name() + entry.desc();
			if (why == null) {
				if (REFUSED_DONE.add(key + "?")) {
					ForbricLog.info("[Forbric/GuestInjectorPruner] %s.%s is no longer rejected once the mixin adapters ran; "
							+ "left in place", node.name.replace('/', '.'), entry.name());
				}
				continue;
			}
			remove(node, entry.name(), entry.desc());
			removed++;
			String mixin = node.name.replace('/', '.');
			net.forbric.kernel.mixin.MixinCompatibility.recordRemovedInjector(entry.config(), mixin, entry.name(), entry.desc(),
					"the kernel removed injector " + entry.name() + " before Mixin read it: " + why + ". Mixin would have "
							+ "rejected it (\"Invalid descriptor\") and failed the mixin with it; the rest of the mixin applies",
					entry.required(),
					List.of("kernel pruned " + entry.name() + entry.desc() + " from " + mixin, "refused binding: " + why,
							"source=GuestInjectorPruner (an injector Mixin rejects outright)",
							"-D" + REFUSED_PROPERTY + "=off keeps it, and Mixin rejects the mixin"));
			if (REFUSED_DONE.add(key)) {
				ForbricLog.info("[Forbric/GuestInjectorPruner] pruned %s.%s — %s; Mixin would have rejected it and failed the "
						+ "mixin with it, so the other %d injector(s) apply as written", mixin, entry.name(), why, countInjectors(node));
			}
		}
		return removed;
	}

	/** Test seam: forget every remembered injector. */
	public static void forgetRefused() {
		REFUSED.clear();
		REFUSED_DONE.clear();
	}

	/** Removes {@code name}{@code desc} and the {@code @Surrogate}s of that name from {@code node}. */
	private static void remove(ClassNode node, String name, String desc) {
		node.methods.removeIf(m -> m.name.equals(name) && (m.desc.equals(desc) || allAnnotations(m).stream()
				.anyMatch(a -> "Lorg/spongepowered/asm/mixin/injection/Surrogate;".equals(a.desc))));
	}

	/** How many injector annotations {@code m} carries. */
	private static int countInjectors(MethodNode m) {
		int n = 0;
		for (AnnotationNode a : allAnnotations(m)) if (INJECTOR_DESCS.contains(a.desc)) n++;
		return n;
	}
}
