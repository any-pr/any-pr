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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import net.forbric.api.Ecosystem;
import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;

/**
 * The check that the jars a launch was handed are a Forbric game, by content (issue #13).
 *
 * <p>The jars here are built to the check's own definition -- {@link LaunchInputCheck#markers} for a carrier, a
 * {@code Block} with NeoForge's extension interface for the base -- and the staged test at the bottom holds that
 * definition to the real artifacts, so the two cannot agree with each other while disagreeing with the game.
 */
@ResourceLock("system-properties")
class LaunchInputCheckTest {
	private static final String NEO_EXTENSION = "net/neoforged/neoforge/common/extensions/IBlockExtension";

	@AfterEach
	void clearSwitch() {
		System.clearProperty(LaunchInputCheck.SWITCH);
	}

	/** A jar holding every marker of {@code families}. Entries are empty: the check reads names, not bytes. */
	private static Path carrier(Path jar, Ecosystem... families) throws IOException {
		try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
			put(zip, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
			for (Ecosystem family : families) {
				for (String marker : LaunchInputCheck.markers(family)) put(zip, marker, new byte[0]);
			}
		}
		return jar;
	}

	/** A jar with only some of a family's markers. */
	private static Path entries(Path jar, String... names) throws IOException {
		try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
			put(zip, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
			for (String name : names) put(zip, name, new byte[0]);
		}
		return jar;
	}

