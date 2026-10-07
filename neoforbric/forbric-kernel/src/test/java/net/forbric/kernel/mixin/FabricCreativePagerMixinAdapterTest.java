package net.forbric.kernel.mixin;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.zip.ZipFile;
import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.*;

class FabricCreativePagerMixinAdapterTest {
	@Test void keyboardKeepsItsExactBodyAndTheSecondPagerStateIsRemoved() throws Exception{
		Path path=Path.of("build/compat-inputs/player-loading/api/fabric-creative-tab-api-v1-5.0.15+6468574f9e.jar");TestFixtures.require(Fixture.THIRD_PARTY,Files.isRegularFile(path),path+" absent");
		ClassNode mixin;try(ZipFile zip=new ZipFile(path.toFile())){mixin=MixinFit.parse(zip.getInputStream(zip.getEntry("net/fabricmc/fabric/mixin/creativetab/client/CreativeModeInventoryScreenMixin.class")).readAllBytes());}
		String body=MixinInstructionFingerprint.hash(StagedFabricMixinFixture.method(mixin,"keyPressed"));
		assertEquals(1,FabricCreativePagerMixinAdapter.adapt(mixin));assertEquals(2,mixin.methods.size());assertTrue(mixin.fields.isEmpty());
		assertEquals(body,MixinInstructionFingerprint.hash(StagedFabricMixinFixture.method(mixin,"keyPressed")));
		assertNotNull(MixinFit.injectorOf(StagedFabricMixinFixture.method(mixin,"keyPressed")));
		assertEquals(0,FabricCreativePagerMixinAdapter.adapt(mixin));
	}
}
