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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.Side;
import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;

/**
 * Where a client declares its datapack registries, and what happens to the list when the two initialisers behind
 * it run in either order.
 *
 * <p>The sweep pack's client forced {@code RegistryDataLoader.<clinit>} from {@code Main.main}, before any Fabric
 * main and with the root registry frozen. WorldWeaver's TAIL injector there ran wover-biome's datapack entrypoint,
 * which created the codec registry wover-biome's own main would have created, and the freeze threw. Both the loader
 * and NeoForge's {@code DataPackRegistriesHooks} were erroneous from then on: no world, no data maps.
 */
class DatapackRegistryDeclarationTest {
	@AfterEach
	void clearSwitches() {
		System.clearProperty(DatapackRegistryDeclaration.DEFERRAL_SWITCH);
		System.clearProperty(DatapackRegistryDeclaration.RECONCILE_SWITCH);
	}

	// --- when --------------------------------------------------------------------------------------------------

	@Test
	void aClientWhoseFabricMainsRunInTheConstructorWaitsForThem() {
		assertTrue(DatapackRegistryDeclaration.waitsForFabric(Side.CLIENT, true, true));
	}

	/** The server's mains already precede step 3a, and its log is the proof the order is clean there. */
	@Test
	void theDedicatedServerKeepsItsOrder() {
		assertFalse(DatapackRegistryDeclaration.waitsForFabric(Side.DEDICATED_SERVER, true, true));
	}

	@Test
	void nothingWaitsWhenThereIsNothingToWaitFor() {
		assertFalse(DatapackRegistryDeclaration.waitsForFabric(Side.CLIENT, false, true), "no Fabric mods");
		assertFalse(DatapackRegistryDeclaration.waitsForFabric(Side.CLIENT, true, false),
				"-Dforbric.fabricMainInConstructor=off already runs the mains before step 3a");
	}

	@Test
	void theSwitchDeclaresFromMainAgain() {
		System.setProperty(DatapackRegistryDeclaration.DEFERRAL_SWITCH, "off");
		assertFalse(DatapackRegistryDeclaration.waitsForFabric(Side.CLIENT, true, true));
	}

	/**
	 * The early window asks before declaring. An unconditional call there is the state that poisoned the client:
	 * a later edit that "simplifies" the guard away gets this, not a world that will not load.
	 */
	@Test
	void theEarlyWindowAsksBeforeDeclaring() throws Exception {
		MethodNode drive = method("driveNativeRegistration");
		assertTrue(drive != null,
				"KernelLifecycle.driveNativeRegistration not found in the compiled src/main classes, which exist "
						+ "before any test runs");

		int asks = firstCall(drive, "waitsForFabric");
		int declares = firstCall(drive, "registerDataPackRegistries");
		assertTrue(asks >= 0, "driveNativeRegistration must ask whether a client waits for its Fabric mains");
		assertTrue(declares > asks, "and only then declare");

		// And the answer decides it: the declaration sits only on the branch taken when the client does NOT wait.
		// Order alone would still pass with the call moved out of the else, declaring from Main.main every time.
		AbstractInsnNode[] insns = drive.instructions.toArray();
		AbstractInsnNode next = insns[asks + 1];
		while (next.getOpcode() < 0) next = next.getNext();
		assertTrue(next instanceof JumpInsnNode jump && (jump.getOpcode() == Opcodes.IFEQ || jump.getOpcode() == Opcodes.IFNE),
				"the waitsForFabric result must be branched on right away");
		JumpInsnNode branch = (JumpInsnNode) next;
		int target = drive.instructions.indexOf(branch.label);
		int from = drive.instructions.indexOf(branch);
		// IFEQ jumps when it does not wait: the declaration must be past the target, and the fall-through (the
		// waiting path) must leave by a GOTO that lands past the declaration too.
		boolean declaresWhenJumping = branch.getOpcode() == Opcodes.IFEQ;
		int waitingStart = declaresWhenJumping ? from : target;
		int waitingEnd = declaresWhenJumping ? target : insns.length;
		for (int i = waitingStart + 1; i < waitingEnd; i++) {
			if (insns[i] instanceof MethodInsnNode call && "registerDataPackRegistries".equals(call.name)) {
				throw new AssertionError("the waiting path declares too, at " + i);
			}
			if (insns[i] instanceof JumpInsnNode leave && leave.getOpcode() == Opcodes.GOTO) {
				assertTrue(drive.instructions.indexOf(leave.label) > declares,
						"the waiting path must jump past the declaration");
				break;
			}
		}
		assertTrue(!declaresWhenJumping || declares > target, "the declaration is on the not-waiting branch");
	}

