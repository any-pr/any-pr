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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.forbric.api.Ecosystem;
import net.forbric.api.ModCatalog;
import net.forbric.kernel.mixin.MixinOverlapLint;

/**
 * Naming the mods a crash report points at.
 *
 * <p>The inputs are real crash reports this loader produced rather than hand-written traces: the whole value of
 * this feature is that it works on what the game actually writes, and a fixture I wrote myself would agree with my
 * code by construction. They are copied into crash-attribution/ beside this class, with the machine's hardware
 * values blanked and every section kept, because the originals under run/ exist only on the machine that crashed,
 * and every test here that read them skipped everywhere else.
 */
class CrashAttributionTest {
	@TempDir
	Path tmp;

	/** supermartijn642corelib's own frame on top: "Container screen registered with null menu type!". */
	private static final String CORE_LIB = "crash-2026-09-20_17.17.35-client.txt";
	/** A worldgen crash ("Feature placement") with Minecraft's whole walkthrough below the trace. */
	private static final String WORLDGEN = "crash-2026-07-12_13.23.39-client.txt";
	/** Mixin naming the mod that failed to apply, from the old forbric-loader (the repository's crash/ folder). */
	private static final String MIXIN_APPLY = "crash-report.txt";

	@AfterEach
	void clearCatalogue() {
		ModCatalog.publish(List.of());
		MixinOverlapLint.publish(List.of());
	}

	/** An overlap of {@code rule} between {@code first} and {@code second} on {@code ClassInstanceMultiMap.find}. */
	private static MixinOverlapLint.Overlap overlap(MixinOverlapLint.Rule rule, String first, String second) {
		String owner = "net/minecraft/util/ClassInstanceMultiMap";
		String desc = "(Ljava/lang/Class;)Ljava/util/Collection;";
		boolean call = rule == MixinOverlapLint.Rule.R4;
		return new MixinOverlapLint.Overlap(rule,
				new MixinOverlapLint.Claim(first, first + ".mixins.json", "a.FindMixin", "find", call ? "Redirect" : "Overwrite",
						owner, "find", desc, call ? "INVOKE" : null, call ? "Lx/Y;z()V" : null, -1, "mixins", first),
				new MixinOverlapLint.Claim(second, second + ".mixins.json", "b.FindMixin", "find",
						call ? "WrapOperation" : "Overwrite", owner, "find", desc, call ? "INVOKE" : null,
						call ? "Lx/Y;z()V" : null, -1, "mixins", second));
	}

	/** A trace whose only frame of interest is the overwritten method, under Minecraft's jar and name. */
	private static final String THROUGH_FIND = "java.lang.ClassCastException: class a cannot be cast to class b\n"
			+ "\tat forbric/net.minecraft.util.ClassInstanceMultiMap.find(ClassInstanceMultiMap.java:62) "
			+ "~[patched-mc-merged-26.2.jar:?] {}\n"
			+ "\tat forbric/net.minecraft.world.level.entity.EntitySection.getEntities(EntitySection.java:40) "
			+ "~[patched-mc-merged-26.2.jar:?] {}\n";

	private static ModCatalog.Entry mod(String id, String name, String jar) {
		return new ModCatalog.Entry(Ecosystem.FABRIC, id, name, "1.0", "", List.of(), jar, "", "");
	}

	/**
	 * The fixture {@code name}, which must still carry Minecraft's own sections: several tests here assert that
	 * the attribution does NOT read them, and against a file without them those assertions would pass vacuously.
	 */
	private static String report(String name) throws Exception {
		try (InputStream in = CrashAttributionTest.class.getResourceAsStream("crash-attribution/" + name)) {
			assertNotNull(in, "crash-attribution/" + name + " is a committed test resource");
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			assertTrue(text.contains("-- System Details --") && text.contains("Mod List:"),
					name + " must keep the System Details section and its Mod List");
			return text;
		}
	}

	@Test
	void everyFixtureKeepsTheSectionsTheAttributionMustIgnore() throws Exception {
		for (String name : List.of(CORE_LIB, WORLDGEN, MIXIN_APPLY)) {
			String text = report(name);
			assertTrue(CrashAttribution.exceptionChain(text).length() < text.length(), name);
		}
	}