	/** A game jar whose {@code Block} implements {@code interfaces}, which is all the check reads of a base. */
	private static Path base(Path jar, String... interfaces) throws IOException {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "net/minecraft/world/level/block/Block", null, "java/lang/Object",
				interfaces);
		cw.visitEnd();
		try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
			put(zip, "version.json", "{\"id\":\"26.2\"}".getBytes(StandardCharsets.UTF_8));
			put(zip, LaunchInputCheck.BLOCK, cw.toByteArray());
		}
		return jar;
	}

	private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(bytes);
		zip.closeEntry();
	}

	private static Path gameBase(Path dir) throws IOException {
		return base(dir.resolve("patched-mc-neoforge-26.2.jar"), "net/minecraft/world/level/ItemLike", NEO_EXTENSION);
	}

	private static Path runtime(Path dir) throws IOException {
		return carrier(dir.resolve("neoforge-runtime-26.2.jar"), Ecosystem.NEOFORGE);
	}

	private static String all(List<String> problems) {
		return String.join("\n", problems);
	}

	@Test
	void aCompleteInstallHasNothingToSay(@TempDir Path dir) throws IOException {
		List<Path> runtimes = List.of(runtime(dir));

		assertEquals(List.of(), LaunchInputCheck.problems(List.of(gameBase(dir)), runtimes));
		assertDoesNotThrow(() -> LaunchInputCheck.require(List.of(gameBase(dir)), runtimes));
	}

	@Test
	void theCarrierIsRecognisedByContentInAnyOrderAndUnderAnyName(@TempDir Path dir) throws IOException {
		Path neo = carrier(dir.resolve("whatever.jar"), Ecosystem.NEOFORGE);
		assertEquals(List.of(), LaunchInputCheck.problems(List.of(gameBase(dir)), List.of(neo)));
	}

	@Test
	void aRuntimeJarHoldingNothingOfTheFamilyStopsTheLaunchNamingTheJarAndTheFix(@TempDir Path dir)
			throws IOException {
		// What the player's installer staged: right name, opened fine, nothing of the family inside.
		Path empty = entries(dir.resolve("neoforge-runtime-26.2.jar"));
		List<String> problems = LaunchInputCheck.problems(List.of(gameBase(dir)), List.of(empty));

		assertEquals(2, problems.size(), all(problems));
		assertTrue(problems.get(0).startsWith("runtime jar neoforge-runtime-26.2.jar contains no NeoForge"),
				problems.get(0));
		assertTrue(problems.get(0).contains("net/neoforged/neoforgespi/language/IModInfo.class"), problems.get(0));
		assertTrue(problems.get(0).contains(empty.toString()), "the player has to be able to find the file");
		assertTrue(problems.get(1).contains("contains a complete NeoForge runtime"), problems.get(1));

		String said = KernelLoadReportTest.capture(() -> assertThrows(LaunchInputCheck.Rejected.class,
				() -> LaunchInputCheck.require(List.of(gameBase(dir)), List.of(empty))));
		// ForbricLog's fallback when log4j is absent, as it is on the test classpath. In the game these are the same
		// lines in latest.log.
		assertTrue(said.contains("[Forbric/ERROR] [Forbric/Install] the Forbric install is broken"), said);
		assertTrue(said.contains("neoforge-runtime-26.2.jar contains no NeoForge"), said);
		assertTrue(said.contains("run the Forbric installer again with the same Game directory and leave "
				+ "\"Built artifacts\" EMPTY"), said);
	}

	@Test
	void aRuntimeJarThatIsMissingOrIsNoJarIsNamed(@TempDir Path dir) throws IOException {
		Path absent = dir.resolve("nope/neoforge-runtime-26.2.jar");
		Path garbage = dir.resolve("other-runtime-26.2.jar");
		Files.writeString(garbage, "<html>404 Not Found</html>");

		String problems = all(LaunchInputCheck.problems(List.of(gameBase(dir)), List.of(absent, garbage)));

		assertTrue(problems.contains("runtime jar neoforge-runtime-26.2.jar does not exist (" + absent + ")"), problems);
		assertTrue(problems.contains("runtime jar other-runtime-26.2.jar cannot be opened as a jar"), problems);
	}

	@Test
	void aCarrierWithOnlyPartOfItsFamilyIsCalledIncompleteAndSaysWhatIsMissing(@TempDir Path dir) throws IOException {
		List<String> neo = LaunchInputCheck.markers(Ecosystem.NEOFORGE);
		Path partial = entries(dir.resolve("neoforge-runtime-26.2.jar"), neo.get(1), neo.get(2), neo.get(3));

		List<String> problems = LaunchInputCheck.problems(List.of(gameBase(dir)), List.of(partial));

		assertEquals(2, problems.size(), all(problems));
		assertTrue(problems.get(0).startsWith("runtime jar neoforge-runtime-26.2.jar is an incomplete NeoForge "
				+ "runtime: it is missing " + neo.get(0)), problems.get(0));
		assertTrue(problems.get(1).contains("contains a complete NeoForge runtime"), problems.get(1));
	}

	/**
	 * A carrier with everything but FMLEnvironment. The base's {@code SharedConstants.<clinit>} is the first
	 * game code that runs and calls NeoForge's; vanilla's {@code Main} catches what it throws, prints it to stderr and
	 * exits 249, so without the marker this launch passed the check and ended with nothing in latest.log.
	 */
	@Test
	void aCarrierWithoutFmlEnvironmentIsIncomplete(@TempDir Path dir) throws IOException {
		assertTrue(LaunchInputCheck.markers(Ecosystem.NEOFORGE).contains("net/neoforged/fml/loading/FMLEnvironment.class"));

		String environment = LaunchInputCheck.markers(Ecosystem.NEOFORGE).stream()
				.filter(m -> m.endsWith("/FMLEnvironment.class")).findFirst().orElseThrow();
		Path stripped = entries(dir.resolve("neoforge-runtime-26.2.jar"), LaunchInputCheck.markers(Ecosystem.NEOFORGE)
				.stream().filter(m -> !m.equals(environment)).toArray(String[]::new));

		List<String> problems = LaunchInputCheck.problems(List.of(gameBase(dir)), List.of(stripped));

		assertEquals(2, problems.size(), all(problems));
		assertTrue(problems.get(0).startsWith("runtime jar neoforge-runtime-26.2.jar is an incomplete NeoForge "
				+ "runtime: it is missing " + environment + " ("), problems.get(0));
	}

	/**
	 * A NeoForge mod under a runtime's name has the family's manifest -- one of the markers -- and nothing else of
	 * it. Called an incomplete runtime, it read as a carrier missing its classes, which no mod has.
	 */
	@Test
	void aModJarUnderARuntimeNameIsCalledAModNotAnIncompleteRuntime(@TempDir Path dir) throws IOException {
		// The shape of sodium-neoforge-0.9.2+mc26.2.jar: the manifest, the mod itself nested, a few classes of its own.
		Path neoMod = entries(dir.resolve("neoforge-runtime-26.2.jar"), "META-INF/neoforge.mods.toml",
				"META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.9.2+mc26.2-mod.jar",
				"net/caffeinemc/mods/sodium/service/SodiumWorkarounds.class");

		List<String> problems = LaunchInputCheck.problems(List.of(gameBase(dir)), List.of(neoMod));

		assertEquals(2, problems.size(), all(problems));
		assertTrue(problems.get(0).startsWith("runtime jar neoforge-runtime-26.2.jar looks like a NeoForge mod, not a "
				+ "runtime: it has META-INF/neoforge.mods.toml, which mods carry too, and none of the runtime's classes ["
				+ "net/neoforged/neoforgespi/language/IModInfo.class, "), problems.get(0));
		assertFalse(problems.get(0).contains("incomplete"), problems.get(0));
		assertTrue(problems.get(1).contains("contains a complete NeoForge runtime"), problems.get(1));

		// A jar whose only manifest is the traditional MinecraftForge one is no longer an ecosystem this loader
		// runs: with none of the NeoForge markers either, it reads as a jar with nothing of the family inside.
		Path legacyForge = entries(dir.resolve("forge-runtime-interop.jar"), "META-INF/mods.toml");
		String said = LaunchInputCheck.problems(List.of(gameBase(dir)), List.of(legacyForge)).get(0);
		assertTrue(said.startsWith("runtime jar forge-runtime-interop.jar contains no NeoForge"), said);

		// A jar that has a runtime class besides its manifest is a broken runtime, not a mod.
		Path broken = entries(dir.resolve("broken.jar"), "META-INF/neoforge.mods.toml",
				LaunchInputCheck.markers(Ecosystem.NEOFORGE).get(2));
		said = LaunchInputCheck.problems(List.of(gameBase(dir)), List.of(broken)).get(0);
		assertTrue(said.startsWith("runtime jar broken.jar is an incomplete NeoForge runtime"), said);
	}

	@Test
	void aLaunchWithNoRuntimeJarCannotStartAndSaysSo(@TempDir Path dir) throws IOException {
		// No --runtimeJar at all: this always died -- the base and the kernel's own game side both name NeoForge
		// classes -- but with a NoClassDefFoundError on stderr and nothing in latest.log.
		String none = all(LaunchInputCheck.problems(List.of(gameBase(dir)), List.of()));
		assertTrue(none.contains("nothing this launch was given contains a complete NeoForge runtime"), none);
		assertTrue(none.contains("no --runtimeJar was passed at all"), none);
	}

	@Test
	void theGameJarHasToBeTheNeoForgeBase(@TempDir Path dir) throws IOException {
		List<Path> runtimes = List.of(runtime(dir));

		Path vanilla = base(dir.resolve("26.2.jar"), "net/minecraft/world/level/ItemLike");
		assertProblem(vanilla, runtimes, "game jar 26.2.jar is plain Minecraft, not Forbric's game");

		// gson renamed to the base's name passes a name match and the installer's link check alike.
		Path notMinecraft = entries(dir.resolve("patched-mc-neoforge-26.2.jar"), "com/google/gson/Gson.class");
		assertProblem(notMinecraft, runtimes, "game jar patched-mc-neoforge-26.2.jar is not a Minecraft game jar: it "
				+ "has no " + LaunchInputCheck.BLOCK);

		Path brokenBlock = dir.resolve("broken.jar");
		try (OutputStream out = Files.newOutputStream(brokenBlock); ZipOutputStream zip = new ZipOutputStream(out)) {
			put(zip, LaunchInputCheck.BLOCK, "not a class".getBytes(StandardCharsets.UTF_8));
		}
		assertProblem(brokenBlock, runtimes, "game jar broken.jar cannot be read as a game jar");

		assertProblem(dir.resolve("gone.jar"), runtimes, "game jar gone.jar does not exist");
		assertTrue(all(LaunchInputCheck.problems(List.of(), runtimes)).contains("no game jar was given"));
	}

	private static void assertProblem(Path base, List<Path> runtimes, String expected) {
		List<String> problems = LaunchInputCheck.problems(List.of(base), runtimes);
		assertEquals(1, problems.size(), all(problems));
		assertTrue(problems.get(0).startsWith(expected), problems.get(0));
	}

	@Test
	void aCarrierPassedAsOneMoreGameJarIsOwnedAllTheSame(@TempDir Path dir) throws IOException {
		Path neo = runtime(dir);

		assertEquals(List.of(), LaunchInputCheck.problems(List.of(gameBase(dir), neo), List.of()));
	}

	@Test
	void switchedOffItWarnsWithTheSameProblemsAndLaunchesAnyway(@TempDir Path dir) throws IOException {
		System.setProperty(LaunchInputCheck.SWITCH, "off");
		Path empty = entries(dir.resolve("neoforge-runtime-26.2.jar"));

		String said = KernelLoadReportTest.capture(() -> assertDoesNotThrow(
				() -> LaunchInputCheck.require(List.of(gameBase(dir)), List.of(empty))));
		assertTrue(said.contains("[Forbric/WARN] [Forbric/Install] the Forbric install is broken"), said);
		assertTrue(said.contains("neoforge-runtime-26.2.jar contains no NeoForge"), said);
		assertTrue(said.contains("-D" + LaunchInputCheck.SWITCH + "=off launches it anyway"), said);
	}

	/**
	 * The jars the check's own definition builds must be told apart from the real artifacts by nothing: a complete
	 * staged install answers none of the problems, and the real base and carrier answer every problem a synthetic
	 * impostor of the other shape earns. Skipped without the staged tree, as every staged test is.
	 */
	@Test
	void theStagedArtifactsAnswerTheSameQuestions(@TempDir Path dir) throws IOException {
		Path base = TestFixtures.stagedRoot().resolve("neoforge-base/patched-mc-neoforge-26.2.jar");
		Path carrierJar = TestFixtures.stagedRoot().resolve("neoforge-runtime/neoforge-runtime.jar");
		TestFixtures.requireFiles(Fixture.STAGED, "the staged game base and carrier", base, carrierJar);

		assertEquals(List.of(), LaunchInputCheck.problems(List.of(base), List.of(carrierJar)));

		Path impostorBase = base(dir.resolve("impostor-base.jar"));
		Path impostorRuntime = carrier(dir.resolve("impostor-runtime.jar"), Ecosystem.NEOFORGE);
