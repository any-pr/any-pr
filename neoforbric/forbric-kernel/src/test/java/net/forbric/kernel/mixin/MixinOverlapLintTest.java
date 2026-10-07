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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.CompatibilityFindings;

/**
 * Two mods' mixins claiming one method in ways that cannot both take effect, on hand-built mixins against a hand-built
 * target: {@code t/Target.tick()V} calls {@code Callee.call()I} twice and {@code Callee.other()V} once, {@code idle()V}
 * is empty, and {@code inherited()V} comes from {@code t/Base}.
 */
class MixinOverlapLintTest {
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
	private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
	private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
	private static final String CALL = "Lt/Callee;call()I";
	private static final String OTHER = "Lt/Callee;other()V";

	private static final Map<String, byte[]> CLASSES = Map.of("t/Target.class", target(), "t/Base.class", base());

	@AfterEach
	void forget() {
		MixinOverlapLint.publish(List.of());
		CompatibilityFindings.reset();
		System.clearProperty(MixinOverlapLint.SWITCH);
	}

	// -------------------------------------------------------------------------------------------------------------
	// The rules
	// -------------------------------------------------------------------------------------------------------------

	@Test
	void twoModsOverwritingOneMethodIsR1() {
		List<MixinOverlapLint.Overlap> found = MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> overwrite(cw, "tick", "()V"))),
				claims("beta", mixin("b/BMixin", cw -> overwrite(cw, "tick", "()V")))));

		assertEquals(1, found.size(), found.toString());
		MixinOverlapLint.Overlap o = found.get(0);
		assertEquals(MixinOverlapLint.Rule.R1, o.rule());
		assertEquals("mixin-overlap:t.Target.tick()V", o.id());
		assertEquals(List.of("alpha", "beta"), List.of(o.first().modId(), o.second().modId()));
	}

	@Test
	void twoModsRedirectingOneCallIsR2WhateverSpellingAndWhenOneOrdinalIsOpen() {
		// One bare selector and one with the descriptor; one descriptor-form target and one dotted; one ordinal open.
		List<MixinOverlapLint.Overlap> found = MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> injector(cw, REDIRECT, "a", "tick", "INVOKE", CALL, -1, false))),
				claims("beta", mixin("b/BMixin", cw -> injector(cw, REDIRECT, "b", "tick()V", "INVOKE", "t/Callee.call()I",
						1, false)))));

		assertEquals(1, found.size(), found.toString());
		assertEquals(MixinOverlapLint.Rule.R2, found.get(0).rule());
		assertEquals("mixin-overlap:t.Target.tick()V@Lt/Callee;call()I", found.get(0).id());
	}

	@Test
	void redirectsOfDifferentOccurrencesOfOneCallDoNotOverlap() {
		assertEquals(List.of(), MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> injector(cw, REDIRECT, "a", "tick", "INVOKE", CALL, 0, false))),
				claims("beta", mixin("b/BMixin", cw -> injector(cw, REDIRECT, "b", "tick", "INVOKE", CALL, 1, false))))));
	}

	@Test
	void anOverwriteAndAnotherModsInjectorInTheMethodIsR3WithTheOverwriteFirst() {
		// "zeta" sorts after "alpha", so the order is the rule's, not the ids'.
		List<MixinOverlapLint.Overlap> found = MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> injector(cw, INJECT, "a", "tick", "HEAD", null, -1, true))),
				claims("zeta", mixin("z/ZMixin", cw -> overwrite(cw, "tick", "()V")))));

		assertEquals(1, found.size(), found.toString());
		assertEquals(MixinOverlapLint.Rule.R3, found.get(0).rule());
		assertEquals("zeta", found.get(0).first().modId());
		assertEquals("Inject", found.get(0).second().kind());
		assertEquals("mixin-overlap:t.Target.tick()V", found.get(0).id());
	}

	@Test
	void aRedirectAndAnotherModsWrapOperationOfTheCallIsANoteAndNoFinding() {
		List<MixinOverlapLint.Overlap> found = MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> injector(cw, WRAP_OPERATION, "a", "tick", "INVOKE", CALL, -1, false))),
				claims("beta", mixin("b/BMixin", cw -> injector(cw, REDIRECT, "b", "tick", "INVOKE", CALL, -1, false)))));

		assertEquals(1, found.size(), found.toString());
		assertEquals(MixinOverlapLint.Rule.R4, found.get(0).rule());
		assertEquals("Redirect", found.get(0).first().kind());
		assertFalse(found.get(0).rule().conflict);
		assertEquals(List.of(), MixinOverlapLint.findings(found));
	}

	// -------------------------------------------------------------------------------------------------------------
	// What is not an overlap
	// -------------------------------------------------------------------------------------------------------------

	@Test
	void oneModsOwnMixinsNeverOverlap() {
		// Two configs of one mod, and two mods of one installed jar (C2ME's modules).
		assertEquals(List.of(), MixinOverlapLint.overlaps(join(
				MixinOverlapLint.claims("alpha", "alpha", "one.mixins.json", "mixins",
						mixin("a/AMixin", cw -> overwrite(cw, "tick", "()V")), CLASSES::get),
				MixinOverlapLint.claims("alpha", "alpha", "two.mixins.json", "mixins",
						mixin("a/BMixin", cw -> overwrite(cw, "tick", "()V")), CLASSES::get))));
		assertEquals(List.of(), MixinOverlapLint.overlaps(join(
				MixinOverlapLint.claims("c2me-base", "c2me", "base.mixins.json", "mixins",
						mixin("a/AMixin", cw -> injector(cw, INJECT, "a", "tick", "HEAD", null, -1, true)), CLASSES::get),
				MixinOverlapLint.claims("c2me-rewrites", "c2me", "rewrites.mixins.json", "mixins",
						mixin("a/BMixin", cw -> overwrite(cw, "tick", "()V")), CLASSES::get))));
	}

	@Test
	void twoBuildsOfOneModIdNeverOverlap() {
		// Sodium's Fabric and NeoForge jars side by side: arbitration keeps one before Mixin sees either.
		assertEquals(List.of(), MixinOverlapLint.overlaps(join(
				MixinOverlapLint.claims("sodium", "sodium-fabric.jar", "sodium-fabric.mixins.json", "mixins",
						mixin("a/AMixin", cw -> overwrite(cw, "tick", "()V")), CLASSES::get),
				MixinOverlapLint.claims("sodium", "sodium-neoforge.jar", "sodium-neoforge.mixins.json", "mixins",
						mixin("a/BMixin", cw -> overwrite(cw, "tick", "()V")), CLASSES::get))));
	}

	@Test
	void redirectsOfDifferentCallsDoNotOverlap() {
		assertEquals(List.of(), MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> injector(cw, REDIRECT, "a", "tick", "INVOKE", CALL, -1, false))),
				claims("beta", mixin("b/BMixin", cw -> injector(cw, REDIRECT, "b", "tick", "INVOKE", OTHER, -1, false))))));
	}

	@Test
	void twoInjectsAtHeadDoNotOverlap() {
		assertEquals(List.of(), MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> injector(cw, INJECT, "a", "tick", "HEAD", null, -1, true))),
				claims("beta", mixin("b/BMixin", cw -> injector(cw, INJECT, "b", "tick", "HEAD", null, -1, true))))));
	}

	@Test
	void aClientMixinAndAServerMixinNeverMeet() {
		assertEquals(List.of(), MixinOverlapLint.overlaps(join(
				MixinOverlapLint.claims("alpha", "alpha", "a.mixins.json", "client",
						mixin("a/AMixin", cw -> overwrite(cw, "tick", "()V")), CLASSES::get),
				MixinOverlapLint.claims("beta", "beta", "b.mixins.json", "server",
						mixin("b/BMixin", cw -> overwrite(cw, "tick", "()V")), CLASSES::get))));
	}

	// -------------------------------------------------------------------------------------------------------------
	// Claims
	// -------------------------------------------------------------------------------------------------------------

	@Test
	void aClaimIsOnlyMadeForAMethodTheSelectorPinsAndTheTargetDeclares() {
		// A wildcard is not judged; a bare name on a class nothing can read pins nothing; a full descriptor does;
		// an inherited method is not injected into.
		assertEquals(List.of(), claims("alpha", mixin("a/AMixin", cw -> injector(cw, INJECT, "a", "*", "HEAD", null, -1, true))));
		assertEquals(List.of(), MixinOverlapLint.claims("alpha", "a.mixins.json",
				mixin("a/AMixin", cw -> injector(cw, INJECT, "a", "tick", "HEAD", null, -1, true)), name -> null));
		List<MixinOverlapLint.Claim> unseen = MixinOverlapLint.claims("alpha", "a.mixins.json",
				mixin("a/AMixin", cw -> injector(cw, INJECT, "a", "tick()V", "HEAD", null, -1, true)), name -> null);
		assertEquals(List.of("tick()V"), unseen.stream().map(c -> c.method() + c.desc()).toList());
		assertEquals(List.of(), claims("alpha", mixin("a/AMixin", cw -> injector(cw, INJECT, "a", "inherited", "HEAD",
				null, -1, true))));
	}

	// -------------------------------------------------------------------------------------------------------------
	// Findings, the runtime pass, the crash lookup
	// -------------------------------------------------------------------------------------------------------------

	@Test
	void eachModInAConflictGetsOneSuspectedFindingNamingTheOthers() {
		List<MixinOverlapLint.Overlap> found = MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> overwrite(cw, "tick", "()V"))),
				claims("beta", mixin("b/BMixin", cw -> overwrite(cw, "tick", "()V"))),
				claims("gamma", mixin("c/CMixin", cw -> overwrite(cw, "tick", "()V")))));
		assertEquals(3, found.size(), found.toString());

		List<CompatibilityFinding> findings = MixinOverlapLint.findings(found);

		assertEquals(List.of("alpha", "beta", "gamma"), findings.stream().map(CompatibilityFinding::modId).sorted().toList());
		for (CompatibilityFinding f : findings) {
			assertEquals("mixin-overlap:t.Target.tick()V", f.id());
			assertEquals(CompatibilityFinding.Confidence.SUSPECTED, f.confidence());
			assertFalse(f.required());
			assertTrue(f.detail().contains("higher priority"), "says which overwrite Mixin keeps: " + f.detail());
			for (String other : List.of("alpha", "beta", "gamma")) {
				assertEquals(!other.equals(f.modId()), f.detail().contains(other), f.modId() + ": " + f.detail());
			}
		}
	}

	@Test
	void theRuntimePassReadsTheConfigsAsServedAndRecordsWhatItFinds() {
		Map<String, byte[]> resources = new HashMap<>(CLASSES);
		resources.put("a/AMixin.class", mixin("a/AMixin", cw -> overwrite(cw, "tick", "()V")));
		resources.put("a/AIdle.class", mixin("a/AIdle", cw -> overwrite(cw, "idle", "()V")));
		resources.put("b/BMixin.class", mixin("b/BMixin", cw -> overwrite(cw, "tick", "()V")));
		resources.put("b/Dropped.class", mixin("b/Dropped", cw -> overwrite(cw, "idle", "()V")));
		resources.put("c/CMixin.class", mixin("c/CMixin", cw -> overwrite(cw, "idle", "()V")));
		Map<String, byte[]> served = Map.of(
				"a.mixins.json", config("a", "AMixin", "AIdle"),
				// The kernel dropped Dropped from b's config before Mixin read it: it cannot be half of an overlap.
				"b.mixins.json", config("b", "BMixin"),
				// No single mod owns c's config: its overwrite of idle would be an accusation with nobody behind it.
				"c.mixins.json", config("c", "CMixin"));
		Map<String, String> owners = Map.of("a.mixins.json", "alpha", "b.mixins.json", "beta");

		List<MixinOverlapLint.Overlap> found = MixinOverlapLint.report(List.of("a.mixins.json", "b.mixins.json",
				"c.mixins.json"), served::get, resources::get, net.fabricmc.api.EnvType.CLIENT, owners::get, id -> id);

		assertEquals(List.of(MixinOverlapLint.Rule.R1), found.stream().map(MixinOverlapLint.Overlap::rule).toList());
		assertEquals(found, MixinOverlapLint.recorded());
		assertEquals(List.of("alpha:mixin-overlap:t.Target.tick()V", "beta:mixin-overlap:t.Target.tick()V"),
				CompatibilityFindings.suspected().stream().map(CompatibilityFinding::key).toList());
		assertEquals(1, MixinOverlapLint.conflictsIn("t.Target", "tick").size());
		assertEquals(List.of(), MixinOverlapLint.conflictsIn("t.Target", "idle"));
	}

	@Test
	void theSwitchTurnsTheRuntimePassOff() {
		List<MixinOverlapLint.Overlap> seeded = MixinOverlapLint.overlaps(join(
				claims("alpha", mixin("a/AMixin", cw -> overwrite(cw, "tick", "()V"))),
				claims("beta", mixin("b/BMixin", cw -> overwrite(cw, "tick", "()V")))));
		MixinOverlapLint.publish(seeded);

		System.setProperty(MixinOverlapLint.SWITCH, "off");
		MixinOverlapLint.reportRegistered();
		assertEquals(seeded, MixinOverlapLint.recorded(), "off must not run the pass");

		System.clearProperty(MixinOverlapLint.SWITCH);
		MixinOverlapLint.reportRegistered();
		assertEquals(List.of(), MixinOverlapLint.recorded(), "on, with no config registered, the pass finds nothing");
	}

	@Test
	void theKernelLintsAfterMixinReadTheConfigsAndBeforeTheFirstReport() throws Exception {
		// Read from the compiled bytecode: KernelBoot.java carries NUL bytes that make grep skip lines.
		Path compiled = Path.of(System.getProperty("user.dir"), "build", "classes", "java", "main",
				"net", "forbric", "kernel", "boot", "KernelBoot.class");
		ClassNode boot = new ClassNode();
		new ClassReader(Files.readAllBytes(compiled)).accept(boot, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
		List<String> calls = new ArrayList<>();
		for (MethodNode m : boot.methods) {
			if (m.instructions == null) continue;
			for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC) {
					calls.add(m.name + ">" + call.owner.substring(call.owner.lastIndexOf('/') + 1) + "." + call.name);
				}
			}
		}
		int init = calls.indexOf("launch>KernelMixinBootstrap.init");
		int lint = calls.indexOf("launch>MixinOverlapLint.reportRegistered");
		int evidence = calls.indexOf("launch>KernelLoadReport.writeEvidence");
		assertTrue(init >= 0 && lint >= 0 && evidence >= 0, calls.toString());
		assertEquals(lint, calls.lastIndexOf("launch>MixinOverlapLint.reportRegistered"), "called once");
		assertTrue(init < lint && lint < evidence, "init " + init + ", lint " + lint + ", evidence " + evidence);
	}

	// -------------------------------------------------------------------------------------------------------------
	// Fixtures
	// -------------------------------------------------------------------------------------------------------------