	@Test
	void theTopFrameSJarNamesTheMod() throws Exception {
		ModCatalog.publish(List.of(
				mod("supermartijn642corelib", "SuperMartijn642's Core Lib",
						"supermartijn642corelib-1.1.24a-forge-mc26.2.jar"),
				mod("sodium", "Sodium", "sodium-fabric-0.9.0.jar")));

		List<CrashAttribution.Suspect> suspects = CrashAttribution.suspects(report(CORE_LIB));

		assertFalse(suspects.isEmpty(), "the top frame of this crash is a mod jar");
		assertEquals("supermartijn642corelib", suspects.get(0).modId(),
				"the frame that threw is a better suspect than the frame that called it");
		// A mod that is installed but nowhere in the trace is not a suspect.
		assertTrue(suspects.stream().noneMatch(s -> s.modId().equals("sodium")), suspects.toString());
	}

	@Test
	void theKernelAndTheGameAreNeverSuspects() throws Exception {
		// Nothing published at all: the catalogue is the deny-list. Every frame in this report belongs to the
		// merged base, a runtime carrier, the kernel jar or the JDK, and none of those is a mod.
		ModCatalog.publish(List.of());

		assertEquals(List.of(), CrashAttribution.suspects(report(CORE_LIB)),
				"patched-mc-merged, the carriers and forbric-kernel's own jar are not mods");
	}

	@Test
	void aJarNameIsNeverTurnedIntoAModId() throws Exception {
		// The jar is in the trace and the catalogue has a mod, but the mod's jar is spelled differently. A
		// file name is not a mod id -- xaeroworldmap-*.jar carries xaerominimap-family ids, and jars renamed to
		// a content hash are real -- so this must find nothing rather than guess from the name.
		ModCatalog.publish(List.of(mod("supermartijn642corelib", "Core Lib", "some-other-name.jar")));

		assertEquals(List.of(), CrashAttribution.suspects(report(CORE_LIB)));
	}

	@Test
	void aMixinHandlerInsideAVanillaClassBlamesTheModAndNotMinecraft() {
		// The one way the jar bracket gets it backwards: this is sodium's code, under a Minecraft class name,
		// carrying the merged base's jar.
		ModCatalog.publish(List.of(
				mod("sodium", "Sodium", "sodium-fabric-0.9.0.jar"),
				mod("minecraft", "Minecraft", "patched-mc-merged-26.2.jar")));
		String trace = "java.lang.NullPointerException\n"
				+ "\tat forbric/net.minecraft.client.Minecraft.handler$chm000$sodium$loadConfig"
				+ "(Minecraft.java:11106) ~[patched-mc-merged-26.2.jar:?] {}\n";

		List<CrashAttribution.Suspect> suspects = CrashAttribution.suspects(trace);

		assertEquals("sodium", suspects.get(0).modId(), suspects.toString());
		assertEquals("its mixin was running", suspects.get(0).reason());
	}

