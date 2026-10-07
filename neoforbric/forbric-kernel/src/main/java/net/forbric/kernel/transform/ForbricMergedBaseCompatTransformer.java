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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.api.Ecosystem;
import net.forbric.api.ForeignType;
import net.forbric.kernel.util.ForbricLog;

/**
 * Repairs class-local bytecode invariants of the game base — NeoForge's own patched jar, which a
 * decompile-recompile pipeline produced — so vanilla parity and Fabric and NeoForge mod behaviour hold
 * on it.
 */
public final class ForbricMergedBaseCompatTransformer implements ClassTransformer {
	/**
	 * Reads another class's bytes, for repairs that have to look beyond the class in hand. Kept because the
	 * boot chain constructs this transformer with a resolver; no current repair walks a superclass chain,
	 * so a transformer built without one simply runs every repair it has.
	 */
	private final java.util.function.Function<String, byte[]> classBytes;

	/** Without a resolver: every repair runs; the ones that would read other classes stand down instead of guessing. */
	public ForbricMergedBaseCompatTransformer() {
		this(null);
	}

	public ForbricMergedBaseCompatTransformer(java.util.function.Function<String, byte[]> classBytes) {
		this.classBytes = classBytes;
	}

	@Override
	public String name() {
		return "forbric-merged-base-compat";
	}

	@Override
	public AnchorSet anchors() {
		// Independent repairs behind one `changed` flag -- key mappings, the particle map, the save on
		// teardown. Each one can stop applying on its own, and a single class-level answer cannot see that.
		// This is the largest reservoir of the failure this mechanism exists for, and it needs one claim per
		// repair rather than one anchor per class.
		//
		// COUNTED, never written down. This sentence said "47" and the comment above it said "Forty" while
		// REPAIRS held 49: two self-descriptions that drifted because nothing compared them to anything, in
		// the one class whose entire job is that a silent change gets noticed. The list is the number.
		return AnchorSet.scanned(REPAIRS.size() + " independent repairs across the whole base, each needing its own claim");
	}

	@Override
	public byte[] transform(String className, byte[] classBytes, TransformContext context) {
		return transform(className, classBytes, context, ClaimReporter.NONE);
	}

	/** The repairs {@link #transform} runs, in its order; a test pins the two lists against each other. */
	static final List<String> REPAIRS = List.of("repairLambdaBootstrapHandles", "addBlockStateAppearanceResolver", "giveKeyMappingItsVanillaMap", "giveTheVanillaParticleMapAViewOfTheLiveOne", "restoreDoublePrecisionToTheRandomSources", "convertRadiansWithVanillasFoldedConstant", "callVanillasWriteByteAgain", "saveTheHeightmapsVanillaSaves", "guardNeoForgesWorldModifierPass", "letForeignResourceConditionsThrough", "letFabricResourceConditionsDecide", "translateAGuestsPrivateSkipMarker", "nameTheReloadListenersNeoForgeRefusesToName", "tolerateEmptyCreativeTabStacks", "bridgeOrphanedPipRenderers", "dropTheWindowTitlesLoaderBrand", "keepTheSaveOffTheTeardownsFailurePath", "vetoUnjudgeableOverlayConditions", "letModdedFeatureFlagsRegister", "wrapTheStreamsVanillaWraps");

	private static final String NEO_EVENT_HOOKS_BINARY = "net.neoforged.neoforge.event.EventHooks";

