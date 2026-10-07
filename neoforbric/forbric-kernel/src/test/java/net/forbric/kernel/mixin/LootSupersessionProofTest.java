package net.forbric.kernel.mixin;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import net.forbric.kernel.transform.LootTableEventBridgeInjector;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class LootSupersessionProofTest {
	@Test void aNameOrAnUnmodifiedLoaderCannotResolveTheMissingLootMixin() throws Exception {
		String mixin="net.fabricmc.fabric.mixin.loot.ReloadableServerRegistriesMixin";
		String target="net.minecraft.server.ReloadableServerRegistries";
		byte[] raw=StagedFabricMixinFixture.bytes(StagedFabricMixinFixture.game(target.replace('.','/'),false));
		byte[] repaired=new LootTableEventBridgeInjector().transform(target,raw,null);
		SupersededMixins.reset();
		try{
			assertNull(SupersededMixins.provedReplacement(mixin));
			SupersededMixins.observeDefinition(target,raw);assertNull(SupersededMixins.provedReplacement(mixin));
			SupersededMixins.observeDefinition(target,repaired);assertNotNull(SupersededMixins.provedReplacement(mixin));
			String before=System.setProperty(LootTableEventBridgeInjector.PROPERTY,"off");
			try{assertNull(SupersededMixins.provedReplacement(mixin));}finally{if(before==null)System.clearProperty(LootTableEventBridgeInjector.PROPERTY);else System.setProperty(LootTableEventBridgeInjector.PROPERTY,before);}
		}finally{SupersededMixins.reset();}
	}
}
