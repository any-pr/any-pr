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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.CompatibilityFindings;

/**
 * GuestInjectorPruner's second kind of entry: an {@code @Inject} the verdict proved Mixin rejects outright, remembered by
 * the adapter and taken out of the node Mixin receives -- only while the same rule still says so of that node, only
 * when it can go alone, and never with its switch off.
 */
@ResourceLock("system-properties")
class GuestInjectorPrunerRefusedTest {
	private static final String MIXIN = "test/RefusedMixin";
	private static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
	private static final String REFUSED = "(Ljava/lang/String;" + CI + ")V";
	private static final String KEPT = "(" + CI + ")V";

	@AfterEach void reset() {
		System.clearProperty(GuestInjectorPruner.PROPERTY);
		System.clearProperty(GuestInjectorPruner.REFUSED_PROPERTY);
		GuestInjectorPruner.forgetRefused();
		CompatibilityFindings.reset();
	}

	/** {@code onPlace} (refused), {@code onTick} (binds), and a surrogate of {@code onPlace}. */
	private static ClassNode mixin() {
		ClassNode node = new ClassNode();
		node.version = Opcodes.V21;
		node.access = Opcodes.ACC_PUBLIC;
		node.name = MIXIN;
		node.superName = "java/lang/Object";
		node.methods.add(handler("onPlace", REFUSED, "place"));
		node.methods.add(handler("onTick", KEPT, "tick"));
		MethodNode surrogate = new MethodNode(Opcodes.ACC_PRIVATE, "onPlace", "(Ljava/lang/String;I" + CI + ")V", null, null);
		surrogate.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Surrogate;")));
		surrogate.instructions.add(new InsnNode(Opcodes.RETURN));
		node.methods.add(surrogate);
		return node;
	}

	private static MethodNode handler(String name, String desc, String selector) {
		AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
		at.values = new ArrayList<>(List.of("value", "HEAD"));
		AnnotationNode inject = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
		inject.values = new ArrayList<>(List.of("method", new ArrayList<>(List.of(selector)), "at", new ArrayList<>(List.of(at))));
		MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE, name, desc, null, null);
		m.visibleAnnotations = new ArrayList<>(List.of(inject));
		m.instructions.add(new InsnNode(Opcodes.RETURN));
		return m;
	}

	private static List<String> names(ClassNode node) {
		return node.methods.stream().map(m -> m.name + m.desc).toList();
	}

	private static GuestInjectorPruner.Refused entry(boolean required) {
		return new GuestInjectorPruner.Refused("refused.mixins.json", MIXIN, "onPlace", REFUSED, "binds place(I)V", required);
	}

	@Test void aRememberedInjectorIsTakenOutWhileTheRuleStillSaysSoAndIsAFinding() {
		GuestInjectorPruner.rememberRefused(entry(true));
		ClassNode node = mixin();
		assertEquals(1, GuestInjectorPruner.pruneRefused(node, (n, h) -> "binds place(I)V, which the handler was not written for"));
		assertEquals(List.of("onTick" + KEPT), names(node), "the handler and its surrogate go, the other injector stays");
		List<CompatibilityFinding> findings = CompatibilityFindings.all();
		assertEquals(1, findings.size(), findings.toString());
		CompatibilityFinding finding = findings.get(0);
		assertEquals("mixin-injector:refused.mixins.json:test.RefusedMixin#onPlace" + REFUSED, finding.id());
		assertEquals(CompatibilityFinding.Confidence.CONFIRMED, finding.confidence());
		assertTrue(finding.required(), "the author's own count is at least one");
		assertTrue(finding.detail().contains("Invalid descriptor"), finding.detail());
	}

	@Test void anOptionalInjectorIsAFindingThatAsksNothing() {
		GuestInjectorPruner.rememberRefused(entry(false));
		GuestInjectorPruner.pruneRefused(mixin(), (n, h) -> "refused");
		assertFalse(CompatibilityFindings.all().get(0).required());
	}

	/** An adapter that moved the injector where it fits wins: the rule asked of the node says no, and it stays. */
	@Test void anInjectorAnAdapterAlreadyMovedStays() {
		GuestInjectorPruner.rememberRefused(entry(true));
		ClassNode node = mixin();
		assertEquals(0, GuestInjectorPruner.pruneRefused(node, (n, h) -> null));
		assertEquals(3, node.methods.size());
		assertTrue(CompatibilityFindings.all().isEmpty());
	}

	/** RED controls: either switch off and the mixin reaches Mixin whole. */
	@Test void eitherSwitchOffTakesNothingOut() {
		GuestInjectorPruner.rememberRefused(entry(true));
		System.setProperty(GuestInjectorPruner.REFUSED_PROPERTY, "off");
		ClassNode node = mixin();
		assertEquals(0, GuestInjectorPruner.pruneRefused(node, (n, h) -> "refused"));
		assertEquals(3, node.methods.size());
		System.clearProperty(GuestInjectorPruner.REFUSED_PROPERTY);
		System.setProperty(GuestInjectorPruner.PROPERTY, "off");
		assertFalse(GuestInjectorPruner.refusedEnabled());
		assertEquals(0, GuestInjectorPruner.pruneRefused(node, (n, h) -> "refused"));
		assertEquals(3, node.methods.size());
	}

	@Test void onlyAnInjectorNothingElseCallsAndNoGroupHoldsCanGoAlone() {
		ClassNode node = mixin();
		assertTrue(GuestInjectorPruner.prunable(node, "onPlace", REFUSED));
		assertFalse(GuestInjectorPruner.prunable(node, "onPlace", "()V"), "no such method");

		// Another method of the mixin calls it: taking it out would leave a call to nothing.
		MethodNode caller = new MethodNode(Opcodes.ACC_PRIVATE, "helper", "()V", null, null);
		caller.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
		caller.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
		caller.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
		caller.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MIXIN, "onPlace", REFUSED, false));
		caller.instructions.add(new InsnNode(Opcodes.RETURN));
		node.methods.add(caller);
		assertFalse(GuestInjectorPruner.prunable(node, "onPlace", REFUSED));

		ClassNode grouped = mixin();
		grouped.methods.get(0).visibleAnnotations.add(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Group;"));
		assertFalse(GuestInjectorPruner.prunable(grouped, "onPlace", REFUSED), "a group counts its members together");
	}

	@Test void theBytesTheVerdictJudgesAreTheOnesThePrunerLeaves() {
		ClassWriter writer = new ClassWriter(0);
		mixin().accept(writer);
		ClassNode after = new ClassNode();
		new ClassReader(GuestInjectorPruner.without(writer.toByteArray(), List.of("onPlace" + REFUSED))).accept(after, 0);
		assertEquals(List.of("onTick" + KEPT), names(after));
	}
}
