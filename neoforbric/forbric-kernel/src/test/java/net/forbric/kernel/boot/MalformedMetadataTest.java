package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import net.forbric.api.CompatibilityFinding;
import net.forbric.api.CompatibilityFindings;
import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.ModCatalog;
import net.forbric.kernel.discovery.ForbricModDiscoverer;
import net.forbric.kernel.discovery.MetadataFailures;

/**
 * One mod's unreadable metadata is that mod's problem. The case is real: EntityCount-multiloader-0.6.1-26.2.jar
 * ships {@code versionRange = "[26.2,26.23"} in its neoforge.mods.toml, and a 130-jar pack containing it died three
 * seconds into the boot with an IllegalArgumentException and no mod named (random100, 2026-09-29).
 */
class MalformedMetadataTest {
	private static final String BAD_RANGE = "[26.2,26.23";

	@TempDir Path temporary;

	@BeforeEach @AfterEach void reset() {
		MetadataFailures.reset();
		MultiLoaderArbiter.reset();
		CompatibilityFindings.reset();
	}

	@Test void aBadRangeCostsOnlyItsOwnManifestAndNamesWhoseRangeItIs() throws Exception {
		Path jar = jar("neo-only.jar", Map.of("META-INF/neoforge.mods.toml", neoToml("entitycount", BAD_RANGE)));
		List<DiscoveredMod> mods = assertDoesNotThrow(() -> new ForbricModDiscoverer().discoverJar(jar));
		assertEquals(List.of(), mods);
		assertTrue(MetadataFailures.failed(jar, Ecosystem.NEOFORGE));
		MetadataFailures.Failure failure = MetadataFailures.of(jar).get(0);
		assertEquals(List.of("entitycount"), failure.modIds());
		assertTrue(failure.message().contains("dependency neoforge of entitycount"), failure.message());
		assertTrue(failure.message().contains(BAD_RANGE), failure.message());
	}

	@Test void aUniversalJarKeepsTheManifestsThatCanBeRead() throws Exception {
		Path jar = jar("universal.jar", Map.of(
				"fabric.mod.json", fabricJson("entitycount", "example.FabricEntry"),
				"META-INF/neoforge.mods.toml", neoToml("entitycount", BAD_RANGE)));
		List<DiscoveredMod> mods = new ForbricModDiscoverer().discoverJar(jar);
		assertEquals(List.of(Ecosystem.FABRIC), mods.stream().map(DiscoveredMod::getEcosystem).toList());
		assertTrue(MetadataFailures.failed(jar, Ecosystem.NEOFORGE));
		assertFalse(MetadataFailures.failed(jar, Ecosystem.FABRIC));

		Path other = jar("broken-json.jar", Map.of(
				"fabric.mod.json", "{\"schemaVersion\":1,\"id\":\"half\",",
				"META-INF/neoforge.mods.toml", neoToml("half", "[26.2,)")));
		assertEquals(List.of(Ecosystem.NEOFORGE),
				new ForbricModDiscoverer().discoverJar(other).stream().map(DiscoveredMod::getEcosystem).toList());
		assertTrue(MetadataFailures.failed(other, Ecosystem.FABRIC));

		Path syntax = jar("broken-toml.jar", Map.of("META-INF/neoforge.mods.toml", "[[mods]\nmodId=\"x\""));
		assertEquals(List.of(), new ForbricModDiscoverer().discoverJar(syntax));
		assertEquals(List.of(), MetadataFailures.of(syntax).get(0).modIds(), "an unparsed file names no mod");
	}

	@Test void aRememberedJarReportsItsFailureAgainAfterAReset() throws Exception {
		Path jar = jar("remembered.jar", Map.of("META-INF/neoforge.mods.toml", neoToml("entitycount", BAD_RANGE)));
		ForbricModDiscoverer discoverer = new ForbricModDiscoverer();
		discoverer.discoverJar(jar);
		MetadataFailures.reset();
		assertFalse(MetadataFailures.failed(jar, Ecosystem.NEOFORGE));
		discoverer.discoverJar(jar);
		assertTrue(MetadataFailures.failed(jar, Ecosystem.NEOFORGE), "a cache hit must not hide the failure");
	}