	/**
	 * In the constructor hook the declaration comes after the Fabric mains AND after the window has closed again —
	 * every copy of the finally javac lays down — so it runs where native Fabric first initialises the loader.
	 */
	@Test
	void theConstructorHookDeclaresAfterTheWindowCloses() throws Exception {
		MethodNode hook = method("onClientEntrypoints");
		assertTrue(hook != null,
				"KernelLifecycle.onClientEntrypoints not found in the compiled src/main classes, which exist before "
						+ "any test runs");

		int declares = firstCall(hook, "registerDataPackRegistries");
		assertTrue(declares >= 0, "a client whose mains run in Minecraft.<init> must declare there");
		assertTrue(declares > lastCall(hook, "runClientEntrypoints"), "after the client entrypoints");
		assertTrue(declares > lastCall(hook, "closeClientEntrypointWindow"),
				"after the root is frozen again — the state the dedicated server declares in");
	}

	/** RegisterDataMapTypesEvent reads the declared list; client setup is where it is posted. */
	@Test
	void clientSetupCannotRunAheadOfTheDeclaration() throws Exception {
		MethodNode setup = method("onNeoClientSetup");
		assertTrue(setup != null,
				"KernelLifecycle.onNeoClientSetup not found in the compiled src/main classes, which exist before any "
						+ "test runs");

		int declares = firstCall(setup, "registerDataPackRegistries");
		int lifecycle = firstCall(setup, "fireClientSetupLifecycle");
		assertTrue(declares >= 0 && declares < lifecycle, "the declaration must precede client setup");
	}

	/** Three call sites on a client; a second post would hand every listener the event twice. */
	@Test
	void theDeclarationRunsOncePerProcess() throws Exception {
		MethodNode declare = method("registerDataPackRegistries");
		assertTrue(declare != null,
				"KernelLifecycle.registerDataPackRegistries not found in the compiled src/main classes, which exist "
						+ "before any test runs");

		MethodInsnNode first = null;
		for (AbstractInsnNode insn : declare.instructions.toArray()) {
			if (insn instanceof MethodInsnNode call) {
				first = call;
				break;
			}
		}
		assertNotNull(first);
		assertEquals("compareAndSet", first.name, "the once-guard must be the first thing it does");
	}

	// --- the two initialisers, in either order ------------------------------------------------------------------

	/**
	 * The premise of the whole fix, on stand-ins with the merged base's shape: NeoForge patched the loader's
	 * initialiser to call into the hooks just before it returns, and the hooks' initialiser copies the loader's
	 * list. Loader first, and the copy is taken before the TAIL injector runs.
	 */
	@Test
	void initialisingTheLoaderFirstLosesWhatItsTailInjectorAdds() throws Exception {
		ClassLoader fresh = freshPair();
		Class.forName(FakeLoader.class.getName(), true, fresh);

		assertEquals(List.of("minecraft:biome", "wover:biome_data"), worldgen(fresh));
		assertEquals(List.of("minecraft:biome"), neo(fresh),
				"NeoForge's copy is vanilla's: this is why the kernel must never force the loader first");
	}

	/** The hooks first — step 3a's order and native NeoForge's — and the copy sees the injector's entry. */
	@Test
	void initialisingTheHooksFirstSeesIt() throws Exception {
		ClassLoader fresh = freshPair();
		Class.forName(FakeHooks.class.getName(), true, fresh);

		assertEquals(List.of("minecraft:biome", "wover:biome_data"), neo(fresh));
	}

	/** Nothing the kernel controls picks the order, so the reconcile restores the entry in either one. */
	@Test
	void theReconcilePutsItBackWhicheverRanFirst() throws Exception {
		ClassLoader fresh = freshPair();
		Class.forName(FakeLoader.class.getName(), true, fresh);
		List<String> loader = worldgen(fresh);
		List<String> neo = neo(fresh);

		List<Object> replaced = new ArrayList<>();
		List<Object> declared = DatapackRegistryDeclaration.reconcile(loader, neo, Function.identity(),
				e -> neo.add((String) e), replaced);

		assertEquals(List.of("wover:biome_data"), declared);
		assertEquals(loader, neo);
		assertSame(loader.get(1), neo.get(1), "the loader's own entry, not a copy");
		assertTrue(replaced.isEmpty());
	}

