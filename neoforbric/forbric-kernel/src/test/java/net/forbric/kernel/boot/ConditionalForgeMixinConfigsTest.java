package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;
import net.forbric.api.*;
import net.forbric.kernel.mixin.MixinConfigOwners;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ModCatalog")
class ConditionalForgeMixinConfigsTest {
    @TempDir Path root;
    @BeforeEach @AfterEach void reset() {
        ModPresence.publishFabric(List.of()); ModPresence.publishForgeFamily(List.of()); MultiLoaderArbiter.reset();
    }
    @Test void optionalRendererIntegrationWaitsForAllItsProviders() throws Exception {
        Path jar = root.resolve("renderer.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("META-INF/neoforge.mods.toml"));
            zip.write(("modLoader=\"javafml\"\nloaderVersion=\"[4,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"renderer\"\nversion=\"1\"\n"
                    + "[[mixins]]\nconfig=\"common.json\"\n[[mixins]]\nconfig=\"optional.json\"\nrequiredMods=[\"fabric_renderer_api_v1\",\"other\"]\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String config : List.of("common.json", "optional.json")) {
                zip.putNextEntry(new ZipEntry(config)); zip.write("{}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        var configs = KernelBoot.discoverForgeMixinConfigs(List.of(jar), "test");
        assertEquals(List.of("fabric_renderer_api_v1", "other"), configs.get(1).requiredMods());
        assertEquals(List.of("common.json"), KernelForgeFamilyMixins.select(configs).stream().map(MixinConfigOwners.Owned::config).toList());
        ModPresence.publishFabric(List.of(mod("fabric-renderer-api-v1")));
        assertEquals(1, KernelForgeFamilyMixins.select(configs).size());
        ModPresence.publishFabric(List.of(mod("fabric-renderer-api-v1"), mod("other")));
        assertEquals(List.of("common.json", "optional.json"), KernelForgeFamilyMixins.select(configs).stream().map(MixinConfigOwners.Owned::config).toList());
    }
    private static DiscoveredMod mod(String id) {
        return new DiscoveredMod(Ecosystem.FABRIC, id, "1", id, List.of(), List.of(), null, "test.jar");
    }
}