	@Test
	void modsTheErrorNamesAsClashingComeBeforeTheModWhoseCodeThrew() {
		// Verbatim head of a real report: chloride + Cubes Without Borders + sodium-neoforge on this loader. Both
		// override Sodium's fullscreen option and Sodium refuses the pair; its own handler frame is the first one.
		ModCatalog.publish(List.of(
				mod("chloride", "Chloride", "chloride-NEOFORGE-mc26.2-v1.8.1.jar"),
				mod("cwb", "Cubes Without Borders", "cwb-4.1.0+26.2.jar"),
				mod("sodium", "Sodium", "sodium-neoforge-0.9.2+mc26.2.jar")));
		String report = "---- Minecraft Crash Report ----\n"
				+ "Description: Failed to build config options\n\n"
				+ "java.lang.IllegalArgumentException: Multiple overrides for option 'sodium:general.fullscreen_mode'! "
				+ "Sources: chloride and cwb\n"
				+ "\tat forbric/net.caffeinemc.mods.sodium.client.config.structure.Config.applyOptionChanges(Config.java:131) "
				+ "~[net.caffeinemc.sodium-neoforge-0.9.2+mc26.2-mod.jar:?] {}\n"
				+ "\tat forbric/net.minecraft.client.Minecraft.handler$zca000$sodium$postInit(Minecraft.java:5117) "
				+ "[patched-mc-merged-26.2.jar:?] {}\n";

		List<CrashAttribution.Suspect> suspects = CrashAttribution.suspects(report);

		assertEquals(List.of("chloride", "cwb", "sodium"), suspects.stream().map(CrashAttribution.Suspect::modId).toList());
		assertEquals(CrashAttribution.CLASH, suspects.get(0).reason());
		assertEquals(CrashAttribution.CLASH, suspects.get(1).reason());
		String en = CrashAttribution.render(false, "crash.txt", suspects);
		assertTrue(en.contains("clash with each other") && en.contains("Chloride, Cubes Without Borders cannot be installed together"), en);
		assertFalse(en.contains("Take the first one out"), en);
		String zh = CrashAttribution.render(true, "crash.txt", suspects);
		assertTrue(zh.contains("互相冲突") && zh.contains("Chloride、Cubes Without Borders 不能装在一起"), zh);
	}

	@Test
	void aSourcesListNamingOnlyOneInstalledModIsNotAClash() {
		ModCatalog.publish(List.of(mod("cwb", "Cubes Without Borders", "cwb.jar")));
		List<CrashAttribution.Suspect> suspects = CrashAttribution.suspects(
				"java.lang.IllegalArgumentException: Multiple overrides for option 'x'! Sources: somethingelse and cwb\n");
		assertEquals(List.of("cwb"), suspects.stream().map(CrashAttribution.Suspect::modId).toList());
		assertFalse(CrashAttribution.render(false, "crash.txt", suspects).contains("clash with each other"),
				"one named side is not a pair the player can choose between");
	}

	@Test
	void aHandlerWhoseTokenIsNotAModIdSaysNothing() {
		// About half the handler frames in run/ carry a method name in that position rather than a mod id --
		// handler$zpf000$mutableSpecialElementRenderers. The token is Mixin's convention, not a guarantee, so
		// it counts only when the catalogue already knows it.
		ModCatalog.publish(List.of(mod("sodium", "Sodium", "sodium-fabric-0.9.0.jar")));
		String trace = "java.lang.IllegalStateException\n"
				+ "\tat forbric/net.minecraft.client.Minecraft.handler$zpf000$mutableSpecialElementRenderers"
				+ "(Minecraft.java:900) ~[patched-mc-merged-26.2.jar:?] {}\n";

		assertEquals(List.of(), CrashAttribution.suspects(trace));
	}

	@Test
	void mixinsOwnWordsAreBelievedWhenTheyNameAMod() throws Exception {
		ModCatalog.publish(List.of(mod("sodium", "Sodium", "sodium-fabric-0.9.0.jar")));

		List<CrashAttribution.Suspect> suspects = CrashAttribution.suspects(report(MIXIN_APPLY));

		// "Mixin [sodium-common.mixins.json:...LevelExtractorMixin from mod sodium] ... FAILED during APPLY".
		// The whole crash is sodium's, and the Suspected Mods line in that very file says NONE.
		assertEquals("sodium", suspects.get(0).modId(), suspects.toString());
		assertEquals("Mixin named it", suspects.get(0).reason());
	}

	@Test
	void onlyTheExceptionChainIsRead() throws Exception {
		String whole = report(WORLDGEN);
		String chain = CrashAttribution.exceptionChain(whole);

		assertTrue(chain.length() < whole.length(), "the walkthrough divider must cut the file");
		assertFalse(chain.contains("-- System Details --"), chain);
		// Everything below the divider is Minecraft's own report -- thread dumps, the mod lists, the graphics
		// card. A mod named there is named for being installed, not for being involved.
		assertFalse(chain.contains("Mod List:"), "the mod list is not evidence");
	}

