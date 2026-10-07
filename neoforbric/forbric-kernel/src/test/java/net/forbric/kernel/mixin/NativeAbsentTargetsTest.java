/* Copyright 2026 The Forbric Project. Licensed under the Apache License, Version 2.0. */
package net.forbric.kernel.mixin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.CompatibilityFindings;
import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.ModPresence;
import net.forbric.api.UnifiedDependency;

/**
 * An injector whose target the owning mod's own platform lacks too is dropped the way native Mixin drops it — the
 * injector, not the mixin — and is no loss of the merge's; one whose target that platform HAS stays the loss it was.
 *
 * <p>Not Enough Crashes' {@code MixinTileEntity} in miniature: a field of its own, and one {@code @Inject} into
 * {@code BlockEntity.populateCrashReport}, a name 26.2 does not have (it has {@code fillCrashReportCategory}), in a
 * Fabric mod's {@code required: true} config without {@code injectors.defaultRequire}. Natively that mixin applies,
 * its field and all, and the injector is skipped without a word; here it was left out as a CONFIRMED required loss
 * and the STRICT policy stopped the server. Classes are synthesized with ASM, and the tables are the shipped one or a
 * test's own, so this runs anywhere; NativeOnlyMethodsCensusTest asks the same questions of the real jars.
 *
 * <p>Each case that keeps the mixin has its RED control beside it: the same inputs with
 * {@code -Dforbric.mixinFit.nativeAbsent=off}, through the adapter's entry without the raw view, without an owner,
 * without the owner's manifest, for a mod whose range excludes the game the table describes, with a selector Mixin
 * does not read as a plain name, or on a base the table was not derived from, reproduce the old verdict — the mixin
 * left out, and a CONFIRMED required finding.
 */
