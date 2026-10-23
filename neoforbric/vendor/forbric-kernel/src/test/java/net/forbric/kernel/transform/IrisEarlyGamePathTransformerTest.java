package net.forbric.kernel.transform;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;

class IrisEarlyGamePathTransformerTest {
	private static final String NAME = "net.irisshaders.iris.mixin.IrisMixinPlugin";
	private byte[] plugin() throws Exception {
		Path path = Path.of("build/compat-inputs/player-loading/mods/iris-neoforge-1.11.4+mc26.2.jar");
		TestFixtures.require(Fixture.THIRD_PARTY, Files.isRegularFile(path), "actual Iris plugin required");
		try (ZipFile zip = new ZipFile(path.toFile())) { return zip.getInputStream(zip.getEntry(NAME.replace('.','/') + ".class")).readAllBytes(); }
	}
	@Test void theRealPluginNoLongerConstructsItsGameProviderToReadOptions() throws Exception {
		var transformer = new IrisEarlyGamePathTransformer();
		byte[] original = plugin(), result = transformer.transform(NAME,original,null);
		assertNotSame(original,result);
		ClassNode node = new ClassNode(); new ClassReader(result).accept(node,0);
		int calls=0;
		for (MethodNode method : node.methods) {
			new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
			for (AbstractInsnNode insn:method.instructions) if(insn instanceof MethodInsnNode call) {
				assertNotEquals("net/irisshaders/iris/platform/IrisPlatformHelpers",call.owner);
				if(call.owner.equals("net/fabricmc/loader/api/FabricLoader"))calls++;
			}
		}
		assertEquals(2,calls);
		assertSame(result,transformer.transform(NAME,result,null));
		assertSame(original,transformer.transform("other.Plugin",original,null));
	}
}
