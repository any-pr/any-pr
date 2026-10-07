package net.forbric.kernel.mixin;

import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.CompatibilityFindings;
import net.forbric.api.Ecosystem;

/** Fabric mods' injectors on the merged base's carrier stubs, as shipped, against the real merged classes. */
@ResourceLock("system-properties")
@ResourceLock("ModCatalog")
class MixinStubRebindTest {
	private static final Path MERGED = TestFixtures.stagedRoot().resolve("neoforge-base/patched-mc-neoforge-26.2.jar");
	private static final Path POPULAR = Path.of("run/client-popular/mods");
	private static final Path MERGED_PACK = Path.of("run/client-merged-pack/mods");

	@AfterEach void reset() {
		CompatibilityFindings.reset();
		System.clearProperty(MixinStubRebind.STUB_FINDING_PROPERTY);
		System.clearProperty(MixinStubRebind.PROPERTY);
		System.clearProperty(MixinStubRebind.MODIFY_VARIABLE_PROPERTY);
		System.clearProperty(MixinStubRebind.CAPTURES_PROPERTY);
		System.clearProperty(MixinStubRebind.SUGAR_BOUNDARY_PROPERTY);
		System.clearProperty(MixinStubRebind.FORGE_FAMILY_PROPERTY);
		System.clearProperty(MixinStubRebind.TYPED_LOCAL_PROPERTY);
		System.clearProperty(MixinStubRebind.SHARED_PROPERTY);
		System.clearProperty(MixinStubRebind.ALLOW_PROPERTY);
		MixinStubRebind.forget();
	}

