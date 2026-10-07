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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import net.forbric.api.Ecosystem;
import net.forbric.api.ModCatalog;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * The adapter must drop a guest mixin exactly when the members it names no longer exist on the merged target —
 * not because of who owns the target class. Targets and mixins are synthesized with ASM so the test does not depend
 * on a real fabric-api jar or a real merged base.
 *
 * <p>The two cases that decide correctness, both measured on the real client set before being written down here:
 * the orphaned-{@code @Shadow} archetype MUST be caught ({@code GuiRenderer.pictureInPictureRenderers} — declared,
 * never assigned), and a PUBLIC field written by some other class MUST NOT be
 * ({@code MovingBlockRenderState.biome} — a render-state DTO, the shape renderer mods target most).
 */
@org.junit.jupiter.api.parallel.ResourceLock("ModCatalog")
class KernelGuestMixinAdapterTest {
	@org.junit.jupiter.api.BeforeEach
	@org.junit.jupiter.api.AfterEach
	void clearCompatibilityEvidence() { net.forbric.api.CompatibilityFindings.reset(); }

	private static final String PKG = "net/example/mixin";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";

	// --- synthetic merged-base targets -------------------------------------------------------------------------

	/**
	 * A target declaring {@code field} of type {@code Ljava/util/Map;}, assigned in {@code <init>} only when
	 * {@code assigned}. Unassigned + private is the orphaned archetype.
	 */
	private static byte[] target(String internalName, String field, int access, boolean assigned) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
		cw.visitField(access, field, "Ljava/util/Map;", null, null).visitEnd();

		MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		ctor.visitCode();
		ctor.visitVarInsn(Opcodes.ALOAD, 0);
		ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		if (assigned) {
			ctor.visitVarInsn(Opcodes.ALOAD, 0);
			ctor.visitInsn(Opcodes.ACONST_NULL);
			ctor.visitFieldInsn(Opcodes.PUTFIELD, internalName, field, "Ljava/util/Map;");
		}
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(3, 1);
		ctor.visitEnd();

		// A method the mixins below can anchor an @Inject on.
		MethodVisitor render = cw.visitMethod(Opcodes.ACC_PUBLIC, "render", "()V", null, null);
		render.visitCode();
		render.visitInsn(Opcodes.RETURN);
		render.visitMaxs(0, 1);
		render.visitEnd();

