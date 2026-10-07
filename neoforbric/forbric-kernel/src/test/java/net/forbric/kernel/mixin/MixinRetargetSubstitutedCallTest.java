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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import net.forbric.api.Ecosystem;

/**
 * R6: an {@code @Inject} anchored on a call the carrier substituted in place follows it, only along a row of
 * {@link MergedBaseCalleeSwaps#SUBSTITUTED}, and runs behind a guard that keeps a {@code LinkageError} out of the
 * method it moved into. Real names in the fixtures, because only the row authorizes the move: the merged
 * {@code ModelManager.lambda$loadBlockModels$2} parses each block-model file through NeoForge's
 * {@code UnbakedModelParser.parse} where vanilla and MinecraftForge call {@code CuboidModel.fromStream}.
 */
class MixinRetargetSubstitutedCallTest {
	private static final String MANAGER = "net/minecraft/client/resources/model/ModelManager";
	private static final String ENTRY = "Ljava/util/Map$Entry;";
	private static final String IDENTIFIER = "Lnet/minecraft/resources/Identifier;";
	private static final String READER = "Ljava/io/Reader;";
	private static final String LAMBDA = "lambda$loadBlockModels$2(" + ENTRY + ")Lcom/mojang/datafixers/util/Pair;";
	private static final String FROM_STREAM = "Lnet/minecraft/client/resources/model/cuboid/CuboidModel;fromStream(Ljava/io/Reader;)"
			+ "Lnet/minecraft/client/resources/model/cuboid/CuboidModel;";
	private static final String PARSE = "Lnet/neoforged/neoforge/client/model/UnbakedModelParser;parse(Ljava/io/Reader;)"
			+ "Lnet/minecraft/client/resources/model/UnbakedModel;";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String MIXIN = "test/ModelManagerMixin";
	/** fusion's handler: the lambda's entry, the callback, and the model id captured from the lambda's locals. */
	private static final String CAPTURING = "(" + ENTRY + MixinRetarget.CALLBACK_INFO_RETURNABLE + IDENTIFIER + ")V";
	private static final String PLAIN = "(" + ENTRY + MixinRetarget.CALLBACK_INFO_RETURNABLE + ")V";

	private static final Path SWEEP = Path.of("build/compat-inputs/sweep90/mods");
	private static final Path MERGED = TestFixtures.stagedRoot().resolve("neoforge-base/patched-mc-neoforge-26.2.jar");

	@AfterEach
	void reset() {
		System.clearProperty(MixinRetarget.PROPERTY);
		System.clearProperty(MixinRetarget.SUBSTITUTED_CALL_PROPERTY);
		System.clearProperty(MixinRetarget.SUBSTITUTED_CALL_GUARD_PROPERTY);
		MixinRetarget.reset();
		MixinStubRebind.forget();
	}

	@Test
	void fusionsModelIdCaptureFollowsTheParserNeoForgeSubstituted() {
		MixinStubRebind.noteEcosystem(MIXIN, Ecosystem.NEOFORGE);
		Function<String, byte[]> resolver = resolver(manager(true, "modelId"));
		byte[] mixin = mixin(INJECT, CAPTURING, "BEFORE", true);
		assertEquals(MixinFit.Verdict.PARTIAL, MixinFit.evaluate(mixin, resolver).verdict(), "premise: fromStream is not there");

		MixinRetarget.Plan plan = MixinRetarget.plan(MixinFit.parse(mixin), resolver);
		assertEquals(2, plan.rewrites().size(), plan.describe());
		MixinRetarget.Rewrite point = plan.rewrites().get(0);
		assertEquals(MixinRetarget.Element.AT_TARGET, point.element());
		assertEquals(FROM_STREAM, point.from());
		assertEquals(PARSE, point.to());
		assertEquals(MixinRetarget.Element.GUARD, plan.rewrites().get(1).element());
		assertEquals(MixinFit.Verdict.FIT, MixinFit.evaluate(MixinRetarget.rewritten(mixin, plan), resolver).verdict());

		MixinStubRebind.noteEcosystem(MIXIN, Ecosystem.FABRIC);
		assertEquals(2, MixinRetarget.plan(MixinFit.parse(mixin), resolver).rewrites().size(), "vanilla calls fromStream too");
		MixinStubRebind.noteEcosystem(MIXIN, Ecosystem.NEOFORGE);
		assertTrue(MixinRetarget.plan(MixinFit.parse(mixin), resolver).isEmpty(), "NeoForge's mods were compiled against parse");
		MixinStubRebind.forget();
		assertTrue(MixinRetarget.plan(MixinFit.parse(mixin), resolver).isEmpty(), "no known owner: no move");
	}

