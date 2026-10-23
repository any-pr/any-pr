package me.pepperbell.continuity.client.mixin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Shaped like Continuity's SpriteSourceListMixin, written against vanilla: a constructor hook that adds a source, and a
 * hook on list(ResourceManager) just before its ImmutableList.builder() call that captures the loader map (the first
 * local after the arguments) and adds an emissive loader for every sprite the pack has an "_e" texture for.
 */
@Mixin(SpriteSourceList.class)
abstract class SpriteSourceListMixin {
	@ModifyVariable(method = "<init>(Ljava/util/List;)V", at = @At(value = "LOAD", ordinal = 0), argsOnly = true, ordinal = 0)
	private List<String> continuity$modifySources(List<String> sources) {
		List<String> withOverlay = new ArrayList<>(sources);
		withOverlay.add("ctm_overlay");
		return withOverlay;
	}

	@Inject(method = "list(Lnet/minecraft/server/packs/resources/ResourceManager;)Ljava/util/List;",
			at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;builder()Lcom/google/common/collect/ImmutableList$Builder;", remap = false),
			locals = LocalCapture.CAPTURE_FAILHARD)
	private void continuity$afterLoadSources(ResourceManager resourceManager, CallbackInfoReturnable<List<String>> cir,
			Map<String, String> loaders) {
		Map<String, String> emissive = new HashMap<>();
		for (String sprite : loaders.keySet()) {
			if (resourceManager.has(sprite + "_e")) emissive.put(sprite + "_e", "emissive:" + sprite + "_e");
		}
		loaders.putAll(emissive);
	}
}