	@Test
	void theReconcileIsANoOpWhenTheOrderWasRight() throws Exception {
		ClassLoader fresh = freshPair();
		Class.forName(FakeHooks.class.getName(), true, fresh);
		List<String> neo = neo(fresh);

		assertTrue(DatapackRegistryDeclaration.reconcile(worldgen(fresh), neo, Function.identity(),
				e -> neo.add((String) e), new ArrayList<>()).isEmpty());
		assertEquals(2, neo.size());
	}

	@Test
	void theReconcileSwitchLeavesNeoForgesListAsCopied() throws Exception {
		System.setProperty(DatapackRegistryDeclaration.RECONCILE_SWITCH, "off");
		ClassLoader fresh = freshPair();
		Class.forName(FakeLoader.class.getName(), true, fresh);
		List<String> neo = neo(fresh);

		assertTrue(DatapackRegistryDeclaration.reconcile(worldgen(fresh), neo, Function.identity(),
				e -> neo.add((String) e), new ArrayList<>()).isEmpty());
		assertEquals(List.of("minecraft:biome"), neo);
	}

	/** Declared by key, so an entry replaced in place cannot be fixed here — only named. */
	@Test
	void anEntryReplacedInPlaceIsNamedNotDeclaredTwice() {
		record Data(String key, String codec) { }
		Data vanilla = new Data("minecraft:biome", "vanilla");
		List<Data> neo = new ArrayList<>(List.of(vanilla));
		List<Data> loader = List.of(new Data("minecraft:biome", "mixin's"));

		List<Object> replaced = new ArrayList<>();
		List<Object> declared = DatapackRegistryDeclaration.reconcile(loader, neo, e -> ((Data) e).key(),
				e -> neo.add((Data) e), replaced);

		assertTrue(declared.isEmpty());
		assertEquals(List.of("minecraft:biome"), replaced);
		assertEquals(List.of(vanilla), neo);
	}

	/**
	 * The failure itself, on stand-ins: a TAIL injector in the loader's initialiser that creates a registry, as
	 * WorldWeaver's does for wover-biome's codec registry. Declared while the root is frozen and before the mod's main
	 * has created it -- the old order -- the initialiser throws and the class is dead for the process, every later
	 * touch a "Could not initialize class". After the main, with the root frozen again -- the new order -- it has
	 * nothing to create and initialises cleanly.
	 */
	@Test
	void theOldOrderPoisonsTheLoaderAndTheNewOneDoesNot() throws Exception {
		ClassLoader before = fresh(FakeRoot.class, FakeCodecRegistry.class, FakeWoverMain.class, FakeTailLoader.class);
		frozen(before, true);   // the pre-Minecraft window has closed; no Fabric main has run
		Throwable first = org.junit.jupiter.api.Assertions.assertThrows(ExceptionInInitializerError.class,
				() -> Class.forName(FakeTailLoader.class.getName(), true, before));
		assertTrue(String.valueOf(first.getCause()).contains("Registry is already frozen"), String.valueOf(first));
		NoClassDefFoundError later = org.junit.jupiter.api.Assertions.assertThrows(NoClassDefFoundError.class,
				() -> Class.forName(FakeTailLoader.class.getName(), true, before));
		assertTrue(DatapackRegistryDeclaration.couldNotInitialize(later.getMessage(), FakeTailLoader.class.getName()),
				later.getMessage());

		ClassLoader after = fresh(FakeRoot.class, FakeCodecRegistry.class, FakeWoverMain.class, FakeTailLoader.class);
		frozen(after, false);   // Minecraft.<init>: the kernel reopened the root for the Fabric mains
		Class.forName(FakeWoverMain.class.getName(), true, after).getMethod("onInitialize").invoke(null);
		frozen(after, true);    // and closed it again before declaring
		Class.forName(FakeTailLoader.class.getName(), true, after);
		assertEquals(List.of("wover:biome_codec"), keys(after), "created once, by the mod's own main");
	}

	// --- the report ---------------------------------------------------------------------------------------------

	/**
	 * Mixin 0.8.7 writes the method part of a merged injector's name as {@code %03x} and pads the class part to at
	 * least three letters, so both can be longer or hex: a later {@code wover_init}, and a mixin past the 4096th.
