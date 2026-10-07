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

package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import net.fabricmc.api.EnvType;
import net.forbric.api.Side;
import net.forbric.kernel.transform.ExecutesInjector;
import net.forbric.kernel.transform.FabricFreezePointInjector;
import net.forbric.kernel.transform.InjectorExecution;

/**
 * WHEN the kernel reaches Fabric's registry freeze point, and what calling it does (issue #52).
 *
 * <p>On Fabric with fabric-api, fabric-registry-sync moves {@code BuiltInRegistries.freeze()} out of {@code Bootstrap}
 * to after every {@code main} entrypoint on a server, and every {@code main} and {@code client} one on a client. The
 * kernel keeps that freeze in {@code Bootstrap}, so the Fabric injectors on it are moved onto two hooks instead, and
 * {@code KernelLifecycle.fabricFreezePoint} is the call that stands in for the moved freeze. Create Fly's TAIL injector
 * creates its registries in the root: run in {@code Bootstrap}, before {@code Create.onInitialize}, it died on
 * "Registry is already frozen (minecraft:root / create:arm_interaction_point_type)" and the game never started.
 *
 * <p>The first half pins the call graph, as {@link FabricMainEntrypointWindowTest} does: there is no game here to boot.
 * javac copies a finally block once per way out of its try, and a hook that is in the right place in one copy and the
 * wrong place in another is wrong on exactly the path nobody runs by hand — so these walk every path out of the method
 * rather than reading the instructions top to bottom. The second half calls {@code fabricFreezePoint} against a
 * stand-in {@code BuiltInRegistries}.
 */
@ResourceLock("system-properties")
@ExecutesInjector(FabricFreezePointInjector.class)
class FabricFreezePointWindowTest {
	private static final String LIFECYCLE = "net/forbric/kernel/boot/KernelLifecycle";
	private static final String SIDE = Type.getInternalName(Side.class);
	private static final String HEAD_HOOK = FabricFreezePointInjector.HEAD_HOOK;
	private static final String TAIL_HOOK = FabricFreezePointInjector.TAIL_HOOK;

	// The steps a walk records, in the words the failure messages print.
	private static final String MAINS = "main entrypoints";
	private static final String CLIENTS = "client entrypoints";
	private static final String HEAD = "HEAD";
	private static final String TAIL = "TAIL";
	private static final String OPEN_ROOT = "open root";
	private static final String CLOSE_ROOT = "close root";
	private static final String CLOSE_WINDOW = "close window";
	private static final String RETURN = "return";
	private static final String THROW = "throw";

	private static final String FROZEN = "Registry is already frozen (minecraft:root / create:arm_interaction_point_type)";

	@BeforeEach
	@AfterEach
	void reset() {
		KernelLifecycle.forgetFabricFreezePoint();
		System.clearProperty(FabricFreezePointInjector.PROPERTY);
	}

	/**
	 * A client freezes where Fabric's {@code client.MinecraftMixin.afterModInit} does: after main, then client, then
	 * the HEAD half while the reopened window is still open, the freeze that closes it, and the TAIL half.
	 */
	@Test
	void theClientReachesTheFreezePointAfterMainAndClientAroundTheWindowsFreeze() throws Exception {
		MethodNode method = method("onClientEntrypoints");

		Walk run = walk(method, method.instructions.getFirst(), Set.of(MAINS, CLIENTS, HEAD, CLOSE_WINDOW, TAIL), null, false);

		assertEquals(Set.of(
				List.of(MAINS, CLIENTS, HEAD, CLOSE_WINDOW, TAIL, RETURN),
				// The window was never reopened (unfreeze threw): nothing to close, both halves still run.
				List.of(MAINS, CLIENTS, HEAD, TAIL, RETURN)), run.exits(),
				"onClientEntrypoints, run through: the HEAD half after the client entrypoints and before the freeze "
						+ "that closes their window, the TAIL half after it");
	}

	/**
	 * Every copy of the finally, including the one a throw takes. The client entrypoints are where a Fabric mod's
	 * failure surfaces; one that skipped the freeze point would leave Create's registries uncreated for the rest of
	 * the run, and that path is the copy nobody reads.
	 */
	@Test
	void everyWayOutOfTheClientEntrypointsReachesBothHalvesInOrder() throws Exception {
		MethodNode method = method("onClientEntrypoints");
		AbstractInsnNode clients = onlyCall(method, "runClientEntrypoints");

		Walk out = walk(method, clients, Set.of(HEAD, CLOSE_WINDOW, TAIL), null, true);

		assertEquals(Set.of(
				List.of(HEAD, CLOSE_WINDOW, TAIL, RETURN),
				List.of(HEAD, TAIL, RETURN),
				List.of(HEAD, CLOSE_WINDOW, TAIL, THROW),
				List.of(HEAD, TAIL, THROW)), out.exits(),
				"every path from runClientEntrypoints to the end of onClientEntrypoints, a throwing one included");
		List<AbstractInsnNode> sites = calls(method, "fabricFreezePoint");
		assertTrue(sites.size() >= 4, "the freeze point is called from a finally, which javac copies: " + sites.size());
		assertTrue(out.visited().containsAll(sites), "a copy of the finally was never walked, so never checked");
	}

