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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.minecraft.Hooks;

import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;
import net.forbric.kernel.boot.KernelFabricEcosystem;
import net.forbric.kernel.mixin.MixinFit;

/**
 * Fabric Loader's {@code Hooks.startServer}/{@code startClient} calls, which mods anchor on as "every mod has
 * initialised", must be in the kernel's game where the kernel's windows are.
 *
 * <p>owo freezes its network channels, particle controllers and registry-set calls from
 * {@code @Inject(at = @At(INVOKE, target = Hooks.startServer, shift = AFTER))} in a {@code @Group(min = 1)}. The
 * kernel's game had no such call, so the group failed with "expected 1 invocation(s) but 0 succeeded", owo's freeze
 * never ran, and the load report marked owo as only partly running — on the server and on the client alike.
 */
@ResourceLock("system-properties")
class FabricHooksCallTest {
	private static final Path MERGED_BASE = TestFixtures.stagedRoot().resolve("neoforge-base/patched-mc-neoforge-26.2.jar")
			.normalize();

	private static final String HOOKS = "net/fabricmc/loader/impl/game/minecraft/Hooks";
	private static final String HOOK_DESC = "(Ljava/io/File;Ljava/lang/Object;)V";
	private static final String KERNEL_LIFECYCLE = "net/forbric/kernel/boot/KernelLifecycle";
	private static final String SERVER_MOD_LOADER = "net/neoforged/neoforge/server/loading/ServerModLoader";
	private static final String MINECRAFT = "net/minecraft/client/Minecraft";
	private static final String OPTIONS = "net/minecraft/client/Options";

	private static final String QUILT_HOOKS = "org/quiltmc/loader/impl/game/minecraft/Hooks";
	private static final Path OWO = Path.of("build/compat-inputs/sweep90/mods/owo-lib-0.13.1+26.2.jar");

	@AfterEach
	void restore() {
		System.clearProperty(LifecycleHookInjector.FABRIC_HOOKS_SWITCH);
		System.clearProperty("forbric.mixinFit.groups");
	}

	// --- server ---------------------------------------------------------------------------------------------------

	/** Right after the kernel's window, as the only thing between it and the rest of {@code main}. */
	@Test
	void theServerMarkerFollowsTheKernelWindowWithFabricsArguments() throws Exception {
		byte[] out = LifecycleHookInjector.forServer().transform(LifecycleHookInjector.SERVER_MAIN, serverMain(), null);

		List<AbstractInsnNode> code = real(method(out, "main"));
		int window = indexOfCall(code, KERNEL_LIFECYCLE, "onServerModLoading");
		assertTrue(window >= 0, "the NeoForge trigger must still be redirected to the kernel");
		assertEquals(Opcodes.ACONST_NULL, code.get(window + 1).getOpcode(), "Fabric passes a null run directory");
		assertEquals(Opcodes.ACONST_NULL, code.get(window + 2).getOpcode(), "Fabric passes a null game instance");
		MethodInsnNode marker = (MethodInsnNode) code.get(window + 3);
		assertEquals(HOOKS, marker.owner);
		assertEquals("startServer", marker.name);
		assertEquals(HOOK_DESC, marker.desc);

		// Two more operand slots than the input needed, and the JVM's own verifier agrees.
		verify("net.minecraft.server.Main", out);
	}

	/**
	 * The NeoForge base only loads mods when the launch is not {@code --initSettings}; the marker must sit on that
	 * same path, never claiming mods were initialised when they were not.
	 */
	@Test
	void theServerMarkerIsOnlyReachedWhenTheWindowRan() {
		byte[] out = LifecycleHookInjector.forServer().transform(LifecycleHookInjector.SERVER_MAIN, serverMain(), null);

		MethodNode main = method(out, "main");
		JumpInsnNode skip = null;
		for (AbstractInsnNode insn : main.instructions) {
			if (insn instanceof JumpInsnNode jump) skip = jump;
		}
		assertNotNull(skip, "premise: the synthetic main branches around the loader, as the merged one does");
		int marker = main.instructions.indexOf(callTo(main, HOOKS, "startServer"));
		assertTrue(main.instructions.indexOf(skip.label) > marker,
				"the branch that skips mod loading must also skip the marker");
	}

