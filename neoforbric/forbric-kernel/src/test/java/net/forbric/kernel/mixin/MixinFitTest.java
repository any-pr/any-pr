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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Pins how a Mixin member target is split into owner/name/desc.
 *
 * <p>This is a DIAGNOSTIC's parser, which is exactly why it needs pinning: when it is wrong it does not crash, it
 * lies. Reading the dotted owner form as part of the method name made every such {@code @At(target=…)} report as an
 * unresolved anchor forever — 11 of Shoulder Surfing's mixins looked half-applied when every anchor was present —
 * and would have made {@code -Dforbric.mixinFit=strict} drop mixins that fit.
 */
class MixinFitTest {
	/**
	 * A mixin targeting one class, with one {@code @Shadow} field that the target does not have — the simplest
	 * anchor that can fail.
	 */
	private static byte[] shadowMixin(String target) {
		org.objectweb.asm.ClassWriter cw =
				new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
		cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, "test/TheMixin",
				null, "java/lang/Object", null);
		org.objectweb.asm.AnnotationVisitor mixin =
				cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		org.objectweb.asm.AnnotationVisitor targets = mixin.visitArray("targets");
		targets.visit(null, target);
		targets.visitEnd();
		mixin.visitEnd();
		org.objectweb.asm.FieldVisitor fv = cw.visitField(0, "notThere", "I", null, null);
		fv.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", false).visitEnd();
		fv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/** An empty class, so the {@code @Shadow} above cannot resolve against it. */
	private static byte[] emptyClass(String name) {
		org.objectweb.asm.ClassWriter cw =
				new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
		cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, name, null,
				"java/lang/Object", null);
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * The discriminator this repo now reports on, and the reason it is worth reporting.
	 *
	 * <p>An anchor that misses on a merged-base class is routine — measured across every gate log here, 1226 of
	 * them, all on runs that pass. One that misses on ANOTHER MOD's class did not occur once in that same set,
	 * and the one real instance found was Iris beside a Sodium build it no longer fits. Same unresolved anchor,
	 * completely different meaning, so {@code evaluate} has to tell them apart.
	 */
	@Test
	void anAnchorThatMissesOnAnotherModsClassIsSeparatedFromOneThatMissesOnTheGame() {
		String target = "net/example/OtherMod";
		java.util.function.Function<String, byte[]> resolver =
				name -> (target + ".class").equals(name) ? emptyClass(target) : null;

		MixinFit.Result asGame = MixinFit.evaluate(shadowMixin(target), resolver, name -> true);
		assertEquals(MixinFit.Verdict.UNFIT, asGame.verdict(), "the anchor misses either way");
		assertTrue(asGame.foreign().isEmpty(), "a merged-base miss is routine and must stay quiet");

		MixinFit.Result asMod = MixinFit.evaluate(shadowMixin(target), resolver, name -> false);
		assertEquals(1, asMod.foreign().size(), "a miss on another mod's class is the signal");
		assertTrue(asMod.foreign().get(0).contains("notThere"), asMod.foreign().toString());
	}

	@Test
	void anAnchorThatRESOLVESIsNeverReportedAsForeign() {
		// The obvious way to get this wrong: report every cross-mod mixin instead of every cross-mod mixin that
		// did not attach. Every pack is full of the former.
		String target = "net/example/OtherMod";
		org.objectweb.asm.ClassWriter cw =
				new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
		cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, target, null,
				"java/lang/Object", null);
		cw.visitField(0, "notThere", "I", null, null).visitEnd();
		cw.visitEnd();
		byte[] withField = cw.toByteArray();