	/**
	 * A server freezes where Fabric's {@code MainMixin.afterModInit} does: after every main, inside the span the root
	 * registry is open for them — Create Fly creates its registries there when fabric-api is absent — and the TAIL
	 * half only once the registration window is frozen again.
	 */
	@Test
	void theServerReachesHeadAfterItsMainsWithTheRootOpenAndTailAfterTheWindowFreezes() throws Exception {
		MethodNode method = method("registerNeoForgeContent");

		Walk run = walk(method, method.instructions.getFirst(),
				Set.of(OPEN_ROOT, MAINS, HEAD, CLOSE_ROOT, CLOSE_WINDOW, TAIL), false, false);

		assertEquals(Set.of(
				List.of(OPEN_ROOT, MAINS, HEAD, CLOSE_ROOT, CLOSE_WINDOW, TAIL, RETURN),
				// The `if (closeWindow)` the bytecode cannot see is always true here.
				List.of(OPEN_ROOT, MAINS, HEAD, CLOSE_ROOT, TAIL, RETURN)), run.exits(),
				"registerNeoForgeContent on a dedicated server, run through");
	}

	@Test
	void everyWayOutOfTheServerMainsReachesBothHalvesInOrder() throws Exception {
		MethodNode method = method("registerNeoForgeContent");
		AbstractInsnNode mains = onlyCall(method, "runMainEntrypoints");

		Walk out = walk(method, mains, Set.of(HEAD, CLOSE_ROOT, CLOSE_WINDOW, TAIL), false, true);

		assertEquals(Set.of(
				List.of(HEAD, CLOSE_ROOT, CLOSE_WINDOW, TAIL, RETURN),
				List.of(HEAD, CLOSE_ROOT, TAIL, RETURN),
				List.of(HEAD, CLOSE_ROOT, CLOSE_WINDOW, TAIL, THROW),
				List.of(HEAD, CLOSE_ROOT, TAIL, THROW)), out.exits(),
				"every path from runMainEntrypoints to the end of registerNeoForgeContent on a dedicated server, a "
						+ "throwing one included");
		assertTrue(out.visited().containsAll(calls(method, "fabricFreezePoint")),
				"a copy of a finally was never walked, so never checked");
	}

	/**
	 * Both calls in the registration window ask {@code side.isClient()} first, and on a client neither is reachable,
	 * not even on a throw. Each hook runs once per process, so a HEAD half spent here, before {@code Minecraft}
	 * exists, would be a no-op in {@code onClientEntrypoints} — the client's own freeze point would run only the TAIL
	 * half, and before the client entrypoints Fabric runs first.
	 */
	@Test
	void aClientNeverReachesTheFreezePointFromTheRegistrationWindow() throws Exception {
		MethodNode method = method("registerNeoForgeContent");
		boolean asks = calls(method, "isClient").stream().anyMatch(c -> SIDE.equals(((MethodInsnNode) c).owner));
		assertTrue(asks, "registerNeoForgeContent no longer asks side.isClient()");

		Walk client = walk(method, method.instructions.getFirst(), Set.of(HEAD, TAIL), true, true);

		assertEquals(Set.of(List.of(RETURN), List.of(THROW)), client.exits(),
				"on a client registerNeoForgeContent must reach neither half of the freeze point");
		List<AbstractInsnNode> sites = calls(method, "fabricFreezePoint");
		assertFalse(sites.isEmpty(), "registerNeoForgeContent no longer calls the freeze point at all");
		assertFalse(sites.stream().anyMatch(client.visited()::contains), "a client reaches a fabricFreezePoint call");
	}