	/** The served node: the annotation keeps the handler's name and moves to the guard; the body is renamed aside. */
	@Test
	void theServedNodeCarriesTheMovedPointOnTheGuard() throws Exception {
		MixinStubRebind.noteEcosystem(MIXIN, Ecosystem.NEOFORGE);
		byte[] mixin = mixin(INJECT, CAPTURING, "BEFORE", true);
		MixinRetarget.remember(MixinRetarget.plan(MixinFit.parse(mixin), resolver(manager(true, "modelId"))));
		ClassNode node = new ClassNode();
		new ClassReader(mixin).accept(node, ClassReader.EXPAND_FRAMES);
		assertEquals(2, MixinRetarget.applyRemembered(MIXIN, node));

		MethodNode outer = method(node, "deserializeModel");
		MethodNode inner = method(node, MixinHandlerShim.asideName(MIXIN, "deserializeModel", MixinRetarget.GUARDED_SUFFIX));
		assertEquals(CAPTURING, outer.desc);
		assertEquals(CAPTURING, inner.desc);
		assertNull(MixinFit.injectorOf(inner), "the body carries no injector: Mixin merges it as a plain method");
		AnnotationNode inject = MixinFit.injectorOf(outer);
		assertEquals(PARSE, MixinFit.value(MixinFit.atNodes(inject).get(0), "target"));
		assertEquals(List.of(LAMBDA), MixinFit.stringList(MixinFit.value(inject, "method")), "the selector stays");
		new Analyzer<>(new BasicVerifier()).analyze(node.name, outer);
	}

	/** Only an @Inject: a @Redirect's handler IS the call, and parse is not fromStream. */
	@Test
	void aHandlerShapedByTheCallStays() {
		MixinStubRebind.noteEcosystem(MIXIN, Ecosystem.FABRIC);
		String redirect = "(" + READER + ")Lnet/minecraft/client/resources/model/cuboid/CuboidModel;";
		assertTrue(MixinRetarget.plan(MixinFit.parse(mixin(REDIRECT, redirect, null, false)),
				resolver(manager(true, "modelId"))).isEmpty());
	}

	/** Captured locals move only on the live table's word: a different local in the slot, or no table, and it stays. */
	@Test
	void capturedLocalsMustBeTheLiveMethods() {
		MixinStubRebind.noteEcosystem(MIXIN, Ecosystem.NEOFORGE);
		byte[] capturing = mixin(INJECT, CAPTURING, "BEFORE", true);
		assertTrue(MixinRetarget.plan(MixinFit.parse(capturing), resolver(manager(false, "modelId"))).isEmpty(), "no table");
		assertTrue(MixinRetarget.plan(MixinFit.parse(capturing), resolver(managerWithReaderInSlotOne())).isEmpty(),
				"slot 1 holds a Reader at the call, not the Identifier the handler takes");
		assertEquals(2, MixinRetarget.plan(MixinFit.parse(mixin(INJECT, PLAIN, "BEFORE", false)),
				resolver(manager(false, "modelId"))).rewrites().size(), "a handler that captures nothing needs no table");
		assertTrue(MixinRetarget.plan(MixinFit.parse(mixin(INJECT, CAPTURING, "BEFORE", false)),
				resolver(manager(true, "modelId"))).isEmpty(), "locals without a capture mode is not a handler Mixin accepts");
	}

	@Test
	void onlyBeforeOrAfterTheCallMoves() {
		MixinStubRebind.noteEcosystem(MIXIN, Ecosystem.NEOFORGE);
		Function<String, byte[]> resolver = resolver(manager(true, "modelId"));
		assertEquals(2, MixinRetarget.plan(MixinFit.parse(mixin(INJECT, PLAIN, null, false)), resolver).rewrites().size());
		assertEquals(2, MixinRetarget.plan(MixinFit.parse(mixin(INJECT, PLAIN, "AFTER", false)), resolver).rewrites().size());
		assertTrue(MixinRetarget.plan(MixinFit.parse(mixin(INJECT, PLAIN, "BY", false)), resolver).isEmpty());
	}

