package net.forbric.kernel.mixin;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.zip.ZipFile;
import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

class FabricRegistryInitializationMixinAdapterTest {
	private ClassNode mixin(String suffix) throws Exception {
		Path path=Path.of("build/compat-inputs/player-loading/api/fabric-registry-sync-v0-7.1.1+c7bd5b8e9e.jar");TestFixtures.require(Fixture.THIRD_PARTY,Files.isRegularFile(path),path+" absent");
		try(ZipFile zip=new ZipFile(path.toFile())){return MixinFit.parse(zip.getInputStream(zip.getEntry("net/fabricmc/fabric/mixin/registry/sync/"+suffix+".class")).readAllBytes());}
	}
	@Test void bootstrapKeepsEveryNativeTrackerButDoesNotRedirectTheFreeze() throws Exception {
		ClassNode mixin=mixin("BootstrapMixin");
		String before=MixinInstructionFingerprint.hash(StagedFabricMixinFixture.method(mixin,"afterInitialize"));
		assertEquals(1,FabricRegistryInitializationMixinAdapter.adapt(mixin));
		assertEquals(before,MixinInstructionFingerprint.hash(StagedFabricMixinFixture.method(mixin,"afterInitialize")));
		assertEquals("TAIL",MixinFit.value(StagedFabricMixinFixture.at(mixin,"afterInitialize"),"value"));
		assertNull(MixinFit.injectorOf(StagedFabricMixinFixture.method(mixin,"delayRegistryFreeze")));
		assertEquals(0,FabricRegistryInitializationMixinAdapter.adapt(mixin));
	}
	@Test void clientAndServerRetainPostFreezeWorkAndClientUnmap() throws Exception {
		for(String name:new String[]{"MainMixin","client/MinecraftMixin"}){
			ClassNode mixin=mixin(name);assertEquals(1,FabricRegistryInitializationMixinAdapter.adapt(mixin));
			MethodNode after=StagedFabricMixinFixture.method(mixin,"afterModInit");boolean tracker=false;
			for(var i:after.instructions)if(i instanceof MethodInsnNode c){assertFalse(c.name.equals("bootStrap"));tracker|=c.name.equals("postFreeze");}
			assertTrue(tracker);new Analyzer<>(new BasicVerifier()).analyze(mixin.name,after);
			if(name.startsWith("client"))assertNotNull(MixinFit.injectorOf(StagedFabricMixinFixture.method(mixin,"disconnectAfter")));
			assertEquals(0,FabricRegistryInitializationMixinAdapter.adapt(mixin));
		}
	}
}
