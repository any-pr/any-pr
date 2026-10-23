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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * A name-only {@code @Inject} selector is a miss when the method it binds is not one the handler was written for.
 *
 * <p>Mixin binds such a selector to the FIRST method of that name the target declares and then checks the handler
 * against it: its arguments then the callback its return calls for, or the callback alone. Anything else is
 * "Invalid descriptor" — an {@code InvalidInjectionException} when it is the injector's only target. On vanilla the name
 * bound the method the mod was compiled against; on the merged base it can bind a carrier's overload
 * (MoogsStructureLib's HEAD of {@code placeEntities} bound MinecraftForge's, and the server died after Done). The verdict
 * read that as resolved, whichever ecosystem the mod was, so neither the preflight nor the census saw it.
 */
@ResourceLock("system-properties")
class MixinFitHandlerFitTest {
	private static final String TARGET = "test/Target";
	private static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	private static final String CIR = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
	private static final String STRING = "Ljava/lang/String;";

	@AfterEach void reset() {
		System.clearProperty(MixinFit.HANDLER_FIT_PROPERTY);
		System.clearProperty(MixinFit.REJECTION_POINT_PROPERTY);
		System.clearProperty(MixinOverloadPin.PROPERTY);
		MixinStubRebind.forget();
	}

	/** {@code place(String, int)} declared first, then {@code place(String)}, then {@code count()I}. */
	private static Function<String, byte[]> target() {
		ClassNode node = new ClassNode();
		node.version = Opcodes.V21;
		node.access = Opcodes.ACC_PUBLIC;
		node.name = TARGET;
		node.superName = "java/lang/Object";
		for (String desc : List.of("(" + STRING + "I)V", "(" + STRING + ")V")) node.methods.add(body("place", desc, Opcodes.RETURN));
		MethodNode count = body("count", "()I", Opcodes.IRETURN);
		count.instructions.insert(new InsnNode(Opcodes.ICONST_0));
		node.methods.add(count);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		byte[] bytes = writer.toByteArray();
		return name -> name.equals(TARGET + ".class") ? bytes : null;
	}