	@Test
	void theAnswerIsAHandfulOfNamesRatherThanASecondList() {
		List<ModCatalog.Entry> many = new java.util.ArrayList<>();
		StringBuilder trace = new StringBuilder("java.lang.RuntimeException\n");
		for (int i = 0; i < 20; i++) {
			many.add(mod("mod" + i, "Mod " + i, "mod" + i + ".jar"));
			trace.append("\tat forbric/com.x.Y.z(Y.java:1) ~[mod").append(i).append(".jar:?] {}\n");
		}
		ModCatalog.publish(many);

		List<CrashAttribution.Suspect> suspects = CrashAttribution.suspects(trace.toString());
		assertEquals(CrashAttribution.MOST, suspects.size(), "past a handful this stops being an answer");
		assertEquals("mod0", suspects.get(0).modId(), "topmost frame first");
	}

	@Test
	void aCrashThroughAMethodTwoModsOverwriteNamesBothOfThem() {
		ModCatalog.publish(List.of(mod("lithium", "Lithium", "lithium-fabric-0.25.3+mc26.2.jar"),
				mod("vmp", "Very Many Players", "vmp-fabric-mc26.2-0.2.0.jar"),
				mod("minecraft", "Minecraft", "patched-mc-merged-26.2.jar")));
		MixinOverlapLint.publish(List.of(overlap(MixinOverlapLint.Rule.R1, "lithium", "vmp")));

		List<CrashAttribution.Suspect> suspects = CrashAttribution.suspects(THROUGH_FIND);

		// Without the overlap the frame's jar is the only signal, and it names Minecraft.
		assertEquals(List.of("lithium", "vmp", "minecraft"), suspects.stream().map(CrashAttribution.Suspect::modId).toList());
		assertEquals(CrashAttribution.OVERLAP, suspects.get(0).reason());
		assertEquals(CrashAttribution.OVERLAP, suspects.get(1).reason());
		assertEquals("Very Many Players", suspects.get(0).collision().other());
		assertEquals("ClassInstanceMultiMap.find", suspects.get(0).collision().method());
		String en = CrashAttribution.render(false, "crash.txt", suspects);
		assertTrue(en.contains("Lithium and Very Many Players both change ClassInstanceMultiMap.find"), en);
		assertTrue(en.contains("its mixin and one from Lithium both change ClassInstanceMultiMap.find"), en);
		String zh = CrashAttribution.render(true, "crash.txt", suspects);
		assertTrue(zh.contains("Lithium 和 Very Many Players 都改了 ClassInstanceMultiMap.find"), zh);
	}

	@Test
	void aNoteOrAnotherMethodNamesNobody() {
		ModCatalog.publish(List.of(mod("lithium", "Lithium", "lithium.jar"), mod("vmp", "Very Many Players", "vmp.jar")));
		// A redirect beside a WrapOperation is how MixinExtras is meant to be used: not something to accuse.
		MixinOverlapLint.publish(List.of(overlap(MixinOverlapLint.Rule.R4, "lithium", "vmp")));
		assertEquals(List.of(), CrashAttribution.suspects(THROUGH_FIND));

		MixinOverlapLint.publish(List.of(overlap(MixinOverlapLint.Rule.R1, "lithium", "vmp")));
		assertEquals(List.of(), CrashAttribution.suspects(THROUGH_FIND.replace("ClassInstanceMultiMap.find(",
				"ClassInstanceMultiMap.getAllInstances(")));
	}

	@Test
	void bothRenderingsSayWhatToDoAndThatItIsAGuess() {
		List<CrashAttribution.Suspect> one =
				List.of(new CrashAttribution.Suspect("sodium", "Sodium", "0.9.0", "its mixin was running", 2));

		String en = CrashAttribution.render(false, "crash-2026-09-20_17.17.35-client.txt", one);
		assertTrue(en.contains("Sodium 0.9.0"), en);
		assertTrue(en.contains("mods folder"), "a player needs to be told what to do next: " + en);
		assertTrue(en.contains("This is a guess"), "it must not claim more than it knows: " + en);
		assertTrue(en.contains("crash-2026-09-20_17.17.35-client.txt"), en);

		String zh = CrashAttribution.render(true, "crash-2026-09-20_17.17.35-client.txt", one);
		assertTrue(zh.contains("Sodium 0.9.0"), zh);