	@Test
	void theSwitches() {
		MixinStubRebind.noteEcosystem(MIXIN, Ecosystem.NEOFORGE);
		Function<String, byte[]> resolver = resolver(manager(true, "modelId"));
		byte[] mixin = mixin(INJECT, CAPTURING, "BEFORE", true);
		System.setProperty(MixinRetarget.SUBSTITUTED_CALL_GUARD_PROPERTY, "off");
		MixinRetarget.Plan unguarded = MixinRetarget.plan(MixinFit.parse(mixin), resolver);
		assertEquals(List.of(MixinRetarget.Element.AT_TARGET), unguarded.rewrites().stream().map(MixinRetarget.Rewrite::element).toList());
		System.clearProperty(MixinRetarget.SUBSTITUTED_CALL_GUARD_PROPERTY);
		System.setProperty(MixinRetarget.SUBSTITUTED_CALL_PROPERTY, "off");
		assertTrue(MixinRetarget.plan(MixinFit.parse(mixin), resolver).isEmpty());
		System.clearProperty(MixinRetarget.SUBSTITUTED_CALL_PROPERTY);
		System.setProperty(MixinRetarget.PROPERTY, "off");
		assertTrue(MixinRetarget.plan(MixinFit.parse(mixin), resolver).isEmpty());
	}