@ResourceLock("ModCatalog")
@ResourceLock("ModPresence")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class NativeAbsentTargetsTest {
	private static final String PKG = "net/example/necmixin";
	private static final String BLOCK_ENTITY = "net/minecraft/world/level/block/entity/BlockEntity";
	private static final String CONFIG = "nec.mixins.json";
	private static final String LEFT_OUT = "mixin:" + CONFIG + ":net.example.necmixin.TileEntityMixin";
	private static final String CATEGORY = "(Lnet/minecraft/CrashReportCategory;)V";

	@BeforeEach @AfterEach void clean() {
		CompatibilityFindings.reset();
		MixinConfigOwners.reset();
		System.clearProperty(NativeAbsentTargets.PROPERTY);
		System.clearProperty(NativeAbsentTargets.BASE_PROPERTY);
		System.clearProperty("mixin.debug.countInjections");
		System.clearProperty("mixin.debug");
		ModPresence.publishFabric(List.of());
		ModPresence.publishForgeFamily(List.of());
	}

	// --- the adapter: kept like native, or left out as before ------------------------------------------------------

	@Test void aTargetVanillaLacksTooKeepsAFabricModsMixinAndRecordsNothing() {
		owned(Ecosystem.FABRIC);
		Map<String, byte[]> classes = necShape(null, false);
		assertEquals(List.of(), judge(json(""), classes, classes), "Mixin gets the whole mixin, as native does");
		assertEquals(List.of(), CompatibilityFindings.all(), "nothing the merge lost, so nothing to report");
	}

	/** RED control: the switch restores the verdict that stopped the server. */
	@Test void switchedOffTheSameMixinIsLeftOutAsARequiredLoss() {
		owned(Ecosystem.FABRIC);
		System.setProperty(NativeAbsentTargets.PROPERTY, "off");
		Map<String, byte[]> classes = necShape(null, false);
		assertEquals(List.of("TileEntityMixin"), judge(json(""), classes, classes));
		assertTrue(confirmedRequired(), "the old CONFIRMED required finding: " + CompatibilityFindings.all());
	}

	/** RED control: the entry without the raw view (every caller before this) asks nothing, as before. */
	@Test void withoutTheRawViewNothingIsAskedOfTheNativeGame() {
		owned(Ecosystem.FABRIC);
		Map<String, byte[]> classes = necShape(null, false);
		assertEquals(List.of("TileEntityMixin"), KernelGuestMixinAdapter.unfitMixins(CONFIG, json(""), classes::get, null));
		assertTrue(confirmedRequired(), CompatibilityFindings.all().toString());
	}

	/** A config no single mod claims has no native game to ask: every miss is the merge's, as before. */
	@Test void aConfigNoModOwnsIsNotAsked() {
		Map<String, byte[]> classes = necShape(null, false);
		assertEquals(List.of("TileEntityMixin"), judge(json(""), classes, classes));
		assertTrue(confirmedRequired(), CompatibilityFindings.all().toString());
	}

	/**
	 * The platform is the owning mod's. With the shipped table, MinecraftForge's and NeoForge's games lack
	 * populateCrashReport as vanilla does, so their mods' injector is dropped natively too; what differs between them is
	 * what each game declares, which aRowOrTheRawClassMakesAMethodThePlatforms asks of a table here and
	 * NativeOnlyMethodsCensusTest.aMethodTheModsOwnPlatformHasAndTheMergeLostStaysAConfirmedLoss of the real one.
	 */
	@Test void everyPlatformIsAskedAboutItsOwnGame() {
		for (Ecosystem platform : Ecosystem.values()) {
			MixinConfigOwners.reset();
			CompatibilityFindings.reset();
			owned(platform);
			Map<String, byte[]> classes = necShape(null, false);
			assertEquals(List.of(), judge(json(""), classes, classes), platform.displayName());
			assertEquals(List.of(), CompatibilityFindings.all(), platform.displayName());
		}
	}

	/** Natively an injector that must inject throws "Critical injection failure"; it is a loss on both, as before. */
	@Test void aRequiredInjectionIsStillALossWhenVanillaLacksTheTargetToo() {
		owned(Ecosystem.FABRIC);
		Map<String, byte[]> classes = necShape(null, false);
		assertEquals(List.of("TileEntityMixin"), judge(json(",\"injectors\":{\"defaultRequire\":1}"), classes, classes),
				"defaultRequire 1 makes the injector mandatory natively too");
		CompatibilityFindings.reset();
		classes = necShape(1, false);
		assertEquals(List.of("TileEntityMixin"), judge(json(""), classes, classes), "so does the injector's own require=1");
		CompatibilityFindings.reset();
		classes = necShape(0, false);
		assertEquals(List.of(), judge(json(",\"injectors\":{\"defaultRequire\":1}"), classes, classes),
				"…and require=0 on the injector wins over the config's default, as InjectionInfo reads it");
	}

	/** A @Group needs one injection between its members, so a grouped injector is never silently empty. */
	@Test void aGroupedInjectorIsNeverDroppedSilently() {
		owned(Ecosystem.FABRIC);
		Map<String, byte[]> classes = necShape(null, true);
		assertEquals(List.of("TileEntityMixin"), judge(json(""), classes, classes));
	}

	/** Under mixin.debug.countInjections native Mixin fails on an empty injector whatever it requires. */
	@Test void countingInjectionsMakesTheEmptyInjectorALossAgain() {
		owned(Ecosystem.FABRIC);
		Map<String, byte[]> classes = necShape(null, false);
		System.setProperty("mixin.debug", "true");
		assertEquals(List.of("TileEntityMixin"), judge(json(""), classes, classes));
	}

	/**
	 * The raw class still declares it, only the chain took it away: whether vanilla had it is unknowable here, so it
	 * stays the merge's miss.
	 */
	@Test void aMethodOnlyTheTransformChainRemovedStaysAMiss() {
		owned(Ecosystem.FABRIC);
		Map<String, byte[]> chained = necShape(null, false);
		Map<String, byte[]> raw = new HashMap<>(chained);
		raw.put(BLOCK_ENTITY + ".class", blockEntity("fillCrashReportCategory", "populateCrashReport"));
		assertEquals(List.of("TileEntityMixin"), judge(json(""), chained, raw));
	}

	// --- whose game: the version the mod asks for ----------------------------------------------------------------------

	/** The game each platform's rows describe, as the shipped table records it; the cases below stand on it. */
	@Test void theShippedTableNamesTheGameEachPlatformsRowsDescribe() {
		NativeAbsentTargets.Table table = NativeAbsentTargets.shipped();
		assertEquals(Map.of("minecraft", "26.2"), table.of(Ecosystem.FABRIC).versions());
		assertEquals(Map.of("minecraft", "26.2", "neoforge", "26.2.0.88"), table.of(Ecosystem.NEOFORGE).versions());
	}

	/**
	 * A mod whose mandatory range excludes the game the rows describe would not run on it natively — its own loader
	 * refuses it — and the newer game it was built for may declare the method: not answered, the miss is the merge's.
	 * A range that admits the game, an optional requirement, and a requirement on something other than the game change
	 * nothing. A range nobody can read is not shown to admit the game, so it is not answered either.
	 */
	@Test void aModItsOwnLoaderWouldNotRunOnTheTablesGameIsNotAnswered() {
		record Case(Ecosystem platform, UnifiedDependency requires, boolean kept) {
		}
		List<Case> cases = List.of(
				new Case(Ecosystem.FABRIC, required("minecraft", "26.2"), true),
				new Case(Ecosystem.FABRIC, required("minecraft", "~26.2"), true),
				new Case(Ecosystem.FABRIC, required("minecraft", ">=26.2-alpha.1 <26.3"), true),
				new Case(Ecosystem.FABRIC, required("fabricloader", ">=0.99"), true),
				new Case(Ecosystem.FABRIC, new UnifiedDependency("minecraft", ">=26.3", false), true),
				new Case(Ecosystem.FABRIC, required("minecraft", ">=26.3"), false),
				new Case(Ecosystem.FABRIC, required("minecraft", "26.1.x"), false),
				new Case(Ecosystem.FABRIC, required("minecraft", "?!"), false),
				new Case(Ecosystem.NEOFORGE, required("forge", ">=65"), true),   // not a game the table names: an ordinary dependency
				new Case(Ecosystem.NEOFORGE, required("minecraft", ">=26.2 <26.3"), true),
				new Case(Ecosystem.NEOFORGE, required("neoforge", ">=99"), false),
				new Case(Ecosystem.NEOFORGE, required("forge", ">=65.1"), true),
				new Case(Ecosystem.NEOFORGE, required("minecraft", "=26.3"), false),
				new Case(Ecosystem.NEOFORGE, required("neoforge", ">=26.2.0.80"), true),
				new Case(Ecosystem.NEOFORGE, required("NeoForge", ">=26.2.0.90"), false),
				new Case(Ecosystem.NEOFORGE, required("minecraft", ">=26.3"), false));
		List<String> wrong = new java.util.ArrayList<>();
		for (Case one : cases) {
			clean();
			owned(one.platform(), one.requires());
			Map<String, byte[]> classes = necShape(null, false);
			List<String> left = judge(json(""), classes, classes);
			boolean kept = left.isEmpty() && CompatibilityFindings.all().isEmpty();
			boolean leftOut = left.equals(List.of("TileEntityMixin")) && confirmedRequired();
			if (one.kept() ? !kept : !leftOut) wrong.add(one + " left " + left + " " + CompatibilityFindings.all());
		}
		assertEquals(List.of(), wrong, "cases judged against the wrong game");
	}

	/**
	 * RED control: without the owning mod's manifest — none published, one of the other platform under the same id, or
	 * a mod that only answers to the id as an alias — nothing shows the mod is native to the table's game.
	 */
	@Test void aModWhoseManifestTheKernelCannotSeeIsNotAnswered() {
		MixinConfigOwners.publish(List.of(new MixinConfigOwners.Owned(CONFIG, "nec", Ecosystem.FABRIC)));
		Map<String, byte[]> classes = necShape(null, false);
		assertEquals(List.of("TileEntityMixin"), judge(json(""), classes, classes), "no manifest published");
		assertTrue(confirmedRequired(), CompatibilityFindings.all().toString());

		CompatibilityFindings.reset();
		ModPresence.publishForgeFamily(List.of(mod(Ecosystem.NEOFORGE, "nec")));
		assertEquals(List.of("TileEntityMixin"), judge(json(""), classes, classes), "the NeoForge mod of that id is not it");
		assertTrue(confirmedRequired(), CompatibilityFindings.all().toString());

		CompatibilityFindings.reset();
		ModPresence.publishForgeFamily(List.of());
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "nec-fabric").withAliases(List.of("nec"))));
		assertEquals(List.of("TileEntityMixin"), judge(json(""), classes, classes), "a mod answering to the id as an alias");
		assertTrue(confirmedRequired(), CompatibilityFindings.all().toString());

		CompatibilityFindings.reset();
		ModPresence.publishFabric(List.of(mod(Ecosystem.FABRIC, "nec")));
		assertEquals(List.of(), judge(json(""), classes, classes), "the mod itself, published as discovery publishes it");
	}

	// --- Minecraft's own libraries -------------------------------------------------------------------------------------

	/**
	 * A class served by one of the library jars the table lists is answered from its own bytes; one served by any other
	 * jar is not, and the warning calls that jar neither a base nor a library — it may be either.
	 */
	@Test void aMinecraftLibraryTheTableListsIsAnsweredForAsItIs() {
		owned(Ecosystem.FABRIC);
		String dispatcher = "com/mojang/brigadier/CommandDispatcher";
		Map<String, byte[]> classes = necShape(null, false, "forbricNoSuchMethod", dispatcher);
		classes.put(dispatcher + ".class", targetClass(dispatcher, false, "execute"));
		String listed = NativeAbsentTargets.shipped().libraries().iterator().next();
		assertEquals(List.of(), KernelGuestMixinAdapter.unfitMixins(CONFIG, json(""), classes::get, null, classes::get,
				owner -> listed), "a listed library jar is what every platform loads");
		assertEquals(List.of(), CompatibilityFindings.all());

		assertEquals(List.of("TileEntityMixin"), KernelGuestMixinAdapter.unfitMixins(CONFIG, json(""), classes::get, null,
				classes::get, owner -> "an older brigadier"), "a library jar the table does not list");
		assertTrue(confirmedRequired(), CompatibilityFindings.all().toString());

		String warning = NativeAbsentTargets.mismatch(dispatcher, "an older brigadier", "e49c");
		assertTrue(warning.contains(dispatcher + " is served by a jar with members an older brigadier, which is neither the "
				+ "merged base"), warning);
		assertTrue(warning.contains("nor one of the Minecraft library jars it lists"), warning);
		assertFalse(warning.contains("a base with members"), warning);
		assertTrue(NativeAbsentTargets.mismatch(dispatcher, null, null).contains("served by no jar the kernel can read"));
	}

	@Test void theTableRecordsEachPlatformsGameAndTheLibraryJars() {
		NativeAbsentTargets.Table table = NativeAbsentTargets.Table.parse(List.of("base b", "library l1 com.mojang:brigadier:1.3.10",
				"library l2 com.mojang:datafixerupper:10.0.21", "platform fabric minecraft=26.2",
				"platform neoforge minecraft=26.2 neoforge=26.2.0.88 malformed =x y=", "platform nobody minecraft=1"));
		assertEquals(java.util.Set.of("l1", "l2"), table.libraries());
		assertEquals(Map.of("minecraft", "26.2"), table.of(Ecosystem.FABRIC).versions());
		assertEquals(Map.of("minecraft", "26.2", "neoforge", "26.2.0.88"), table.of(Ecosystem.NEOFORGE).versions());

		NativeAbsentTargets.Rows rows = table.of(Ecosystem.NEOFORGE);
		assertNull(NativeAbsentTargets.unmetRequirement(rows, mod(Ecosystem.NEOFORGE, "m", required("neoforge", ">=26.2.0.88"))));
		assertEquals("neoforge >=26.2.0.89",
				NativeAbsentTargets.unmetRequirement(rows, mod(Ecosystem.NEOFORGE, "m", required("neoforge", ">=26.2.0.89"))));
		assertNull(NativeAbsentTargets.unmetRequirement(rows, mod(Ecosystem.NEOFORGE, "m", required("forge", ">=99"))),
				"a requirement on something the line does not name is not the game's");
	}

	// --- which selectors are a plain name lookup -----------------------------------------------------------------------

	/**
	 * Only what Mixin reads as a plain name — optionally a descriptor, optionally the target as owner — finds nothing
	 * silently. A dynamic selector resolves somewhere else (MixinSquared's {@code @MixinSquared:Handler} to another
	 * mixin's merged handler), a quantifier's minimum throws whatever {@code require} says, and a dotted name is an
	 * owner and a name: none of those is answered, so a real loss written that way stays one.
	 */
	@Test void onlyAPlainNameIsAnsweredFor() {
		assertArrayEquals(new String[] {"populateCrashReport", null}, NativeAbsentTargets.plainSelector("populateCrashReport", BLOCK_ENTITY));
		assertArrayEquals(new String[] {"populateCrashReport", CATEGORY},
				NativeAbsentTargets.plainSelector("populateCrashReport" + CATEGORY, BLOCK_ENTITY));
		assertArrayEquals(new String[] {"populateCrashReport", null},
				NativeAbsentTargets.plainSelector("L" + BLOCK_ENTITY + ";populateCrashReport", BLOCK_ENTITY), "its own owner");
		assertArrayEquals(new String[] {"<init>", "()V"}, NativeAbsentTargets.plainSelector("<init>()V", BLOCK_ENTITY));
		assertArrayEquals(new String[] {"lambda$tick$0", null}, NativeAbsentTargets.plainSelector("lambda$tick$0", BLOCK_ENTITY));

		for (String other : List.of("@MixinSquared:Handler", "@Desc(foo)", "populateCrashReport+", "populateCrashReport{1,}",