		MixinFit.Result fit = MixinFit.evaluate(shadowMixin(target),
				name -> (target + ".class").equals(name) ? withField : null, name -> false);
		assertEquals(MixinFit.Verdict.FIT, fit.verdict());
		assertTrue(fit.foreign().isEmpty(), "it attached — there is nothing to tell the player");
	}

	@Test
	void descriptorFormOwnerIsStripped() {
		MixinFit.Member m = MixinFit.parseMember("Lnet/minecraft/client/CameraType;isFirstPerson()Z");
		assertEquals("net/minecraft/client/CameraType", m.owner());
		assertEquals("isFirstPerson", m.name());
		assertEquals("()Z", m.desc());
	}

	/** The case this fix exists for: Shoulder Surfing, malilib and litematica all write targets this way. */
	@Test
	void dottedInternalNameOwnerIsStripped() {
		MixinFit.Member m = MixinFit.parseMember("net/minecraft/client/CameraType.isFirstPerson()Z");
		assertEquals("net/minecraft/client/CameraType", m.owner());
		assertEquals("isFirstPerson", m.name(), "the owner must not end up glued to the method name");
		assertEquals("()Z", m.desc());
	}

	@Test
	void aFullyDottedOwnerBecomesAnInternalName() {
		MixinFit.Member m = MixinFit.parseMember("com.example.Foo.bar()V");
		assertEquals("com/example/Foo", m.owner(), "owners are compared against ASM's internal names");
		assertEquals("bar", m.name());
	}

	@Test
	void aConstructorTargetKeepsItsAngleBrackets() {
		MixinFit.Member m = MixinFit.parseMember("net/minecraft/client/model/Model.<init>");
		assertEquals("net/minecraft/client/model/Model", m.owner());
		assertEquals("<init>", m.name());
		assertNull(m.desc());
	}

	@Test
	void aBareNameHasNoOwner() {
		MixinFit.Member m = MixinFit.parseMember("addToTooltip");
		assertNull(m.owner());
		assertEquals("addToTooltip", m.name());
		assertNull(m.desc());
	}

	@Test
	void aFieldTargetSplitsOnTheColon() {
		MixinFit.Member m = MixinFit.parseMember("net/minecraft/client/gui/Hud.random:Lnet/minecraft/util/RandomSource;");
		assertEquals("net/minecraft/client/gui/Hud", m.owner());
		assertEquals("random", m.name());
		assertEquals("Lnet/minecraft/util/RandomSource;", m.desc());
	}

	/** Shoulder Surfing writes a space before the descriptor; Mixin ignores it, so this must too. */
	@Test
	void whitespaceInsideTheMemberIsIgnored() {
		MixinFit.Member m = MixinFit.parseMember(
				"net/minecraft/client/renderer/entity/EntityRenderer.createRenderState ()Lnet/minecraft/client/renderer/entity/state/EntityRenderState;");
		assertEquals("net/minecraft/client/renderer/entity/EntityRenderer", m.owner());
		assertEquals("createRenderState", m.name(), "a trailing space must not become part of the method name");
		assertEquals("()Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", m.desc());
	}

	/**
	 * A mixin with one {@code @Inject(method = "discover", at = @At(value = "INVOKE", target = <resolve>))} —
	 * the bare-name selector fabric-model-loading-api-v1 writes for {@code discoverModelDependencies}.
	 */
	private static byte[] injectMixin(String target, String atTarget) {
		org.objectweb.asm.ClassWriter cw =
				new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
		cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, "test/TheMixin",
				null, "java/lang/Object", null);
		org.objectweb.asm.AnnotationVisitor mixin =
				cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		org.objectweb.asm.AnnotationVisitor targets = mixin.visitArray("targets");
		targets.visit(null, target);
		targets.visitEnd();
		mixin.visitEnd();
		org.objectweb.asm.MethodVisitor mv = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PRIVATE, "handler",
				"(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
		org.objectweb.asm.AnnotationVisitor inject =
				mv.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
		org.objectweb.asm.AnnotationVisitor method = inject.visitArray("method");
		method.visit(null, "discover");
		method.visitEnd();
		org.objectweb.asm.AnnotationVisitor ats = inject.visitArray("at");
		org.objectweb.asm.AnnotationVisitor at = ats.visitAnnotation(null, "Lorg/spongepowered/asm/mixin/injection/At;");
		at.visit("value", "INVOKE");
		at.visit("target", atTarget);
		at.visitEnd();
		ats.visitEnd();
		inject.visitEnd();
		mv.visitCode();
		mv.visitInsn(org.objectweb.asm.Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * A target with two {@code discover} overloads: {@code discover()V} delegates to {@code discover(I)V}, and only
	 * the second one calls {@code callee} — the merged {@code ModelManager.discoverModelDependencies} shape.
	 */
	private static byte[] twoOverloads(String name, String calleeOwner, String calleeName, String calleeDesc) {
		org.objectweb.asm.ClassWriter cw =
				new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
		cw.visit(org.objectweb.asm.Opcodes.V21, org.objectweb.asm.Opcodes.ACC_PUBLIC, name, null,
				"java/lang/Object", null);
		org.objectweb.asm.MethodVisitor delegating = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC,
				"discover", "()V", null, null);
		delegating.visitCode();
		delegating.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
		delegating.visitInsn(org.objectweb.asm.Opcodes.ICONST_0);
		delegating.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKEVIRTUAL, name, "discover", "(I)V", false);
		delegating.visitInsn(org.objectweb.asm.Opcodes.RETURN);
		delegating.visitMaxs(0, 0);
		delegating.visitEnd();
		org.objectweb.asm.MethodVisitor real = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC,
				"discover", "(I)V", null, null);
		real.visitCode();
		real.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC, calleeOwner, calleeName, calleeDesc, false);
		real.visitInsn(org.objectweb.asm.Opcodes.POP);
		real.visitInsn(org.objectweb.asm.Opcodes.RETURN);
		real.visitMaxs(0, 0);
		real.visitEnd();
		cw.visitEnd();
		return cw.toByteArray();
	}

	/**
	 * Mixin binds a bare-name selector to the FIRST declared overload only (single-match quantifier; TargetSelectors
	 * stops at the first match). fabric-model-loading-api-v1's {@code discoverModelDependencies} injector lands on the
	 * three-arg stub, and its handler measured zero references at runtime: PARTIAL is the truth, and MixinStubRebind is
	 * what moves it.
	 */
	@Test
	void aBareNameSelectorIsJudgedAgainstTheFirstDeclaredOverloadAsMixinBindsIt() {
		String target = "net/example/ModelManager";
		byte[] targetBytes = twoOverloads(target, "net/example/Discovery", "resolve", "()Ljava/util/Map;");
		java.util.function.Function<String, byte[]> resolver =
				name -> (target + ".class").equals(name) ? targetBytes : null;

		// Mixin binds "discover" to discover()V, declared first — the stub. The call lives only in the second overload,
		// so Mixin finds nothing to inject at: PARTIAL, as the zero handler references measured at runtime said.
		MixinFit.Result partial = MixinFit.evaluate(
				injectMixin(target, "Lnet/example/Discovery;resolve()Ljava/util/Map;"), resolver);
		assertEquals(MixinFit.Verdict.PARTIAL, partial.verdict(),
				"a bare name binds the first declared overload only: " + partial.unresolved());
		assertEquals(1, partial.unresolved().size(), partial.unresolved().toString());
		assertTrue(partial.unresolved().get(0).contains("resolve"), partial.unresolved().toString());
	}

	/**
	 * An {@code @At} into ANOTHER class is reported under that class's full name, and says which of the target's
	 * methods it was looked for in. It used to be reported under the mixin's target: owo's anchor on Fabric Loader's
	 * {@code Hooks.startServer} read "missing: @At(INVOKE) Main.startServer in main", a method that does not exist.
	 * Then under the owner's simple name, where owo's Quilt alternative -- also a class called {@code Hooks} -- read
	 * exactly like the Fabric call that had just been fixed.
	 */
	@Test
	void anAnchorIntoAnotherClassIsReportedUnderThatClass() {
		String target = "net/example/ModelManager";
		byte[] targetBytes = twoOverloads(target, "net/example/Discovery", "resolve", "()Ljava/util/Map;");
		java.util.function.Function<String, byte[]> resolver =
				name -> (target + ".class").equals(name) ? targetBytes : null;

		assertEquals(java.util.List.of("@At(INVOKE) net.example.Discovery.resolve in ModelManager.discover"),
				MixinFit.evaluate(injectMixin(target, "Lnet/example/Discovery;resolve()Ljava/util/Map;"), resolver)
						.unresolved());
		assertEquals(java.util.List.of("@At(INVOKE) net.example.Discovery.resolve in ModelManager.discover"),
				MixinFit.evaluate(injectMixin(target, "net/example/Discovery.resolve()Ljava/util/Map;"), resolver)
						.unresolved(), "the dotted owner form names the same class");
	}

	/**
	 * A {@code @Group}'s members are alternatives: its min/max replace each member's own require, so once one member
	 * binds completely the others' misses are not misses. Only within that group -- another group none of whose
	 * members binds keeps its miss, and so does an injector in no group.
	 */
	@Test
	void aSatisfiedGroupForgivesItsOtherAlternativesAndNothingElse() {
		String target = "net/example/ModelManager";
		byte[] targetBytes = twoOverloads(target, "net/example/Discovery", "resolve", "()Ljava/util/Map;");
		java.util.function.Function<String, byte[]> resolver =
				name -> (target + ".class").equals(name) ? targetBytes : null;
		String here = "Lnet/example/ModelManager;discover(I)V";   // discover()V calls it: resolves
		String gone = "Lnet/example/Gone;call()V";

		MixinFit.Result result = MixinFit.evaluate(groupedMixin(target, new String[][] {
				{"fabricAlternative", "hooks", here},
				{"quiltAlternative", "hooks", gone},
				{"loneAlternative", "other", gone},
				{"unnamedHit", "", here},
				{"unnamedMiss", "", gone},
				{"ungrouped", null, gone},
		}), resolver);