	@Test void architecturysBreakSpeedMovesToTheBodyAndStillReceivesTheState() throws Exception {
		ClassNode mixin = fromJar(POPULAR.resolve("architectury-fabric-21.1.10.jar"), "dev/architectury/mixin/fabric/MixinPlayer");
		ClassNode player = merged("net/minecraft/world/entity/player/Player");
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.FABRIC);
		assertEquals(1, MixinStubRebind.adapt(mixin, name -> player));
		MethodNode outer = mixin.methods.stream().filter(m -> m.name.equals("breakSpeed")).findFirst().orElseThrow();
		assertEquals(List.of("getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F"),
				MixinFit.stringList(MixinFit.value(MixinFit.injectorOf(outer), "method")));
		assertTrue(outer.desc.startsWith("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
				+ "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;"), outer.desc);
		assertNull(MixinFit.injectorOf(mixin.methods.stream().filter(m -> m.name.equals("breakSpeed" + MixinHandlerShim.INNER_SUFFIX)).findFirst().orElseThrow()));
		List<Integer> loads = Arrays.stream(outer.instructions.toArray()).filter(VarInsnNode.class::isInstance).map(i -> ((VarInsnNode) i).var).toList();
		assertEquals(List.of(0, 1, 3), loads, "this, the state (the stub passes its only argument first), the callback");
		new Analyzer<>(new BasicVerifier()).analyze(mixin.name, outer);
		assertEquals(0, MixinStubRebind.adapt(mixin, name -> player), "a second pass changes nothing");
	}

	/**
	 * MixinOverloadPin and this rebind, composed (issue #56's pin meeting a carrier stub). The merged {@code AxeItem}
	 * declares the carrier's five-argument {@code evaluateNewBlockState} body FIRST and keeps vanilla's four-argument
	 * signature after it, as a stub forwarding to it. A Fabric mod's bare-name HEAD hook written for vanilla's four
	 * arguments binds the body and fails the whole mixin; nothing here moves it, because a bare name lands on the body.
	 * The pin names vanilla's stub, and the rebind then carries it onto the body that runs, handing over the stub's
	 * arguments.
	 */
	@Test void aBareNameThePinPointsAtAStubThenMovesOntoTheBody() throws Exception {
		ClassNode axe = merged("net/minecraft/world/item/AxeItem");
		String args = "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;"
				+ "Lnet/minecraft/world/level/block/state/BlockState;";
		String stub = "evaluateNewBlockState(" + args + ")Ljava/util/Optional;";
		String body = "evaluateNewBlockState(" + args + "Lnet/minecraft/world/item/context/UseOnContext;)Ljava/util/Optional;";
		String handler = "(" + args + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";

		ClassNode alone = synthetic("com/example/AxeStripMixin", "net/minecraft/world/item/AxeItem", "onStrip", handler, false,
				injector("Lorg/spongepowered/asm/mixin/injection/Inject;", "evaluateNewBlockState", at("HEAD")));
		MixinStubRebind.noteEcosystem(alone.name, Ecosystem.FABRIC);
		assertEquals(0, MixinStubRebind.adapt(alone, name -> axe), "a bare name binds the body, so there is no stub to leave");

		ClassNode mixin = synthetic("com/example/AxeStripMixin", "net/minecraft/world/item/AxeItem", "onStrip", handler, false,
				injector("Lorg/spongepowered/asm/mixin/injection/Inject;", "evaluateNewBlockState", at("HEAD")));
		assertEquals(1, MixinOverloadPin.pin(mixin, name -> axe));
		assertEquals(List.of(stub), selectors(mixin, "onStrip"));
		assertEquals(1, MixinStubRebind.adapt(mixin, name -> axe));
		assertEquals(List.of(body), selectors(mixin, "onStrip"));
	}

	/**
	 * The same composition as MixinFit judges it, before anything is applied: an anchor in the body counts. Asked
	 * about the bare name, the rebind sees the body Mixin binds first, moves nothing, and the anchor read as missing
	 * in vanilla's stub -- a one-injector mixin would have been removed as UNFIT before the pin and the rebind could
	 * repair it.
	 */
	@Test void mixinFitJudgesThePinnedThenRebasedInjectorInTheBody() throws Exception {
		ClassNode axe = merged("net/minecraft/world/item/AxeItem");
		org.objectweb.asm.ClassWriter axeWriter = new org.objectweb.asm.ClassWriter(0);
		axe.accept(axeWriter);
		byte[] axeBytes = axeWriter.toByteArray();
		String args = "Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;"
				+ "Lnet/minecraft/world/level/block/state/BlockState;";
		ClassNode mixin = synthetic("com/example/AxeStripSoundMixin", "net/minecraft/world/item/AxeItem", "onStripSound",
				"(" + args + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", false,
				injector("Lorg/spongepowered/asm/mixin/injection/Inject;", "evaluateNewBlockState", at("INVOKE", "target",
						"Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;"
								+ "Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V")));
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.FABRIC);
		org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
		mixin.accept(writer);
		MixinFit.Result fit = MixinFit.evaluate(writer.toByteArray(),
				name -> name.equals("net/minecraft/world/item/AxeItem.class") ? axeBytes : null);
		assertEquals(MixinFit.Verdict.FIT, fit.verdict(), fit.toString());
	}

	@Test void fabricApisElytraCheckMovesWhereItsFieldReadIs() throws Exception {
		ClassNode mixin = StagedFabricMixinFixture.mixin("fabric-entity-events-v1", "net/fabricmc/fabric/mixin/entity/event/elytra/LivingEntityMixin");
		ClassNode living = merged("net/minecraft/world/entity/LivingEntity");
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.FABRIC);
		assertEquals(1, MixinStubRebind.adapt(mixin, name -> living));
		MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("injectElytraCheck")).findFirst().orElseThrow();
		assertEquals(List.of("canGlide(Z)Z"), MixinFit.stringList(MixinFit.value(MixinFit.injectorOf(handler), "method")));
	}

	@Test void aPairVanillaAlreadyHasStaysWhereTheModPutIt() throws Exception {
		TestFixtures.requireDirectory(Fixture.THIRD_PARTY, "local merged mod pack", MERGED_PACK);
		Path xaero;
		try (var files = Files.list(MERGED_PACK)) {
			xaero = files.filter(p -> p.getFileName().toString().contains("xaerominimap-fabric")).findFirst().orElse(null);
		}
		TestFixtures.require(Fixture.THIRD_PARTY, xaero != null, "Xaero's Minimap (Fabric) fixture absent");
		ClassNode mixin = fromJar(xaero, "xaero/common/mixin/MixinFabricMinecraftClient");
		ClassNode minecraft = merged("net/minecraft/client/Minecraft");
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.FABRIC);
		assertEquals(0, MixinStubRebind.adapt(mixin, name -> minecraft),
				"vanilla's own disconnect(Screen, boolean) forwards to its three-argument overload: the mod chose it");
	}

	@Test void aNeoForgeModsMixinIsLeftAsNeoForgeWouldHaveIt() throws Exception {
		ClassNode mixin = fromJar(POPULAR.resolve("architectury-fabric-21.1.10.jar"), "dev/architectury/mixin/fabric/MixinPlayer");
		ClassNode player = merged("net/minecraft/world/entity/player/Player");
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.NEOFORGE);
		assertEquals(0, MixinStubRebind.adapt(mixin, name -> player), "compiled against the stub-first shape: native behaviour");
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.NEOFORGE);
		assertEquals(0, MixinStubRebind.adapt(mixin, name -> player), "MinecraftForge keeps the same stub: native behaviour too");
		MixinStubRebind.forget();
		assertEquals(0, MixinStubRebind.adapt(mixin, name -> player), "no known owner: no move");
	}

	// --- a Forge-family mod on the other carrier's stub ---

	private static final Path SWEEP = Path.of("build/compat-inputs/sweep90/mods");
	private static final String MODEL_MANAGER = "net/minecraft/client/resources/model/ModelManager";
	private static final String LOAD_MODELS_BODY = "loadModels(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;"
			+ "Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;Lnet/minecraft/client/resources/model/ModelBakery;"
			+ "Lnet/minecraft/client/renderer/block/LoadedBlockModels;Lit/unimi/dsi/fastutil/objects/Object2IntMap;"
			+ "Lnet/minecraft/client/model/geom/EntityModelSet;Ljava/util/concurrent/Executor;"
			+ "Lnet/neoforged/neoforge/client/entity/animation/json/AnimationLoader$PendingAnimations;)Ljava/util/concurrent/CompletableFuture;";

	/**
	 * fusion is a MinecraftForge mod. MinecraftForge's ModelManager has one loadModels, the body; NeoForge added an
	 * overload and left the seven-argument one as a stub nothing calls. fusion's name-only HEAD capture of the block
	 * atlas bound that stub, its static stayed null, and every model bake threw on it (35,845 "Unable to bake model").
	 */
	@Test void fusionsSpriteCaptureMovesToTheBodyMinecraftForgeRan() throws Exception {
		ClassNode mixin = fromJar(SWEEP.resolve("fusion-1.3.15a-forge-mc26.2.jar"), "com/supermartijn642/fusion/mixin/ModelManagerMixin");
		ClassNode manager = merged(MODEL_MANAGER);
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.NEOFORGE);
		MixinStubRebind.adapt(mixin, name -> manager);
		MethodNode outer = mixin.methods.stream().filter(m -> m.name.equals("captureBlockItemSprites")).findFirst().orElseThrow();
		assertEquals(List.of(LOAD_MODELS_BODY), MixinFit.stringList(MixinFit.value(MixinFit.injectorOf(outer), "method")));
		assertNull(MixinFit.injectorOf(mixin.methods.stream().filter(m -> m.name.equals("captureBlockItemSprites" + MixinHandlerShim.INNER_SUFFIX))
				.findFirst().orElseThrow()));
		List<Integer> loads = Arrays.stream(outer.instructions.toArray()).filter(VarInsnNode.class::isInstance).map(i -> ((VarInsnNode) i).var).toList();
		assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 8), loads, "the stub's seven arguments, then the callback past the pending animations");
		new Analyzer<>(new BasicVerifier()).analyze(mixin.name, outer);

		for (Ecosystem stays : List.of(Ecosystem.NEOFORGE, Ecosystem.NEOFORGE)) {
			ClassNode again = fromJar(SWEEP.resolve("fusion-1.3.15a-forge-mc26.2.jar"), "com/supermartijn642/fusion/mixin/ModelManagerMixin");
			MixinStubRebind.noteEcosystem(again.name, stays);
			if (stays == Ecosystem.NEOFORGE) System.setProperty(MixinStubRebind.FORGE_FAMILY_PROPERTY, "off");
			MixinStubRebind.adapt(again, name -> manager);
			assertEquals(List.of("loadModels"), selectors(again, "captureBlockItemSprites"),
					stays == Ecosystem.NEOFORGE ? "the switch: Fabric mods only" : "a NeoForge mod was compiled against that very stub");
			System.clearProperty(MixinStubRebind.FORGE_FAMILY_PROPERTY);
		}
	}

	private static final String DISCOVER_STUB = "discoverModelDependencies(Ljava/util/Map;Lnet/minecraft/client/resources/model/BlockStateModelLoader$LoadedModels;"
			+ "Lnet/minecraft/client/resources/model/ClientItemInfoLoader$LoadedClientInfos;)Lnet/minecraft/client/resources/model/ModelManager$ResolvedModels;";
	private static final String DISCOVER_BODY = "discoverModelDependencies(Ljava/util/Map;Lnet/minecraft/client/resources/model/BlockStateModelLoader$LoadedModels;"
			+ "Lnet/minecraft/client/resources/model/ClientItemInfoLoader$LoadedClientInfos;"
			+ "Lnet/neoforged/neoforge/client/model/standalone/StandaloneModelLoader$LoadedModels;)Lnet/minecraft/client/resources/model/ModelManager$ResolvedModels;";

	/**
	 * fusion's overlay models are added as discovery roots through the ModelDiscovery the method builds, taken by a
	 * @Local with nothing but its type. The ResolvedModels construction it anchors on is only in NeoForge's overload,
	 * where `result` is the one ModelDiscovery live there.
	 */
	@Test void fusionsOverlayHookMovesWithTheOnlyModelDiscoveryInTheBody() throws Exception {
		ClassNode manager = merged(MODEL_MANAGER);
		ClassNode mixin = fromJar(SWEEP.resolve("fusion-1.3.15a-forge-mc26.2.jar"), "com/supermartijn642/fusion/mixin/ModelManagerMixin");
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.NEOFORGE);
		assertEquals(2, MixinStubRebind.adapt(mixin, name -> manager), "the sprite capture and the overlay hook");
		assertEquals(List.of(DISCOVER_BODY), selectors(mixin, "registerBlockModelOverlays"));

		System.setProperty(MixinStubRebind.TYPED_LOCAL_PROPERTY, "off");
		ClassNode off = fromJar(SWEEP.resolve("fusion-1.3.15a-forge-mc26.2.jar"), "com/supermartijn642/fusion/mixin/ModelManagerMixin");
		MixinStubRebind.adapt(off, name -> manager);
		assertEquals(List.of(DISCOVER_STUB), selectors(off, "registerBlockModelOverlays"), "the switch");
	}

	/**
	 * supermartijn642corelib (Fabric) hooks the same method before ModelDiscovery.missingModel, capturing the stub's
	 * three arguments and the ModelDiscovery by type: wrapped, the @Local stays on the outer's last parameter.
	 */
	@Test void coreLibsModelHookMovesWrappedWithItsByTypeLocal() throws Exception {
		ClassNode manager = merged(MODEL_MANAGER);
		ClassNode mixin = fromJar(SWEEP.resolve("supermartijn642corelib-1.1.24b-fabric-mc26.2.jar"), "com/supermartijn642/core/mixin/ModelManagerMixin");
		MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.FABRIC);
		assertEquals(1, MixinStubRebind.adapt(mixin, name -> manager));
		MethodNode outer = mixin.methods.stream().filter(m -> m.name.equals("discoverModelDependencies")).findFirst().orElseThrow();
		assertEquals(List.of(DISCOVER_BODY), MixinFit.stringList(MixinFit.value(MixinFit.injectorOf(outer), "method")));
		Type[] params = Type.getArgumentTypes(outer.desc);
		assertEquals("Lnet/minecraft/client/resources/model/ModelDiscovery;", params[params.length - 1].getDescriptor());
		assertTrue(MixinStubRebind.annotated(outer, params.length - 1), "the @Local moved with its parameter");
		new Analyzer<>(new BasicVerifier()).analyze(mixin.name, outer);
	}

	/** A second ModelDiscovery live at the anchor, or the one there unnamed by the table: MixinExtras' pick is not the proof's. */
	@Test void aByTypeLocalIsLeftWhereTheBodyDoesNotDecideIt() throws Exception {
		String discovery = "Lnet/minecraft/client/resources/model/ModelDiscovery;";
		for (String why : List.of("a second ModelDiscovery slot", "the one slot unnamed", "no local variable table")) {
			ClassNode manager = merged(MODEL_MANAGER);
			MethodNode body = manager.methods.stream().filter(m -> DISCOVER_BODY.equals(m.name + m.desc)).findFirst().orElseThrow();
			switch (why) {
				case "a second ModelDiscovery slot" -> {
					int slot = body.maxLocals;
					body.maxLocals++;
					org.objectweb.asm.tree.LabelNode start = new org.objectweb.asm.tree.LabelNode(), end = new org.objectweb.asm.tree.LabelNode();
					body.instructions.insert(start);
					body.instructions.insert(new VarInsnNode(Opcodes.ASTORE, slot));
					body.instructions.insert(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/client/resources/model/ModelDiscovery"));
					body.instructions.insert(new org.objectweb.asm.tree.InsnNode(Opcodes.ACONST_NULL));
					body.instructions.add(end);
					body.localVariables.add(new org.objectweb.asm.tree.LocalVariableNode("other", discovery, null, start, end, slot));
				}
				case "the one slot unnamed" -> body.localVariables.removeIf(l -> l.desc.equals(discovery));
				default -> body.localVariables = null;
			}
			ClassNode mixin = fromJar(SWEEP.resolve("fusion-1.3.15a-forge-mc26.2.jar"), "com/supermartijn642/fusion/mixin/ModelManagerMixin");
			MixinStubRebind.noteEcosystem(mixin.name, Ecosystem.NEOFORGE);
			MixinStubRebind.adapt(mixin, name -> manager);
			assertEquals(List.of(DISCOVER_STUB), selectors(mixin, "registerBlockModelOverlays"), why);
		}
	}

	/**
	 * What MixinExtras' implicit @Local counts, as Mixin's own Locals.getLocalsAt reports it on these javac shapes: each
	 * slot typed by its table entry and carried down the method from above. {@code List x = new ArrayList()} beside
	 * {@code List y} is two Lists to it, and so is an x a block, a loop or a catch above left behind; on two it fails
	 * the injection, and the mixin with it. The data flow at the anchor saw only y in each. An argument counts, never
	 * read or not; {@code this} never does: an instance method's own class is no candidate there, and one copy of it is
	 * the only one.
	 */
	@Test void aByTypeLocalCountsWhatMixinExtrasCounts() throws Exception {
		ClassNode shapes = new ClassNode();
		try (java.io.InputStream in = TypedLocals.class.getResourceAsStream("MixinStubRebindTest$TypedLocals.class")) {
			new ClassReader(in).accept(shapes, 0);
		}
		List<AnnotationNode> yield = List.of(at("INVOKE", "target", "Ljava/lang/Thread;yield()V"));
		java.util.Map<String, Boolean> decided = new java.util.TreeMap<>();
		for (MethodNode shape : shapes.methods) {
			if (shape.name.startsWith("<")) continue;
			Type wanted = Type.getType((shape.access & Opcodes.ACC_STATIC) != 0 ? List.class : TypedLocals.class);
			decided.put(shape.name, MixinStubRebind.theOnlyLocalOfItsType(shapes, shape, wanted, yield));
		}
		assertEquals(java.util.Map.of("twoLists", false, "anArrayListAndAList", true, "aBlockAbove", false, "aLoopAbove", false,
				"aCatchAbove", false, "aSlotReused", true, "onlyItself", false, "itselfAndACopy", true, "anArgument", true,
				"anArgumentAndALocal", false), decided);
	}

	/** javac's shapes around a {@code Thread.yield()} anchor, for a by-type {@code @Local List} (or TypedLocals, in its own methods). */
	@SuppressWarnings({"rawtypes", "unused"})
	private static final class TypedLocals {
		static void twoLists() { List x = new java.util.ArrayList(); List y = List.of(); Thread.yield(); x.size(); y.size(); }
		static void anArrayListAndAList() { java.util.ArrayList x = new java.util.ArrayList(); List y = List.of(); Thread.yield(); x.size(); y.size(); }
		static void aBlockAbove() { List y = List.of(); { List x = new java.util.ArrayList(); x.size(); } Thread.yield(); y.size(); }
		static void aLoopAbove() { List y = List.of(); for (int i = 0; i < 3; i++) { List x = new java.util.ArrayList(); x.size(); } Thread.yield(); y.size(); }
