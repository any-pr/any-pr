package net.forbric.kernel.boot;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import net.forbric.api.*;
import net.forbric.kernel.discovery.ForbricModDiscoverer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("mod-presence")
class NestedPresenceAndEnumPathsTest {
    @TempDir Path tmp;
    @AfterEach void reset() {ModPresence.publishForgeFamily(List.of());ModPresence.publishFabric(List.of());MultiLoaderArbiter.reset();}
    private Path jar(Map<String,String> entries) throws Exception {
        Path path=tmp.resolve("fixture.jar");try(var zip=new ZipOutputStream(Files.newOutputStream(path))) {
            for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
        return path;
    }
    private static String manifest(String mods) {return "modLoader=\"javafml\"\nloaderVersion=\"[2,)\"\nlicense=\"test\"\n"+mods;}
    @Test void nestedOwnerMetadataPrecedesFabricAliasesAndIsIdempotent() throws Exception {
        Path path=jar(Map.of("META-INF/neoforge.mods.toml",manifest("[[mods]]\nmodId=\"spectrelib\"\nversion=\"0.22.0\"\n")));
        KernelBoot.publishNestedPresence(List.of(path));assertEquals(Ecosystem.NEOFORGE,ModPresence.metadata("spectrelib").getEcosystem());
        assertEquals(path.toString(),ModPresence.metadata("spectrelib").getSource());KernelBoot.publishNestedPresence(List.of(path));assertEquals(1,ModPresence.forgeFamilyMods().size());
    }
    @Test void followsEachModDeclaredEnumResource() throws Exception {
        Path path=jar(Map.of("META-INF/neoforge.mods.toml",manifest("[[mods]]\nmodId=\"first\"\nversion=\"1\"\nenumExtensions=\"enum/26.2.json\"\n[[mods]]\nmodId=\"second\"\nversion=\"1\"\nenumExtensions=\"enum/second.json\"\n"),"enum/26.2.json","first-payload","enum/second.json","second-payload","META-INF/enumextensions.json","wrong"));
        var declarations=NeoEnumExtensions.declarationsForJar(new ForbricModDiscoverer(),path);assertEquals(Set.of("first","second"),declarations.keySet());assertEquals("first-payload",new String(declarations.get("first"),StandardCharsets.UTF_8));assertEquals("second-payload",new String(declarations.get("second"),StandardCharsets.UTF_8));
    }
    @Test void conventionalResourceStillWorks() throws Exception {
        Path path=jar(Map.of("META-INF/neoforge.mods.toml",manifest("[[mods]]\nmodId=\"legacy\"\nversion=\"1\"\n"),"META-INF/enumextensions.json","legacy-payload"));
        assertEquals("legacy-payload",new String(NeoEnumExtensions.declarationsForJar(new ForbricModDiscoverer(),path).get("legacy"),StandardCharsets.UTF_8));
    }
}