	/**
	 * A base carrying BOTH server triggers gets one marker. SERVER_TRIGGERS keeps the no-argument form in case a base
	 * flips back to it; two markers would fail owo's {@code @Group(max = 1)} with "expected 1 but 2".
	 */
	@Test
	void aMainWithBothTriggersGetsOneMarker() throws Exception {
		byte[] out = LifecycleHookInjector.forServer().transform(LifecycleHookInjector.SERVER_MAIN, serverMainBothForms(),
				null);

		MethodNode main = method(out, "main");
		int markers = 0;
		for (AbstractInsnNode insn : main.instructions) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(HOOKS) && call.name.equals("startServer")) markers++;
		}
		assertEquals(1, markers, "one Hooks.startServer per method, whatever the number of triggers");
		assertTrue(indexOfCall(real(main), KERNEL_LIFECYCLE, "onServerModLoading") >= 0
				&& indexOfCall(real(main), KERNEL_LIFECYCLE, "onServerModLoadingNoArg") >= 0,
				"premise: both triggers were redirected");
		verify("net.minecraft.server.Main", out);
	}

	/** The switch puts back exactly the bytes from before: the plain owner+name redirect, nothing else. */
	@Test
	void switchedOffTheServerEntryIsExactlyTheRedirect() {
		System.setProperty(LifecycleHookInjector.FABRIC_HOOKS_SWITCH, "off");
		byte[] in = serverMain();

		byte[] out = LifecycleHookInjector.forServer().transform(LifecycleHookInjector.SERVER_MAIN, in, null);

		assertArrayEquals(redirectOnly(in), out);
	}

	/** The client ENTRY ({@code client.main.Main}) is not where Fabric's client hook goes; it gets no marker. */
	@Test
	void theClientEntryGetsNoServerMarker() {
		byte[] out = LifecycleHookInjector.forClient().transform(LifecycleHookInjector.CLIENT_MAIN, clientMain(), null);

		assertEquals(null, callTo(method(out, "main"), HOOKS, "startServer"));
	}

	@Test
	void onTheRealMainTheMarkerFollowsTheWindowOnItsOwnPath() throws Exception {
		byte[] real = mergedBase("net/minecraft/server/Main.class");

		LifecycleHookInjector injector = LifecycleHookInjector.forServer();
		byte[] out = injector.transform(LifecycleHookInjector.SERVER_MAIN, real, null);
		assertFalse(injector.missedRequiredExcision());

		MethodNode main = method(out, "main");
		List<AbstractInsnNode> code = real(main);
		int window = indexOfCall(code, KERNEL_LIFECYCLE, "onServerModLoading");
		assertTrue(window >= 0, "the merged Main.main must still carry the NeoForge trigger");
		MethodInsnNode marker = (MethodInsnNode) code.get(window + 3);
		assertEquals(HOOKS + ".startServer" + HOOK_DESC, marker.owner + "." + marker.name + marker.desc);

		// The merged main guards the trigger with `if (!initSettings)`; the guard's target must come after the marker.
		for (AbstractInsnNode insn : main.instructions) {
			if (insn instanceof JumpInsnNode jump && main.instructions.indexOf(jump) < main.instructions.indexOf(marker)
					&& main.instructions.indexOf(jump.label) > main.instructions.indexOf(code.get(window))) {
				assertTrue(main.instructions.indexOf(jump.label) > main.instructions.indexOf(marker),
						"a branch around the window must not land between the window and the marker");
			}
		}
	}

	/** The marker runs nothing: the window before it already did, and running mains here would be outside it. */
	@Test
	void theServerMarkerRunsNothing() {
		boolean before = KernelFabricEcosystem.mainsAlreadyRan();

		Hooks.startServer(null, null);

		assertEquals(before, KernelFabricEcosystem.mainsAlreadyRan());
	}

	// --- client ---------------------------------------------------------------------------------------------------

	/** {@code Hooks.startClient(this.gameDirectory, this)}, right before the first {@code new Options}. */
	@Test
	void theClientHookIsFabricsCallWithFabricsArguments() throws Exception {
		byte[] out = new ClientEntrypointHookInjector().transform("net.minecraft.client.Minecraft",
				minecraft(true), ctx());

		List<AbstractInsnNode> code = real(method(out, "<init>"));
		int options = indexOfNew(code, OPTIONS);
		MethodInsnNode hook = (MethodInsnNode) code.get(options - 1);
		assertEquals(HOOKS + ".startClient" + HOOK_DESC, hook.owner + "." + hook.name + hook.desc);
		assertEquals(Opcodes.ALOAD, code.get(options - 2).getOpcode(), "the game instance is `this`");
		assertEquals(0, ((VarInsnNode) code.get(options - 2)).var);
		FieldInsnNode dir = (FieldInsnNode) code.get(options - 3);
		assertEquals(MINECRAFT + ".gameDirectory:Ljava/io/File;", dir.owner + "." + dir.name + ":" + dir.desc);
		assertEquals(Opcodes.ALOAD, code.get(options - 4).getOpcode());
		assertEquals(null, callTo(method(out, "<init>"), KERNEL_LIFECYCLE, "onClientEntrypoints"),
				"the kernel's window now runs from inside Hooks.startClient — one call, not two");

		verify("net.minecraft.client.Minecraft", out);
	}

	/** A base without the field must not get a GETFIELD that fails the constructor: Fabric's null is passed instead. */
	@Test
	void withoutTheGameDirectoryFieldTheRunDirectoryIsNull() throws Exception {
		byte[] out = new ClientEntrypointHookInjector().transform("net.minecraft.client.Minecraft",
				minecraft(false), ctx());

		List<AbstractInsnNode> code = real(method(out, "<init>"));
		int options = indexOfNew(code, OPTIONS);
		assertEquals("startClient", ((MethodInsnNode) code.get(options - 1)).name);
		assertEquals(Opcodes.ACONST_NULL, code.get(options - 3).getOpcode());

		verify("net.minecraft.client.Minecraft", out);
	}

	@Test
	void switchedOffTheClientGetsTheBareKernelHook() {
		System.setProperty(LifecycleHookInjector.FABRIC_HOOKS_SWITCH, "off");

		byte[] out = new ClientEntrypointHookInjector().transform("net.minecraft.client.Minecraft",
				minecraft(true), ctx());

		List<AbstractInsnNode> code = real(method(out, "<init>"));
		MethodInsnNode hook = (MethodInsnNode) code.get(indexOfNew(code, OPTIONS) - 1);
		assertEquals(KERNEL_LIFECYCLE + ".onClientEntrypoints()V", hook.owner + "." + hook.name + hook.desc);
		assertEquals(null, callTo(method(out, "<init>"), HOOKS, "startClient"));
	}

	/** On the real constructor the field is assigned before the hook reads it, as on Fabric. */
	@Test
	void onTheRealMinecraftTheGameDirectoryIsAssignedBeforeTheHookReadsIt() throws Exception {
		byte[] real = mergedBase("net/minecraft/client/Minecraft.class");

		byte[] out = new ClientEntrypointHookInjector().transform("net.minecraft.client.Minecraft", real, ctx());

		MethodNode init = null;
		for (MethodNode m : read(out).methods) {
			if ("<init>".equals(m.name) && callTo(m, HOOKS, "startClient") != null) init = m;
		}
		assertNotNull(init, "the hook must be in the constructor");
		int hook = init.instructions.indexOf(callTo(init, HOOKS, "startClient"));
		int assigned = -1;
		for (AbstractInsnNode insn : init.instructions) {
			if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD && "gameDirectory".equals(f.name)) {
				assigned = init.instructions.indexOf(insn);
				break;
			}
		}
		assertTrue(assigned >= 0 && assigned < hook, "gameDirectory must be assigned before Hooks.startClient reads it");
		FieldInsnNode read = (FieldInsnNode) real(init).get(indexOfCall(real(init), HOOKS, "startClient") - 2);
		assertEquals("gameDirectory", read.name, "the run directory is the field, not a null, on the real base");
	}

	// --- what the call is for -------------------------------------------------------------------------------------

	/**
	 * owo's injector, judged the way the kernel judges every guest mixin: against the untransformed entry its anchor
	 * is missing — and the report names the class the anchor really belongs to — and against the transformed one it
	 * fits.
	 */
	@Test
	void anOwoShapedFreezeHookFitsOnlyOnceTheMarkerIsThere() {
		byte[] mixin = owoShapedMainMixin();
		byte[] before = serverMain();
		byte[] after = LifecycleHookInjector.forServer().transform(LifecycleHookInjector.SERVER_MAIN, before, null);

		MixinFit.Result missing = MixinFit.evaluate(mixin, name -> "net/minecraft/server/Main.class".equals(name) ? before : null);
		assertEquals(MixinFit.Verdict.PARTIAL, missing.verdict(), missing.unresolved().toString());
		assertEquals(List.of("@At(INVOKE) net.fabricmc.loader.impl.game.minecraft.Hooks.startServer in Main.main",
				"@At(INVOKE) org.quiltmc.loader.impl.game.minecraft.Hooks.startServer in Main.main"), missing.unresolved(),
				"each anchor names its owner in full -- two classes called Hooks -- and the method it was looked for in");

		// owo's real shape: the Fabric call and the Quilt call are alternatives of one @Group(min = 1, max = 1). The
		// Fabric one binds, so the group is satisfied and the Quilt miss is not a miss.
		MixinFit.Result fits = MixinFit.evaluate(mixin, name -> "net/minecraft/server/Main.class".equals(name) ? after : null);
		assertEquals(MixinFit.Verdict.FIT, fits.verdict(), fits.unresolved().toString());