	@Test
	void eachHookRunsOncePerProcess(@TempDir Path dir) throws Throwable {
		ClassLoader game = standIn(dir, COUNTING);

		KernelLifecycle.fabricFreezePoint(game, HEAD_HOOK);
		KernelLifecycle.fabricFreezePoint(game, HEAD_HOOK);
		assertEquals(1, count(game, "heads"), "the HEAD half ran again: every moved handler would register twice");
		assertEquals(0, count(game, "tails"), "the HEAD half spent the TAIL half's call");

		KernelLifecycle.fabricFreezePoint(game, TAIL_HOOK);
		KernelLifecycle.fabricFreezePoint(game, TAIL_HOOK);
		assertEquals(1, count(game, "tails"));

		// Per process, not per class: the server window and the client one are both in the one JVM.
		ClassLoader another = standIn(dir, COUNTING);
		KernelLifecycle.fabricFreezePoint(another, HEAD_HOOK);
		KernelLifecycle.fabricFreezePoint(another, TAIL_HOOK);
		assertEquals(0, count(another, "heads") + count(another, "tails"));
	}

	/**
	 * Fabric never runs the TAIL half without the HEAD half. A server whose window failed before its mains reaches only
	 * the TAIL call in the outer finally; running it there would make Create's TAIL injector fail a second time,
	 * against registries nobody created, and bury the first failure under it.
	 */
	@Test
	void theTailHalfWaitsForTheHeadHalf(@TempDir Path dir) throws Throwable {
		ClassLoader game = standIn(dir, COUNTING);

		KernelLifecycle.fabricFreezePoint(game, TAIL_HOOK);
		assertEquals(0, count(game, "tails"), "the TAIL half ran without the HEAD half");

		KernelLifecycle.fabricFreezePoint(game, HEAD_HOOK);
		KernelLifecycle.fabricFreezePoint(game, TAIL_HOOK);
		assertEquals(1, count(game, "heads"));
		assertEquals(1, count(game, "tails"), "a TAIL call skipped before HEAD spent the real one");
	}

	/**
	 * Create Fly's own failure, thrown from the TAIL half: reported with the mod's exception — not reflection's
	 * wrapper — and not rethrown, the way a failing entrypoint in the same window is. Nor is it tried again: the
	 * handlers before the one that threw have already run, and a second call would run them twice.
	 */
	@Test
	void aHookThatThrowsIsReportedAndNotRethrown(@TempDir Path dir) throws Throwable {
		ClassLoader game = standIn(dir, THROWING);
		KernelLifecycle.fabricFreezePoint(game, HEAD_HOOK);

		String log = capture(() -> KernelLifecycle.fabricFreezePoint(game, TAIL_HOOK));
		assertTrue(log.contains(TAIL_HOOK) && log.contains(FROZEN), "the failure was not reported with its cause:\n" + log);
		assertFalse(log.contains("InvocationTargetException"), "reported reflection's wrapper, not the mod's exception:\n" + log);

		KernelLifecycle.fabricFreezePoint(game, TAIL_HOOK);
		assertEquals(1, count(game, "heads"));
		assertEquals(1, count(game, "tails"), "a failed TAIL half was run again");
	}

	/**
	 * A {@code BuiltInRegistries} without the hooks (the injector switched off, so nothing was moved onto them), or no
	 * {@code BuiltInRegistries} at all: nothing waits for the freeze point, so there is nothing to call or warn about.
	 */
	@Test
	void aGameWithoutTheHooksIsLeftAlone(@TempDir Path dir) throws Exception {
		ClassLoader bare = standIn(dir, BARE);
		try (URLClassLoader none = new URLClassLoader(new URL[0], null)) {
			String log = capture(() -> {
				KernelLifecycle.fabricFreezePoint(bare, HEAD_HOOK);
				KernelLifecycle.fabricFreezePoint(bare, TAIL_HOOK);
				KernelLifecycle.forgetFabricFreezePoint();
				KernelLifecycle.fabricFreezePoint(none, HEAD_HOOK);
				KernelLifecycle.fabricFreezePoint(none, TAIL_HOOK);
			});
			assertFalse(log.contains("WARN"), "warned about a game with nothing waiting for the freeze point:\n" + log);
		}
	}

	@Test
	void switchedOffNothingIsCalled(@TempDir Path dir) throws Throwable {
		ClassLoader game = standIn(dir, COUNTING);

		System.setProperty(FabricFreezePointInjector.PROPERTY, "off");
		KernelLifecycle.fabricFreezePoint(game, HEAD_HOOK);
		KernelLifecycle.fabricFreezePoint(game, TAIL_HOOK);
		assertEquals(0, count(game, "heads") + count(game, "tails"),
				"switched off, the moved injectors stay on the bootstrap freeze and the kernel calls nothing");

		// The same stand-in, switched back on, is reached: the zero above is the switch, not a dead loader.
		System.clearProperty(FabricFreezePointInjector.PROPERTY);
		KernelLifecycle.fabricFreezePoint(game, HEAD_HOOK);
		assertEquals(1, count(game, "heads"));
