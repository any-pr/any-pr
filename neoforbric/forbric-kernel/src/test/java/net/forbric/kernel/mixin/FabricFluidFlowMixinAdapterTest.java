package net.forbric.kernel.mixin;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.nio.file.*;
import java.util.zip.ZipFile;
import net.forbric.kernel.TestFixtures;
import net.forbric.kernel.TestFixtures.Fixture;

class FabricFluidFlowMixinAdapterTest {
	private ClassNode mixin() throws Exception {
		Path path=Path.of("build/compat-inputs/player-loading/api/fabric-block-api-v1-3.1.0+53515aab9e.jar");
		TestFixtures.require(Fixture.THIRD_PARTY,Files.isRegularFile(path),"actual current Fabric Block API fixture required");
		try(ZipFile zip=new ZipFile(path.toFile())){return MixinFit.parse(zip.getInputStream(zip.getEntry("net/fabricmc/fabric/mixin/block/LiquidBlockMixin.class")).readAllBytes());}
	}
	@Test void bothLiveCarrierPathsCallTheOriginalFabricVetoAndKeepTheirNativeOperations() throws Exception {
		ClassNode mixin=mixin();
		ClassNode target=StagedFabricMixinFixture.game("net/minecraft/world/level/block/LiquidBlock",false);
		assertEquals(3,FabricFluidFlowMixinAdapter.adapt(mixin,n->target));
		assertNull(MixinFit.injectorOf(StagedFabricMixinFixture.method(mixin,"shouldSpreadLiquid$forbricOriginal")));
		for(String name:new String[]{"onPlace","neighborChanged","updateShape"}) {
			MethodNode wrapper=StagedFabricMixinFixture.method(mixin,"forbric$fluidFlow$"+name);
			new Analyzer<>(new BasicVerifier()).analyze(mixin.name,wrapper);
			int originals=0,operations=0;
			for(var insn:wrapper.instructions)if(insn instanceof MethodInsnNode call){if(call.name.equals("shouldSpreadLiquid$forbricOriginal"))originals++;if(call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation")&&call.name.equals("call"))operations++;}
			assertEquals(1,originals);assertEquals(1,operations);
			if(name.equals("updateShape")){
				var hostArgs=org.objectweb.asm.Type.getArgumentTypes(StagedFabricMixinFixture.method(target,"updateShape").desc);
				var wrapperArgs=org.objectweb.asm.Type.getArgumentTypes(wrapper.desc);
				assertArrayEquals(hostArgs,java.util.Arrays.copyOfRange(wrapperArgs,5,wrapperArgs.length));
				assertEquals("Lnet/minecraft/world/level/LevelReader;",wrapperArgs[6].getDescriptor());
			}
		}
		assertEquals(0,FabricFluidFlowMixinAdapter.adapt(mixin,n->target));
	}
	/**
	 * fabric-block-api and Create Fly both keep a {@code shouldSpreadLiquid$forbricOriginal} in LiquidBlock; only
	 * {@code @Unique} lets Mixin rename the second instead of skipping it. FluidPairMixinAdaptersWeaveTest weaves both.
	 */
	@Test void theRetainedOriginalIsUniqueSoASecondModsOriginalIsRenamedNotSkipped() {
		MethodNode original=new MethodNode(org.objectweb.asm.Opcodes.ACC_PRIVATE,"shouldSpreadLiquid","()V",null,null);
		AnnotationNode inject=new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
		AnnotationNode kept=new AnnotationNode("Lorg/spongepowered/asm/mixin/Dynamic;");
		original.visibleAnnotations=new java.util.ArrayList<>(java.util.List.of(kept,inject));
		FabricFluidFlowMixinAdapter.retainOriginal(original,inject);
		assertEquals("shouldSpreadLiquid$forbricOriginal",original.name);
		assertEquals(java.util.List.of("Lorg/spongepowered/asm/mixin/Dynamic;","Lorg/spongepowered/asm/mixin/Unique;"),
				original.visibleAnnotations.stream().map(a->a.desc).toList());
		assertNull(original.invisibleAnnotations);
	}
	@Test void changedOrVanillaCarrierIsNotRewritten() throws Exception {
		ClassNode mixin=mixin();
		ClassNode vanilla=StagedFabricMixinFixture.game("net/minecraft/world/level/block/LiquidBlock",true);
		assertEquals(0,FabricFluidFlowMixinAdapter.adapt(mixin,n->vanilla));
	}
}