	/**
	 * The guard, run on a real JVM: a handler whose body does not link is skipped and said once, anything else it
	 * throws passes through as it would natively, and a body that links runs.
	 */
	@Test
	void theGuardSkipsAHandlerThatDoesNotLinkAndNothingElse() throws Exception {
		for (boolean isStatic : new boolean[] {true, false}) {
			MixinRetarget.reset();
			Class<?> guarded = define(guarded(isStatic));
			Object instance = isStatic ? null : guarded.getDeclaredConstructor().newInstance();
			Method handler = guarded.getDeclaredMethod("handler", String.class, long.class, Object.class);
			handler.setAccessible(true);

			handler.invoke(instance, "links", 7L, null);
			assertEquals("links7", guarded.getDeclaredField("seen").get(null), "a body that links runs, with every argument");

			handler.invoke(instance, "unlinked", 0L, null);
			assertTrue(MixinRetarget.skippedHooks().contains("test.GuardedMixin.handler"), "said once, naming the handler");
			handler.invoke(instance, "unlinked", 0L, null);

			InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
					() -> handler.invoke(instance, "throws", 0L, null));
			assertTrue(thrown.getCause() instanceof IllegalStateException, "only a LinkageError is the guard's business");
		}
	}

	/** fusion's real ModelManagerMixin against the real merged ModelManager: 7/8 anchors, then all of them. */
	@Test
	void fusionsRealMixinOnTheRealMergedBase() throws Exception {
		Path fusion = SWEEP.resolve("fusion-1.3.15a-forge-mc26.2.jar");
		TestFixtures.require(Fixture.STAGED, Files.isRegularFile(MERGED), "sweep pack or staged merged base absent");
		TestFixtures.require(Fixture.THIRD_PARTY, Files.isRegularFile(fusion), "sweep pack or staged merged base absent");
		byte[] mixin = read(fusion, "com/supermartijn642/fusion/mixin/ModelManagerMixin.class");
		Function<String, byte[]> resolver = name -> {
			try {
				return read(MERGED, name);
			} catch (Exception e) {
				return null;
			}
		};
		MixinStubRebind.noteEcosystem("com/supermartijn642/fusion/mixin/ModelManagerMixin", Ecosystem.NEOFORGE);
		MixinFit.Result raw = MixinFit.evaluate(mixin, resolver);
		assertEquals(MixinFit.Verdict.PARTIAL, raw.verdict(), "premise: " + raw.unresolved());
		assertTrue(raw.unresolved().stream().anyMatch(u -> u.contains("fromStream")), raw.unresolved().toString());

		MixinRetarget.Plan plan = MixinRetarget.plan(MixinFit.parse(mixin), resolver);
		assertEquals(List.of("deserializeModel:AT_TARGET", "deserializeModel:GUARD"),
				plan.rewrites().stream().map(r -> r.handler() + ":" + r.element()).toList(), plan.describe());
		assertEquals(PARSE, plan.rewrites().get(0).to());
		MixinFit.Result after = MixinFit.evaluate(MixinRetarget.rewritten(mixin, plan), resolver);
		assertEquals(MixinFit.Verdict.FIT, after.verdict(), "after: " + after.unresolved());

		// The node Mixin is served, through the adapters getClassNode runs after the plan, in their order: the capture is
		// softened on the guard that now carries it, and the stub rebind still moves the sprite capture beside it.
		MixinRetarget.remember(plan);
		ClassNode served = new ClassNode();
		new ClassReader(mixin).accept(served, ClassReader.EXPAND_FRAMES);
		MixinRetarget.applyRemembered(served.name, served);
		MixinAtShape.normalise(served);
		MixinLocalsCapture.soften(served);
		ClassNode manager = new ClassNode();
		new ClassReader(resolver.apply(MANAGER + ".class")).accept(manager, 0);
		MixinStubRebind.adapt(served, name -> MANAGER.equals(name) ? manager : null);
		MethodNode outer = method(served, "deserializeModel");
		AnnotationNode inject = MixinFit.injectorOf(outer);
		assertEquals(PARSE, MixinFit.value(MixinFit.atNodes(inject).get(0), "target"));
		assertEquals("CAPTURE_FAILSOFT", MixinFit.asString(MixinFit.value(inject, "locals")));
		assertNull(MixinFit.injectorOf(method(served, MixinHandlerShim.asideName(served.name, "deserializeModel", MixinRetarget.GUARDED_SUFFIX))));
		method(served, "captureBlockItemSprites" + MixinHandlerShim.INNER_SUFFIX);
		new Analyzer<>(new BasicVerifier()).analyze(served.name, outer);
		MethodNode live = manager.methods.stream().filter(m -> LAMBDA.equals(m.name + m.desc)).findFirst().orElseThrow();
		assertTrue(InsertedLambdaArgumentShim.localsAtCall(PARSE, live, new Type[] {Type.getType(IDENTIFIER)}),
				"the model id is the local fusion captures at the parse call");

		MixinStubRebind.noteEcosystem("com/supermartijn642/fusion/mixin/ModelManagerMixin", Ecosystem.NEOFORGE);
		assertTrue(MixinRetarget.plan(MixinFit.parse(mixin), resolver).rewrites().stream()
				.noneMatch(r -> r.element() == MixinRetarget.Element.AT_TARGET && PARSE.equals(r.to())));
	}

	// --- fixtures ---

	/**
	 * The merged lambda's shape where it matters: the model id in slot 1 and the reader in slot 2, then
	 * {@code parse(reader)} with the id under it, as NeoForge's body has them; {@code slotOneName} names slot 1 in the
	 * table.
	 */
	private static byte[] manager(boolean table, String slotOneName) {
		return manager(table, IDENTIFIER, slotOneName);
	}

	private static byte[] managerWithReaderInSlotOne() {
		return manager(true, READER, "reader");
	}

	private static byte[] manager(boolean table, String slotOneType, String slotOneName) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, MANAGER, null, "java/lang/Object", null);
		int paren = LAMBDA.indexOf('(');
		MethodVisitor m = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
				LAMBDA.substring(0, paren), LAMBDA.substring(paren), null, null);
		m.visitCode();
		Label start = new Label(), id = new Label(), reader = new Label(), end = new Label();
		m.visitLabel(start);
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 1);
		m.visitLabel(id);
		m.visitInsn(Opcodes.ACONST_NULL);
		m.visitVarInsn(Opcodes.ASTORE, 2);
		m.visitLabel(reader);
		m.visitVarInsn(Opcodes.ALOAD, 1);
		m.visitVarInsn(Opcodes.ALOAD, 2);
		MixinFit.Member parse = MixinFit.parseMember(PARSE);
		m.visitMethodInsn(Opcodes.INVOKESTATIC, parse.owner(), parse.name(), parse.desc(), false);
		m.visitMethodInsn(Opcodes.INVOKESTATIC, "com/mojang/datafixers/util/Pair", "of",
				"(Ljava/lang/Object;Ljava/lang/Object;)Lcom/mojang/datafixers/util/Pair;", false);