	@Test void theOwnerIsAFamilyThatCanReadTheJar() throws Exception {
		Path broken = universal("entitycount.jar", BAD_RANGE);
		assertEquals(Ecosystem.FABRIC, MultiLoaderArbiter.ownerOf(broken),
				"NeoForge cannot read this jar; native Fabric loads it from fabric.mod.json");
		assertTrue(MultiLoaderArbiter.suppressedFor(broken, Ecosystem.NEOFORGE));
		assertFalse(MultiLoaderArbiter.suppressedFor(broken, Ecosystem.FABRIC));

		// Control: the identical jar with a valid range keeps the documented preference.
		Path fine = universal("entitycount-fixed.jar", "[26.2,26.3)");
		assertEquals(Ecosystem.NEOFORGE, MultiLoaderArbiter.ownerOf(fine));
	}

	@Test void onePackJarWithABadRangeNoLongerStopsTheOthers() throws Exception {
		Path mods = Files.createDirectories(temporary.resolve("mods"));
		Path good = jar(mods, "good-neo.jar", Map.of("META-INF/neoforge.mods.toml", neoToml("goodneo", "[26.2,)")));
		jar(mods, "bad-neo.jar", Map.of("META-INF/neoforge.mods.toml", neoToml("entitycount", BAD_RANGE)));
		jar(mods, "good-fabric.jar", Map.of("fabric.mod.json", fabricJson("goodfabric", null)));

		KernelBoot.ForgeFamilyMods found = assertDoesNotThrow(() ->
				KernelBoot.discoverForgeFamilyModJars(mods, DuplicateModArbiter.Decision.none()));
		assertEquals(List.of(good), found.jars());

		MetadataFailures.recordFindings(MultiLoaderArbiter::ownerOf, List.of());
		List<CompatibilityFinding> lost = CompatibilityFindings.confirmedRequired();
		assertEquals(List.of("entitycount:metadata:neoforge"), lost.stream().map(CompatibilityFinding::key).toList());
		assertTrue(lost.get(0).detail().contains(BAD_RANGE), lost.get(0).detail());
		assertTrue(lost.get(0).evidence().contains("jar=bad-neo.jar"), lost.get(0).evidence().toString());
	}

	@Test void anUnreadableInstalledJarIsAModThatDidNotLoadNotAProblemOfNoMod() throws Exception {
		Path mods = Files.createDirectories(temporary.resolve("mods"));
		Path good = jar(mods, "good-neo.jar", Map.of("META-INF/neoforge.mods.toml", neoToml("goodneo", "[26.2,)")));
		jar(mods, "bad-neo.jar", Map.of("META-INF/neoforge.mods.toml", neoToml("entitycount", BAD_RANGE)));
		KernelBoot.discoverForgeFamilyModJars(mods, DuplicateModArbiter.Decision.none());
		MetadataFailures.recordFindings(MultiLoaderArbiter::ownerOf, List.of());
		try {
			KernelModCatalog.publish(new ForbricModDiscoverer().discoverJar(good), mods);
			ModCatalog.Entry row = ModCatalog.all().stream().filter(e -> e.modId().equals("entitycount")).findFirst()
					.orElseThrow(() -> new AssertionError("no row for the installed jar: " + ModCatalog.all()));
			assertEquals(ModCatalog.Status.FAILED, row.status());
			assertEquals("bad-neo.jar", row.jar());
			assertTrue(row.statusDetail().contains(BAD_RANGE), row.statusDetail());
			assertEquals(List.of(), CompatibilityFindings.unattributed(), "it belongs to an installed mod");

			CompatibilityFindings.observeInitializationFailures();
			assertEquals(1, CompatibilityFindings.confirmedRequired().size(), "the row must not mint a second finding: "
					+ CompatibilityFindings.confirmedRequired());
		} finally {
			KernelModCatalog.publish(List.of());
		}
	}