		cw.visitEnd();
		return cw.toByteArray();
	}

	// --- synthetic guest mixins --------------------------------------------------------------------------------

	private static ClassWriter beginMixin(String simpleName, String targetInternalName, String... interfaces) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, PKG + "/" + simpleName, null, "java/lang/Object",
				interfaces.length == 0 ? null : interfaces);
		AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor arr = mixin.visitArray("value");
		arr.visit(null, Type.getObjectType(targetInternalName));
		arr.visitEnd();
		mixin.visitEnd();
		return cw;
	}

	/** A mixin that {@code @Shadow}s one Map field and injects into {@code render}. */
	private static byte[] shadowingMixin(String simpleName, String target, String field, String... interfaces) {
		ClassWriter cw = beginMixin(simpleName, target, interfaces);
		FieldVisitor fv = cw.visitField(Opcodes.ACC_PRIVATE, field, "Ljava/util/Map;", null, null);
		fv.visitAnnotation(SHADOW, false).visitEnd();
		fv.visitEnd();

		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onRender", "()V", null, null);
		AnnotationVisitor inject = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, "render");
		methods.visitEnd();
		inject.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 1);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A mixin whose @Inject names a method the target does not have. */
	private static byte[] danglingMixin(String simpleName, String target) {
		ClassWriter cw = beginMixin(simpleName, target);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onGone", "()V", null, null);
		AnnotationVisitor inject = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, "methodThatNoLongerExists");
		methods.visitEnd();
		inject.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 1);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** One anchor resolves ({@code render}) and one does not ({@code methodThatNoLongerExists}) — PARTIAL. */
	private static byte[] halfMixin(String simpleName, String target) {
		ClassWriter cw = beginMixin(simpleName, target);
		for (String[] spec : new String[][] {{"onRender", "render"}, {"onGone", "methodThatNoLongerExists"}}) {
			MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, spec[0], "()V", null, null);
			AnnotationVisitor inject = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
			AnnotationVisitor methods = inject.visitArray("method");
			methods.visit(null, spec[1]);
			methods.visitEnd();
			inject.visitEnd();
			mv.visitCode();
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(0, 1);
			mv.visitEnd();
		}
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A mixin whose one injector lists SEVERAL candidate selectors — Mixin's require=1 alternatives idiom. */
	private static byte[] multiSelectorMixin(String simpleName, String target, String... selectors) {
		ClassWriter cw = beginMixin(simpleName, target);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onEither", "()V", null, null);
		AnnotationVisitor inject = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
		AnnotationVisitor methods = inject.visitArray("method");
		for (String s : selectors) methods.visit(null, s);
		methods.visitEnd();
		inject.visitEnd();
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 1);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** A mixin that casts its target to {@code contract} — the dependent half of a cast contract. */
	private static byte[] castingMixin(String simpleName, String target, String contract) {
		ClassWriter cw = beginMixin(simpleName, target);
		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onRender", "()V", null, null);
		AnnotationVisitor inject = mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
		AnnotationVisitor methods = inject.visitArray("method");
		methods.visit(null, "render");
		methods.visitEnd();
		inject.visitEnd();
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitTypeInsn(Opcodes.CHECKCAST, contract);
		mv.visitInsn(Opcodes.POP);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(1, 1);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] accessorMixin(String simpleName, String targetInternalName) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
				PKG + "/" + simpleName, null, "java/lang/Object", null);
		AnnotationVisitor mixin = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor arr = mixin.visitArray("value");
		arr.visit(null, Type.getObjectType(targetInternalName));
		arr.visitEnd();
		mixin.visitEnd();

		MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getThing", "()I", null, null);
		mv.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", false).visitEnd();
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] config(String pkg, String... clientMixins) {
		StringBuilder sb = new StringBuilder("{\"package\":\"").append(pkg).append("\",\"client\":[");
		for (int i = 0; i < clientMixins.length; i++) {
			if (i > 0) sb.append(',');
			sb.append('"').append(clientMixins[i]).append('"');
		}
		sb.append("]}");
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** The same config with a {@code plugin}, which is what makes the attribution deferrable. */
	private static byte[] configWithPlugin(String pkg, String plugin, String... clientMixins) {
		String json = new String(config(pkg, clientMixins), StandardCharsets.UTF_8);
		return json.replaceFirst("^\\{", java.util.regex.Matcher.quoteReplacement("{\"plugin\":\"" + plugin + "\","))
				.getBytes(StandardCharsets.UTF_8);
	}

	private static Function<String, byte[]> resolver(Map<String, byte[]> classes) {
		return path -> classes.get(path);
	}

	// --- tests --------------------------------------------------------------------------------------------------

	@Test
	void dropsOrphanedShadowButKeepsAnIntactMixinOnTheSameOwnedPackage() {
		String gui = "net/minecraft/client/gui/render/SomeGuiThing";
		String ok = "net/minecraft/client/renderer/LevelRenderer";
		Map<String, byte[]> classes = new HashMap<>();
		// The archetype: private, declared, NEVER assigned.
		classes.put(gui + ".class", target(gui, "orphanedRenderers", Opcodes.ACC_PRIVATE, false));
		// Same owned package, but the shadowed field is assigned — under the OLD rule this was dropped too.
		classes.put(ok + ".class", target(ok, "sectionsToRender", Opcodes.ACC_PRIVATE, true));

		classes.put(PKG + "/GuiRendererMixin.class",
				shadowingMixin("GuiRendererMixin", gui, "orphanedRenderers"));
		classes.put(PKG + "/LevelRendererMixin.class",
				shadowingMixin("LevelRendererMixin", ok, "sectionsToRender"));

		List<String> dropped = KernelGuestMixinAdapter.unfitMixins("example.mixins.json",
				config(PKG.replace('/', '.'), "GuiRendererMixin", "LevelRendererMixin"), resolver(classes));

		assertEquals(List.of("GuiRendererMixin"), dropped,
				"only the orphaned-@Shadow mixin should be dropped; owning the package is not a hazard");
	}

	@Test
	void onlyTheArraysMixinPreparesOnThisSideAreJudged() {
		String t = "net/minecraft/client/renderer/GameRenderer";
		Map<String, byte[]> classes = new HashMap<>();
		classes.put(t + ".class", target(t, "unused", Opcodes.ACC_PRIVATE, true));
		classes.put(PKG + "/ClientUnfit.class", danglingMixin("ClientUnfit", t));
		classes.put(PKG + "/ServerUnfit.class", danglingMixin("ServerUnfit", t));
		classes.put(PKG + "/CommonUnfit.class", danglingMixin("CommonUnfit", t));
		byte[] cfg = ("{\"package\":\"" + PKG.replace('/', '.') + "\",\"required\":true,\"mixins\":[\"CommonUnfit\"],"
				+ "\"client\":[\"ClientUnfit\"],\"server\":[\"ServerUnfit\"]}").getBytes(StandardCharsets.UTF_8);

		// fusion's client-array mixin was judged on a dedicated server, recorded as a required loss, and the
		// default STRICT policy stopped a server that would never have applied it.
		assertEquals(List.of("CommonUnfit", "ServerUnfit"), KernelGuestMixinAdapter.unfitMixins("s.mixins.json", cfg,
				resolver(classes), net.fabricmc.api.EnvType.SERVER));
		assertEquals(List.of("CommonUnfit", "ClientUnfit"), KernelGuestMixinAdapter.unfitMixins("s.mixins.json", cfg,
				resolver(classes), net.fabricmc.api.EnvType.CLIENT));
		assertEquals(List.of("CommonUnfit", "ClientUnfit", "ServerUnfit"),
				KernelGuestMixinAdapter.unfitMixins("s.mixins.json", cfg, resolver(classes)), "no side known: every array");

		System.setProperty(KernelGuestMixinAdapter.SIDED_PROPERTY, "off");
		try {
			assertEquals(List.of("CommonUnfit", "ClientUnfit", "ServerUnfit"), KernelGuestMixinAdapter.unfitMixins(
					"s.mixins.json", cfg, resolver(classes), net.fabricmc.api.EnvType.SERVER), "the switch judges both sides");
		} finally {
			System.clearProperty(KernelGuestMixinAdapter.SIDED_PROPERTY);
		}
	}

	@Test
	void keepsAPublicFieldWrittenElsewhere() {
		// MovingBlockRenderState.biome: public, read in-class, written by whoever populates the render state.
		// A class-local putfield scan calls this orphaned; judging only private fields is what makes it sound.
		String state = "net/minecraft/client/renderer/block/MovingBlockRenderState";
		Map<String, byte[]> classes = new HashMap<>();
		classes.put(state + ".class", target(state, "biome", Opcodes.ACC_PUBLIC, false));
		classes.put(PKG + "/MovingBlockRenderStateMixin.class",