	/**
	 * One claim per repair, in {@link #REPAIRS} order. A repair with one fixed target declares it REQUIRED with
	 * the cost of its silence; one that scans by shape declares {@link AnchorSet#scanned}. Client-only targets
	 * are simply never loaded on a dedicated server, which the ledger reports as absent, not missed.
	 */
	@Override
	public List<Claim> claims() {
		List<Claim> out = new ArrayList<>();
		out.add(scanned("repairLambdaBootstrapHandles", "any class whose invokedynamic names a lambda handle that disagrees with the method it names"));
		out.add(scanned("addBlockStateAppearanceResolver",
				"net.minecraft.world.level.block.Block and ...block.state.BlockState, which each inherit "
						+ "getAppearance as a default from BOTH NeoForge and fabric-api and declare neither, so the "
						+ "first mod to ask a neighbour what it looks like — any connected-texture mod — dies on "
						+ "IncompatibleClassChangeError mid-frame"));
		out.add(fixed("giveKeyMappingItsVanillaMap", KEY_MAPPING,
				"KeyMapping has no vanilla-typed MAP — a mod reading it as a Map dies on NoSuchFieldError (LiquidBounce, on a key press)"));
		out.add(fixed("giveTheVanillaParticleMapAViewOfTheLiveOne", PARTICLE_RESOURCES,
				"the vanilla-typed particle provider map stays empty — particles registered the vanilla way never render"));
		out.add(randomSourcePrecisionEnabled()
				? new Claim(claimId("restoreDoublePrecisionToTheRandomSources"), AnchorSet.of(
						new AnchorSet.Anchor(XOROSHIRO_RANDOM_SOURCE.replace('/', '.'), AnchorSet.Severity.REQUIRED,
								"every noise octave's origin is off — the patched nextDouble() rounds through float, so no "
										+ "world generates the way the same seed does in vanilla"),
						new AnchorSet.Anchor(BIT_RANDOM_SOURCE.replace('/', '.'), AnchorSet.Severity.REQUIRED,
								"WorldgenRandom's nextDouble() rounds through float and can return exactly 1.0 — out of "
										+ "the [0,1) range every caller assumes")))
				: scanned("restoreDoublePrecisionToTheRandomSources", "-D" + RANDOM_PRECISION_PROPERTY + "=off"));
		out.add(fixed("convertRadiansWithVanillasFoldedConstant", "net/minecraft/world/entity/Entity",
				"every angle the game computes from a vector is off in the eighth digit — the patched base divides by "
						+ "pi at run time where vanilla multiplies by a constant it folded in float"));
		out.add(vanillaWriteByteEnabled()
				? fixed("callVanillasWriteByteAgain", PLAYER_ABILITIES_PACKET,
						"a packet writes its byte through NeoForge's writeByte(byte), so a mixin on vanilla's writeByte(int) "
								+ "there binds nothing — ViaFabricPlus' old-protocol ability flags, a required injector")
				: scanned("callVanillasWriteByteAgain", "-D" + VANILLA_WRITE_BYTE_PROPERTY + "=off"));
		out.add(savedHeightmapsEnabled()
				? fixed("saveTheHeightmapsVanillaSaves", CHUNK_STATUS,
						"an unfinished chunk is saved with the two worldgen heightmaps vanilla never persists, and "
								+ "reloads with them stale — a feature placed on WORLD_SURFACE_WG then lands somewhere "
								+ "vanilla would not put it")
				: scanned("saveTheHeightmapsVanillaSaves", "-D" + SAVED_HEIGHTMAPS_PROPERTY + "=off"));
		out.add(fixed("guardNeoForgesWorldModifierPass", NEO_SERVER_LIFECYCLE_HOOKS,
				"NeoForge's biome/structure modifier pass is neutered — every neoforge:biome_modifier does nothing"));
		out.add(fixed("letForeignResourceConditionsThrough", ICONDITION,
				"another ecosystem's condition type fails NeoForge's evaluator and the whole registry load with it"));
		out.add(fixed("letFabricResourceConditionsDecide", CONDITIONAL_OPS,
				"fabric:load_conditions has no evaluator — a Fabric mod's conditional data files all load"));
		out.add(fixed("translateAGuestsPrivateSkipMarker", JSON_RELOAD_LISTENER,
				"fabric-api's skip marker reaches the reader's cast — the datapack load dies (\"can't proceed with server load\")"));
		out.add(fixed("nameTheReloadListenersNeoForgeRefusesToName", ADD_CLIENT_RELOAD_LISTENERS,
				"a Fabric mod's client reload listener kills the client — NeoForge refuses to name it"));
		out.add(fixed("tolerateEmptyCreativeTabStacks", NEO_EVENT_HOOKS_BINARY.replace('.', '/'),
				"one empty stack from any mod aborts the whole creative menu"));
		out.add(fixed("bridgeOrphanedPipRenderers", GUI_RENDERER,
				"a picture-in-picture renderer registered the vanilla way never draws"));
		out.add(fixed("dropTheWindowTitlesLoaderBrand", "net/minecraft/client/Minecraft",
				"the window title carries another loader's brand"));
		out.add(fixed("keepTheSaveOffTheTeardownsFailurePath", INTEGRATED_SERVER,
				"a throw in IntegratedServer.teardownPublishedState costs the world save"));
		out.add(fixed("vetoUnjudgeableOverlayConditions", OVERLAY_ENTRY,
				"a pack.mcmeta overlay gated by a condition no evaluator here can judge is mounted anyway"));
		out.add(fixed("letModdedFeatureFlagsRegister", FEATURE_FLAGS,
				"NeoForge mods' declared feature flags are never registered — a mod asking for its own flag dies in its static "
						+ "initialiser and its datapack then fails the whole registry load"));
		out.add(fixed("wrapTheStreamsVanillaWraps", BOOTSTRAP,
				"System.out and System.err are never routed into log4j, so every line a mod PRINTS rather than logs "
						+ "is absent from latest.log — including the debug output a mod is told to turn on when it "
						+ "misbehaves"));
		return List.copyOf(out);
	}