	private static MethodNode body(String name, String desc, int ret) {
		MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, name, desc, null, null);
		m.instructions.add(new InsnNode(ret));
		return m;
	}

	/** One {@code @Inject(method = selector, at = @At("HEAD"))} handler of {@code desc}, plus whatever {@code extra} adds. */
	private static byte[] mixin(String selector, String desc, java.util.function.Consumer<ClassNode> extra) {
		ClassNode mixin = new ClassNode();
		mixin.version = Opcodes.V21;
		mixin.access = Opcodes.ACC_PUBLIC;
		mixin.name = "test/TheMixin";
		mixin.superName = "java/lang/Object";
		AnnotationNode type = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
		type.values = new ArrayList<>(List.of("value", new ArrayList<>(List.of(Type.getObjectType(TARGET)))));
		mixin.invisibleAnnotations = new ArrayList<>(List.of(type));
		mixin.methods.add(handler("onPlace", desc, selector));
		if (extra != null) extra.accept(mixin);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		mixin.accept(writer);
		return writer.toByteArray();
	}

	private static MethodNode handler(String name, String desc, String selector) {
		AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
		at.values = new ArrayList<>(List.of("value", "HEAD"));
		AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
		inject.values = new ArrayList<>(List.of("method", new ArrayList<>(List.of(selector)), "at", new ArrayList<>(List.of(at))));
		MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE, name, desc, null, null);
		handler.visibleAnnotations = new ArrayList<>(List.of(inject));
		handler.instructions.add(new InsnNode(Opcodes.RETURN));
		return handler;
	}

	private static MixinFit.Result judge(byte[] mixin) {
		return MixinFit.evaluate(mixin, target());
	}

	/**
	 * The shape MixinOverloadPin moves: the handler fits the second overload alone, so the pin spells it and the verdict
	 * judges the injector where it lands -- one mechanism, not two that disagree.
	 */
	@Test void aHandlerWrittenForTheSecondOverloadIsJudgedWhereThePinPutsIt() {
		MixinFit.Result fit = judge(mixin("place", "(" + STRING + CI + ")V", null));
		assertEquals(MixinFit.Verdict.FIT, fit.verdict(), fit.reason());
		assertEquals(List.of(), fit.rejected());
	}

	/** With the pin off, Mixin binds the first overload: the name is a miss, and Mixin would reject it outright. */
	@Test void withThePinOffTheSecondOverloadsHandlerMissesTheFirstThatTheNameBinds() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		MixinFit.Result fit = judge(mixin("place", "(" + STRING + CI + ")V", null));
		assertEquals(MixinFit.Verdict.UNFIT, fit.verdict(), fit.reason());
		String refused = "@Inject target Target.place binds place(" + STRING + "I)V, which the handler was not written for; it "
				+ "fits place(" + STRING + ")V, declared later, and Mixin binds the first";
		assertEquals(List.of(refused), fit.unresolved());
		assertEquals(List.of(new MixinFit.Rejection("onPlace", "(" + STRING + CI + ")V", refused, true)), fit.rejected());
	}

	/** RED control: with the rule off the name binds and that is all the verdict asks, as before. */
	@Test void withTheRuleOffTheBindingIsAllThatCounts() {
		System.setProperty(MixinFit.HANDLER_FIT_PROPERTY, "off");
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		MixinFit.Result fit = judge(mixin("place", "(" + STRING + CI + ")V", null));
		assertEquals(MixinFit.Verdict.FIT, fit.verdict());
		assertEquals(List.of(), fit.rejected());
	}

	/**
	 * Beside an injector that binds, the refused one leaves the mixin PARTIAL -- and is named as one Mixin rejects
	 * outright, because its name binds exactly one method and HEAD is sure to be found there: Mixin throws there,
	 * whatever require says, and the mixin's application fails with it. Kept as a PARTIAL that "loses the rest with no error", it would not.
	 */
	@Test void aRefusedInjectorBesideOneThatBindsIsARejectionThePartialMustAnswerFor() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		MixinFit.Result fit = judge(mixin("place", "(" + STRING + CI + ")V", m -> m.methods.add(handler("onCount", "(" + CIR + ")V", "count"))));
		assertEquals(MixinFit.Verdict.PARTIAL, fit.verdict(), fit.reason());
		assertEquals(1, fit.rejected().size(), fit.rejected().toString());
		assertEquals("onPlace", fit.rejected().get(0).handler());
		assertTrue(fit.rejected().get(0).everywhere());
	}

	/**
	 * A satisfied {@code @Group} settles its alternatives' misses away -- but not a rejection: Mixin throws on the refused
	 * member whatever the group's count says, so the verdict keeps it and names it.
	 */
	@Test void aGroupNeverSettlesARejectionAway() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		byte[] grouped = mixin("place", "(" + STRING + CI + ")V", m -> {
			m.methods.add(handler("onCount", "(" + CIR + ")V", "count"));
			for (MethodNode h : m.methods) h.visibleAnnotations.add(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Group;"));
		});
		MixinFit.Result fit = judge(grouped);
		assertEquals(MixinFit.Verdict.PARTIAL, fit.verdict(), fit.reason());
		assertEquals(List.of("onPlace"), fit.rejected().stream().map(MixinFit.Rejection::handler).toList());
	}

	/**
	 * Not a rejection: two selectors that each bind a method Mixin skips one by one (it looks for a match among the
	 * others before failing), and a handler that captures locals goes Mixin's capture way, which the kernel softens to
	 * a warning. Both stay misses.
	 */
	@Test void twoBoundMethodsOrCapturedLocalsAreMissesMixinSkips() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		byte[] two = mixin("place", "(" + STRING + CI + ")V", m -> {
			AnnotationNode inject = MixinFit.injectorOf(m.methods.getFirst());
			inject.values.set(1, new ArrayList<>(List.of("place", "count")));
		});
		MixinFit.Result both = judge(two);
		assertEquals(MixinFit.Verdict.UNFIT, both.verdict(), both.reason());
		assertEquals(List.of(), both.rejected());

		byte[] captures = mixin("place", "(" + STRING + CI + "J)V", m -> {
			AnnotationNode inject = MixinFit.injectorOf(m.methods.getFirst());
			inject.values.addAll(List.of("locals", new String[] {
					"Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;", "CAPTURE_FAILHARD"}));
		});
		MixinFit.Result captured = judge(captures);
		assertEquals(MixinFit.Verdict.UNFIT, captured.verdict(), captured.reason());
		assertEquals(List.of(), captured.rejected());
	}

	/**
	 * The node-seam check the pruner asks: the same rule of the mixin as Mixin will receive it. A selector the pin has
	 * spelled is never rejected there, and a target that binds the handler as written stops the rejection.
	 */
	@Test void theNodeSeamAsksTheSameRuleOfTheMixinAsItNowStands() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		ClassNode target = MixinFit.parse(target().apply(TARGET + ".class"));
		ClassNode mixin = MixinFit.parse(mixin("place", "(" + STRING + CI + ")V", null));
		MethodNode handler = mixin.methods.getFirst();
		assertTrue(MixinFit.stillRejected(mixin, handler, n -> n.equals(TARGET) ? target : null)
				.contains("binds place(" + STRING + "I)V, which the handler was not written for"));
		// What MixinOverloadPin writes when it pins: a spelled descriptor binds by itself, and is not judged here.
		MixinFit.injectorOf(handler).values.set(1, new ArrayList<>(List.of("place(" + STRING + ")V")));
		assertNull(MixinFit.stillRejected(mixin, handler, n -> n.equals(TARGET) ? target : null));
		// Unreadable target: not certain, so nothing is taken out.
		MixinFit.injectorOf(handler).values.set(1, new ArrayList<>(List.of("place")));
		assertNull(MixinFit.stillRejected(mixin, handler, n -> null));
		// RED control: the rule off.
		System.setProperty(MixinFit.HANDLER_FIT_PROPERTY, "off");
		assertNull(MixinFit.stillRejected(mixin, handler, n -> n.equals(TARGET) ? target : null));
	}

	@Test void theFirstOverloadsArgumentsOrTheCallbackAloneFit() {
		assertEquals(MixinFit.Verdict.FIT, judge(mixin("place", "(" + STRING + "I" + CI + ")V", null)).verdict());
		assertEquals(MixinFit.Verdict.FIT, judge(mixin("place", "(" + CI + ")V", null)).verdict());
		// Captured locals after the callback are Mixin's to check at the point; this does not judge them.
		assertEquals(MixinFit.Verdict.FIT, judge(mixin("place", "(" + STRING + "I" + CI + "J)V", null)).verdict());
		// An owner-qualified name is still a name.
		assertEquals(MixinFit.Verdict.UNFIT, judge(mixin("L" + TARGET + ";place", "(" + STRING + CI + ")V", null)).verdict());
	}

	@Test void aSpelledDescriptorIsBoundByItselfAndNotJudgedHere() {
		assertEquals(MixinFit.Verdict.FIT, judge(mixin("place(" + STRING + ")V", "(" + STRING + CI + ")V", null)).verdict());
	}

	@Test void theCallbackMustBeTheOneTheReturnCallsFor() {
		assertEquals(MixinFit.Verdict.UNFIT, judge(mixin("place", "(" + CIR + ")V", null)).verdict(), "void takes CallbackInfo");
		assertEquals(MixinFit.Verdict.UNFIT, judge(mixin("count", "(" + CI + ")V", null)).verdict(), "a value takes the returnable");
		assertEquals(MixinFit.Verdict.FIT, judge(mixin("count", "(" + CIR + ")V", null)).verdict());
	}

	@Test void aCoercedArgumentAndASurrogateAreMixinsToAccept() {
		byte[] coerced = mixin("place", "(Ljava/lang/Object;I" + CI + ")V", m -> {
			MethodNode h = m.methods.getFirst();
			h.invisibleParameterAnnotations = new List[3];
			h.invisibleParameterAnnotations[0] = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Coerce;")));
		});
		assertEquals(MixinFit.Verdict.FIT, judge(coerced).verdict());
		assertEquals(MixinFit.Verdict.UNFIT, judge(mixin("place", "(Ljava/lang/Object;I" + CI + ")V", null)).verdict(),
				"the same argument without @Coerce");
		byte[] surrogate = mixin("place", "(" + STRING + CI + ")V", m -> {
			MethodNode s = body("onPlace", "(" + STRING + "I" + CI + ")V", Opcodes.RETURN);
			s.access = Opcodes.ACC_PRIVATE;
			s.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Surrogate;")));
			m.methods.add(s);
		});
		assertEquals(MixinFit.Verdict.FIT, judge(surrogate).verdict());
		// …because Mixin binds the surrogate on the first overload, not because the pin moved the handler: the two
		// read a surrogate the same way, and the pin leaves this selector as the mod wrote it.
		ClassNode node = MixinFit.parse(surrogate);
		ClassNode target = MixinFit.parse(target().apply(TARGET + ".class"));
		assertNull(MixinOverloadPin.destination(node, node.methods.getFirst(), "place", target));
		assertSame(target.methods.get(1), MixinOverloadPin.destination(node.methods.getFirst(), "place", target),
				"RED control: asked without the mixin, the surrogate is not seen and the pin would move the handler");
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		assertEquals(MixinFit.Verdict.FIT, judge(surrogate).verdict(), "the surrogate alone, with the pin off");
	}

	// --- where Mixin meets the handler at all ---

	/**
	 * {@code place(String, int)} (the carrier's, declared first) calls {@code count()}, {@code log()}, reads
	 * {@code flag} and makes a {@code new StringBuilder()} once each, then returns; {@code place(String)} and {@code count()I} as in {@link #target()}.
	 */
	private static Function<String, byte[]> targetWithCalls() {
		ClassNode node = new ClassNode();
		new ClassReader(target().apply(TARGET + ".class")).accept(node, 0);
		MethodNode first = node.methods.getFirst();
		first.instructions.clear();
		first.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		first.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "count", "()I", false));
		first.instructions.add(new InsnNode(Opcodes.POP));
		first.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		first.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "log", "()V", false));
		first.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		first.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET, "flag", "Z"));
		first.instructions.add(new InsnNode(Opcodes.POP));
		first.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
		first.instructions.add(new InsnNode(Opcodes.DUP));
		first.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));
		first.instructions.add(new InsnNode(Opcodes.POP));
		first.instructions.add(new InsnNode(Opcodes.RETURN));
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		byte[] bytes = writer.toByteArray();
		return name -> name.equals(TARGET + ".class") ? bytes : null;
	}

	/** The handler for {@code place(String)}, its one {@code @At} spelled by {@code at} (value first, then key, value…). */
	private static byte[] pointMixin(Object... at) {
		return mixin("place", "(" + STRING + CI + ")V", m -> {
			AnnotationNode point = MixinFit.atNodes(MixinFit.injectorOf(m.methods.getFirst())).getFirst();
			point.values = new ArrayList<>(List.of("value", at[0]));
			for (int i = 1; i + 1 < at.length; i += 2) point.values.addAll(List.of(at[i], at[i + 1]));
		});
	}

	private static boolean rejects(byte[] mixin) {
		MixinFit.Result fit = MixinFit.evaluate(mixin, targetWithCalls());
		assertEquals(MixinFit.Verdict.UNFIT, fit.verdict(), fit.reason());
		return !fit.rejected().isEmpty();
	}

	/**
	 * Mixin checks an {@code @Inject} handler at each point it finds in the bound method: where its {@code @At} finds none
	 * it injects nothing and throws nothing, and {@code require} counts that as any other miss. So a refused binding is a
	 * rejection only where a point is sure to be found -- the rest are misses, and the mixin is kept as one with misses.
	 */
	@Test void aRefusedBindingIsARejectionOnlyWhereItsAtIsSureToFindAPoint() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		String count = "L" + TARGET + ";count()I";
		assertTrue(rejects(pointMixin("HEAD")));
		assertTrue(rejects(pointMixin("HEAD", "ordinal", 3)), "HEAD takes the first instruction whatever the ordinal");
		assertTrue(rejects(pointMixin("RETURN")));
		assertTrue(rejects(pointMixin("RETURN", "ordinal", 0)));
		assertFalse(rejects(pointMixin("RETURN", "ordinal", 1)), "one return: ordinal 1 finds none");
		assertTrue(rejects(pointMixin("TAIL")));
		assertTrue(rejects(pointMixin("INVOKE", "target", count)));
		assertTrue(rejects(pointMixin("INVOKE", "target", TARGET + ".count()I")), "the dotted owner too");
		assertFalse(rejects(pointMixin("INVOKE", "target", "L" + TARGET + ";absent()V")), "a call the carrier's body never makes");
		assertFalse(rejects(pointMixin("INVOKE", "target", "Lother/Owner;count()I")), "the same name on another owner");
		assertTrue(rejects(pointMixin("INVOKE", "target", count, "ordinal", 0)));
		assertFalse(rejects(pointMixin("INVOKE", "target", count, "ordinal", 1)), "one call: ordinal 1 finds none");
		assertTrue(rejects(pointMixin("INVOKE_ASSIGN", "target", count)));
		assertFalse(rejects(pointMixin("INVOKE_ASSIGN", "target", "L" + TARGET + ";log()V")), "a void call assigns nothing");
		assertTrue(rejects(pointMixin("INVOKE", "target", "L" + TARGET + ";log()V")));
		assertTrue(rejects(pointMixin("FIELD", "target", "L" + TARGET + ";flag:Z")));
		assertTrue(rejects(pointMixin("FIELD", "target", "L" + TARGET + ";flag:Z", "opcode", Opcodes.GETFIELD)));
		assertFalse(rejects(pointMixin("FIELD", "target", "L" + TARGET + ";flag:Z", "opcode", Opcodes.PUTFIELD)), "no write");
		assertTrue(rejects(pointMixin("NEW", "target", "java/lang/StringBuilder")));
		assertTrue(rejects(pointMixin("NEW", "target", "Ljava/lang/StringBuilder;")));
		assertTrue(rejects(pointMixin("NEW", "target", "()Ljava/lang/StringBuilder;")));
		assertFalse(rejects(pointMixin("NEW", "target", "(Ljava/lang/String;)Ljava/lang/StringBuilder;")), "another constructor");
		assertFalse(rejects(pointMixin("NEW", "target", "java/lang/StringBuilder", "ordinal", 1)), "one new: ordinal 1 finds none");
		assertFalse(rejects(pointMixin("NEW", "target", "java/util/ArrayList")), "a type the body never makes");
		assertFalse(rejects(pointMixin("CONSTANT")), "a kind of point this does not read is not sure");
		assertFalse(rejects(pointMixin("INVOKE")), "no target: not sure");
		assertFalse(rejects(pointMixin("INVOKE", "target", count, "slice", "s")), "a slice: not sure");
		byte[] sliced = mixin("place", "(" + STRING + CI + ")V", m -> MixinFit.injectorOf(m.methods.getFirst()).values.addAll(
				List.of("slice", new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Slice;"))))));
		assertFalse(rejects(sliced), "an injector slice: not sure, even at HEAD");
	}

	/** The miss says why it is not a rejection; the verdict is the one any miss gets. */
	@Test void aMissWithNoSurePointSaysSo() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		MixinFit.Result fit = MixinFit.evaluate(pointMixin("INVOKE", "target", "L" + TARGET + ";absent()V"), targetWithCalls());
		assertEquals(List.of("@Inject target Target.place binds place(" + STRING + "I)V, which the handler was not written for; it fits "
				+ "place(" + STRING + ")V, declared later, and Mixin binds the first; its @At may find no point there, and Mixin "
				+ "rejects a handler only at a point it finds"), fit.unresolved());
		assertEquals(List.of(), fit.rejected());
	}

	/** RED control: with the point rule off a refused binding is a rejection whatever its @At finds, as before. */
	@Test void withThePointRuleOffEveryRefusedBindingIsARejection() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		System.setProperty(MixinFit.REJECTION_POINT_PROPERTY, "off");
		assertTrue(rejects(pointMixin("INVOKE", "target", "L" + TARGET + ";absent()V")));
		assertTrue(rejects(pointMixin("CONSTANT")));
		assertTrue(rejects(pointMixin("HEAD")));
	}

	/**
	 * The node seam asks the same: with the target's code, a binding whose point is not sure is not taken out. Without
	 * code there is nothing to find a point in, so nothing is sure -- which is why the seam reads its targets with code.
	 */
	@Test void theNodeSeamAsksForAPointInTheTargetsCode() {
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		ClassNode withCode = new ClassNode();
		new ClassReader(targetWithCalls().apply(TARGET + ".class")).accept(withCode, 0);
		ClassNode skipped = new ClassNode();
		new ClassReader(targetWithCalls().apply(TARGET + ".class")).accept(skipped, ClassReader.SKIP_CODE);
		ClassNode head = MixinFit.parse(pointMixin("HEAD"));
		ClassNode absent = MixinFit.parse(pointMixin("INVOKE", "target", "L" + TARGET + ";absent()V"));
		assertNotNull(MixinFit.stillRejected(head, head.methods.getFirst(), n -> withCode));
		assertNull(MixinFit.stillRejected(absent, absent.methods.getFirst(), n -> withCode));
		assertNull(MixinFit.stillRejected(head, head.methods.getFirst(), n -> skipped), "no code, no sure point");
		System.setProperty(MixinFit.REJECTION_POINT_PROPERTY, "off");
		assertNotNull(MixinFit.stillRejected(absent, absent.methods.getFirst(), n -> withCode), "RED control: the rule off");
	}

	// --- a @Surrogate, as Mixin looks one up ---

	/** A {@code @Surrogate} of the handler's name with {@code desc}, its annotation visible or not. */
	private static java.util.function.Consumer<ClassNode> surrogate(String desc, boolean visible, boolean coerceFirst) {
		return m -> {
			MethodNode s = body("onPlace", desc, Opcodes.RETURN);
			s.access = Opcodes.ACC_PRIVATE;
			List<AnnotationNode> annotation = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Surrogate;")));
			if (visible) s.visibleAnnotations = annotation; else s.invisibleAnnotations = annotation;
			if (coerceFirst) {
				s.invisibleParameterAnnotations = new List[Type.getArgumentTypes(desc).length];
				s.invisibleParameterAnnotations[0] = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Coerce;")));
			}
			m.methods.add(s);
		};
	}

	/**
	 * When the handler does not fit, Mixin looks a surrogate up by the handler's name and EXACTLY the callback descriptor
	 * ({@code Bytecode.findMethod}), and takes it only with a visible {@code @Surrogate} ({@code Annotations.getVisible}).
	 * A surrogate that takes the callback alone, or a {@code @Coerce}d argument, or whose annotation is invisible, is not
	 * found: the binding stays refused (and pinned, with the pin on). Only the exact one stands in.
	 */
	@Test void aSurrogateStandsInOnlyWhereMixinFindsIt() {
		ClassNode target = MixinFit.parse(target().apply(TARGET + ".class"));
		String exact = "(" + STRING + "I" + CI + ")V";
		List<java.util.function.Consumer<ClassNode>> notFound = List.of(
				surrogate("(" + CI + ")V", true, false),
				surrogate("(Ljava/lang/Object;I" + CI + ")V", true, true),
				surrogate(exact, false, false));
		for (java.util.function.Consumer<ClassNode> edit : notFound) {
			byte[] mixin = mixin("place", "(" + STRING + CI + ")V", edit);
			ClassNode node = MixinFit.parse(mixin);
			MethodNode surrogate = node.methods.get(1);
			assertSame(target.methods.get(1), MixinOverloadPin.destination(node, node.methods.getFirst(), "place", target),
					"not found by Mixin, so the pin moves the handler: " + surrogate.desc);
			System.setProperty(MixinOverloadPin.PROPERTY, "off");
			assertEquals(1, judge(mixin).rejected().size(), "refused, and a rejection: " + surrogate.desc);
			System.clearProperty(MixinOverloadPin.PROPERTY);
			if (surrogate.visibleAnnotations != null) {
				assertTrue(MixinFit.handlerFits(surrogate, target.methods.getFirst().desc),
						"RED control: the looser rule (the handler's own fit) would have stood it in: " + surrogate.desc);
			}
		}
		byte[] found = mixin("place", "(" + STRING + CI + ")V", surrogate(exact, true, false));
		ClassNode node = MixinFit.parse(found);
		assertNull(MixinOverloadPin.destination(node, node.methods.getFirst(), "place", target));
		System.setProperty(MixinOverloadPin.PROPERTY, "off");
		assertEquals(MixinFit.Verdict.FIT, judge(found).verdict());
	}

	/** Beside a selector that binds, a refused one only says how many hit, as any other miss among alternatives. */
	@Test void anAlternativeThatBindsKeepsTheInjector() {
		byte[] both = mixin("place", "(" + STRING + CI + ")V", m -> {
			AnnotationNode inject = MixinFit.injectorOf(m.methods.getFirst());
			inject.values.set(1, new ArrayList<>(List.of("place", "place(" + STRING + ")V")));
		});
		assertEquals(MixinFit.Verdict.FIT, judge(both).verdict());
	}
}
