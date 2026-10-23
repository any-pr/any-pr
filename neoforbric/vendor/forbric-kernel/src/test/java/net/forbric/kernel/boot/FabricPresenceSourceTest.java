package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.forbric.api.DiscoveredMod;
import net.forbric.api.Ecosystem;
import net.forbric.api.ModPresence;
import net.forbric.kernel.fabric.KernelModContainer;
import net.forbric.kernel.fabric.KernelModMetadata;

class FabricPresenceSourceTest {

	@TempDir Path dir;
	@AfterEach void reset() { ModPresence.publishForgeFamily(List.of()); }

	@Test void aSingleUniversalJarCanSupplyItsDefaultConfigThroughTheFabricAlias() throws Exception {
		Path jar = dir.resolve("lambdynamiclights.jar");
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
			zip.putNextEntry(new ZipEntry("lambdynlights.toml"));
			zip.write("mode = \"fancy\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		publish(jar);
		var alias = new DuplicateModArbiter.Alias("lambdynlights", Ecosystem.FABRIC, "4.12.4");
		var decision = DuplicateModArbiter.arbitrate(List.of(new DuplicateModArbiter.Claim(jar,
				Ecosystem.NEOFORGE, List.of("lambdynlights"))), List.of(alias));
		assertTrue(decision.ownerByModId().isEmpty(), "one jar needs no cross-jar arbitration");
		var container = KernelModContainer.presence(KernelModMetadata.builtin("lambdynlights", "4.12.4", "LambDynamicLights"),
				KernelFabricEcosystem.presenceSource("lambdynlights", decision));
		assertEquals("mode = \"fancy\"\n", Files.readString(container.findPath("lambdynlights.toml").orElseThrow()));
		assertNull(container.getJar(), "the alias must not load code or resources a second time");
	}

	@Test void aCrossJarWinnerTakesPrecedence() throws Exception {
		Path original = Files.createFile(dir.resolve("original.jar"));
		Path winner = Files.createFile(dir.resolve("winner.jar"));
		publish(original);
		var decision = new DuplicateModArbiter.Decision(Set.of(original), Map.of("lambdynlights", winner), List.of());
		assertEquals(winner, KernelFabricEcosystem.presenceSource("lambdynlights", decision));
	}

	@Test void absentOrUnreadableSourcesStayAbsent() {
		publish(dir.resolve("missing.jar"));
		assertNull(KernelFabricEcosystem.presenceSource("lambdynlights", DuplicateModArbiter.Decision.none()));
		assertNull(KernelFabricEcosystem.presenceSource("unknown", DuplicateModArbiter.Decision.none()));
	}

	private static void publish(Path source) {
		ModPresence.publishForgeFamily(List.of(new DiscoveredMod(Ecosystem.NEOFORGE, "lambdynlights", "4.12.4",
				"LambDynamicLights", List.of(), List.of(), null, source.toString())));
	}
}