	private Claim fixed(String repair, String internalTarget, String cost) {
		return new Claim(claimId(repair), AnchorSet.of(new AnchorSet.Anchor(internalTarget.replace('/', '.'), AnchorSet.Severity.REQUIRED, cost)));
	}

	private Claim scanned(String repair, String why) {
		return new Claim(claimId(repair), AnchorSet.scanned(why));
	}


	/** Reports {@code id} as applied when {@code applied}; the repair's own answer is returned unchanged. */
	private boolean claim(ClaimReporter reporter, String id, boolean applied) {
		if (applied) reporter.hit(claimId(id));
		return applied;
	}

	private String claimId(String repair) {
		return name() + "#" + repair;
	}

	@Override
	public byte[] transform(String className, byte[] classBytes, TransformContext context, ClaimReporter reporter) {
		if (classBytes == null || classBytes.length == 0) return classBytes;
		try {
			ClassNode node = new ClassNode();
			new ClassReader(classBytes).accept(node, 0);
			boolean changed = false;
			changed |= claim(reporter, "repairLambdaBootstrapHandles", repairLambdaBootstrapHandles(node));
			changed |= claim(reporter, "addBlockStateAppearanceResolver", addBlockStateAppearanceResolver(node));
			changed |= claim(reporter, "giveKeyMappingItsVanillaMap", giveKeyMappingItsVanillaMap(node));
			changed |= claim(reporter, "giveTheVanillaParticleMapAViewOfTheLiveOne", giveTheVanillaParticleMapAViewOfTheLiveOne(node));
			changed |= claim(reporter, "restoreDoublePrecisionToTheRandomSources", restoreDoublePrecisionToTheRandomSources(node));
			changed |= claim(reporter, "convertRadiansWithVanillasFoldedConstant", convertRadiansWithVanillasFoldedConstant(node));
			changed |= claim(reporter, "callVanillasWriteByteAgain", callVanillasWriteByteAgain(node));
			changed |= claim(reporter, "saveTheHeightmapsVanillaSaves", saveTheHeightmapsVanillaSaves(node));
			changed |= claim(reporter, "guardNeoForgesWorldModifierPass", guardNeoForgesWorldModifierPass(node));
			changed |= claim(reporter, "letForeignResourceConditionsThrough", letForeignResourceConditionsThrough(node));
			changed |= claim(reporter, "letFabricResourceConditionsDecide", letFabricResourceConditionsDecide(node));
			changed |= claim(reporter, "translateAGuestsPrivateSkipMarker", translateAGuestsPrivateSkipMarker(node));
			changed |= claim(reporter, "nameTheReloadListenersNeoForgeRefusesToName", nameTheReloadListenersNeoForgeRefusesToName(node));
			changed |= claim(reporter, "tolerateEmptyCreativeTabStacks", tolerateEmptyCreativeTabStacks(node));
			changed |= claim(reporter, "bridgeOrphanedPipRenderers", bridgeOrphanedPipRenderers(node));
			changed |= claim(reporter, "dropTheWindowTitlesLoaderBrand", dropTheWindowTitlesLoaderBrand(node));
			changed |= claim(reporter, "keepTheSaveOffTheTeardownsFailurePath", keepTheSaveOffTheTeardownsFailurePath(node));
			changed |= claim(reporter, "vetoUnjudgeableOverlayConditions", vetoUnjudgeableOverlayConditions(node));
			changed |= claim(reporter, "letModdedFeatureFlagsRegister", letModdedFeatureFlagsRegister(node));
			changed |= claim(reporter, "wrapTheStreamsVanillaWraps", wrapTheStreamsVanillaWraps(node));

			byte[] result = classBytes;
			if (changed) {
				ClassWriter writer = new ClassWriter(0);
				node.accept(writer);
				result = writer.toByteArray();
			}
			return result;
		} catch (RuntimeException e) {
			ForbricLog.warn("[Forbric/MergedBaseCompat] could not inspect " + className, e);
			return classBytes;
		}
	}