	@Test void aUniversalJarThatStillLoadsIsOnlyANote() throws Exception {
		Path jar = universal("entitycount.jar", BAD_RANGE);
		MultiLoaderArbiter.ownerOf(jar);
		MetadataFailures.recordFindings(MultiLoaderArbiter::ownerOf, List.of());
		assertEquals(List.of(), CompatibilityFindings.confirmedRequired());
		List<CompatibilityFinding> notes = CompatibilityFindings.suspected();
		assertEquals(1, notes.size());
		assertTrue(notes.get(0).detail().contains("loads as FABRIC"), notes.get(0).detail());
	}

	@Test void aBrokenCarrierManifestIsForbricsProblemNotAMods() throws Exception {
		Path carrier = jar("neoforge-runtime.jar", Map.of("META-INF/neoforge.mods.toml", neoToml("neoforge", BAD_RANGE)));
		assertEquals(List.of(), KernelBoot.discoverForgeMixinConfigs(List.of(carrier), "runtime jar"));
		MetadataFailures.recordFindings(MultiLoaderArbiter::ownerOf, List.of(carrier));
		assertEquals(List.of("forbric:metadata:neoforge"),
				CompatibilityFindings.confirmedRequired().stream().map(CompatibilityFinding::key).toList());
	}

	private Path universal(String name, String range) throws Exception {
		return jar(name, Map.of(
				"fabric.mod.json", fabricJson("entitycount", "example.FabricEntry"),
				"META-INF/neoforge.mods.toml", neoToml("entitycount", range),
				"example/FabricEntry.class", initializer("example/FabricEntry"),
				"example/NeoEntry.class", neoMod("example/NeoEntry", "entitycount")));
	}

	private static String neoToml(String modId, String neoRange) {
		return "modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"" + modId
				+ "\"\nversion=\"1\"\n[[dependencies." + modId + "]]\nmodId=\"neoforge\"\ntype=\"required\"\n"
				+ "versionRange=\"" + neoRange + "\"\n";
	}

	private static String fabricJson(String modId, String entry) {
		return "{\"schemaVersion\":1,\"id\":\"" + modId + "\",\"version\":\"1\",\"entrypoints\":"
				+ (entry == null ? "{}" : "{\"main\":[\"" + entry + "\"]}") + "}";
	}

	private Path jar(String name, Map<String, Object> entries) throws Exception {
		return jar(temporary, name, entries);
	}

	private static Path jar(Path dir, String name, Map<String, Object> entries) throws Exception {
		Path path = dir.resolve(name);
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
			for (var entry : new LinkedHashMap<>(entries).entrySet()) {
				out.putNextEntry(new JarEntry(entry.getKey()));
				out.write(entry.getValue() instanceof byte[] bytes ? bytes
						: ((String) entry.getValue()).getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			}
		}
		return path;
	}

	private static byte[] initializer(String name) {
		ClassWriter out = new ClassWriter(0);
		out.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", new String[] {"net/fabricmc/api/ModInitializer"});
		constructor(out);
		var init = out.visitMethod(Opcodes.ACC_PUBLIC, "onInitialize", "()V", null, null);
		init.visitCode(); init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 1); init.visitEnd();
		out.visitEnd();
		return out.toByteArray();
	}

	private static byte[] neoMod(String name, String modId) {
		ClassWriter out = new ClassWriter(0);
		out.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
		var annotation = out.visitAnnotation("Lnet/neoforged/fml/common/Mod;", true);
		annotation.visit("value", modId);
		annotation.visitEnd();
		constructor(out);
		out.visitEnd();
		return out.toByteArray();
	}

	private static void constructor(ClassWriter out) {
		var ctor = out.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		ctor.visitCode(); ctor.visitVarInsn(Opcodes.ALOAD, 0);
		ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		ctor.visitInsn(Opcodes.RETURN); ctor.visitMaxs(1, 1); ctor.visitEnd();
	}
}