	private static final String KEY_MAPPING = "net/minecraft/client/KeyMapping";

	private static final String PARTICLE_RESOURCES = "net/minecraft/client/particle/ParticleResources";

	private static final String KERNEL_NEO_WORLDGEN = "net/forbric/kernel/runtime/KernelNeoWorldgen";
	private static final String NEO_SERVER_LIFECYCLE_HOOKS = "net/neoforged/neoforge/server/ServerLifecycleHooks";
	private static final String RUN_MODIFIERS = "(Lnet/minecraft/server/MinecraftServer;)V";

	private static final String ICONDITION = ForeignType.ICONDITION.internal(Ecosystem.NEOFORGE);
	private static final String CODEC_DESC = "Lcom/mojang/serialization/Codec;";
	private static final String KERNEL_NEO_CONDITIONS = "net/forbric/kernel/runtime/KernelNeoConditions";
	static final String CODEC_TO_CODEC = "(Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/Codec;";
	private static final String BOOTSTRAP = "net/minecraft/server/Bootstrap";
	private static final String JSON_RELOAD_LISTENER = "net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener";
	private static final String DATA_RESULT = "Lcom/mojang/serialization/DataResult;";

	private static final String CONDITIONAL_OPS = "net/neoforged/neoforge/common/conditions/ConditionalOps";
	private static final String CONDITIONAL_FACTORY =
			"(Lcom/mojang/serialization/Codec;Ljava/lang/String;)Lcom/mojang/serialization/Codec;";
	private static final String KERNEL_FABRIC_CONDITIONS = "net/forbric/kernel/runtime/KernelFabricConditions";

	private static final String ADD_CLIENT_RELOAD_LISTENERS =
			"net/neoforged/neoforge/client/event/AddClientReloadListenersEvent";
	private static final String VANILLA_CLIENT_LISTENERS =
			"net/neoforged/neoforge/client/resources/VanillaClientListeners";
	private static final String NAME_FOR_CLASS =
			"(Ljava/lang/Class;)Lnet/minecraft/resources/Identifier;";
	private static final String KERNEL_RELOAD_NAMES = "net/forbric/kernel/runtime/KernelClientReloadNames";
	private static final String NAME_KEYED = "Ljava/util/Map;";
	/** Vanilla's own descriptor for it, and the one fabric-api reads. */
	private static final String ID_KEYED = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;";
	private static final String KERNEL_PARTICLES = "net/forbric/kernel/runtime/KernelParticleProviders";
	private static final String KERNEL_KEY_MAPPING_MAP = "net/forbric/kernel/runtime/KernelKeyMappingMap";

	private static boolean repairLambdaBootstrapHandles(ClassNode node) {
		Map<String, MethodNode> methods = new HashMap<>();
		for (MethodNode method : node.methods) {
			methods.put(method.name + method.desc, method);
		}

		boolean changed = false;
		for (MethodNode caller : node.methods) {
			for (AbstractInsnNode insn = caller.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (!(insn instanceof InvokeDynamicInsnNode indy) || indy.bsmArgs == null) continue;
				for (int i = 0; i < indy.bsmArgs.length; i++) {
					if (!(indy.bsmArgs[i] instanceof Handle handle)) continue;
					Handle repaired = repairLambdaHandle(node, methods, caller, indy, handle);
					if (repaired == handle) continue;
					indy.bsmArgs[i] = repaired;
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Gives {@code BlockState} its own {@code getAppearance}, because it inherits TWO.
	 *
	 * <p>The patched class declares {@code IBlockStateExtension} (NeoForge); fabric-api's mixin then adds
	 * {@code FabricBlockState}. NeoForge's and Fabric's both
	 * carry a {@code default getAppearance} with a byte-identical descriptor, neither overrides the other, and
	 * the class declares nothing — so the JVM refuses to choose and the FIRST caller dies:
	 * <pre>
	 * java.lang.IncompatibleClassChangeError: Conflicting default methods:
	 *   net/neoforged/neoforge/common/extensions/IBlockStateExtension.getAppearance
	 *   net/fabricmc/fabric/api/block/v1/FabricBlockState.getAppearance
	 *   at BlockState.getAppearance
	 *   at me.pepperbell.continuity.client.model.CtmBlockStateModel.emitQuads
