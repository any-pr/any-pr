package fi.dy.masa.litematica.mixin.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Schematic passes in Litematica's shape: two @Inject handlers on vanilla's main-pass lambda, selected by name and
 * descriptor, after the first and second layer-group draw, each taking that lambda's arguments and a CallbackInfo.
 * The bodies are the fixture's own.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
	@Inject(method = "lambda$addMainPass$0(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;F)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Ljava/util/List;Ljava/lang/String;)V",
					ordinal = 0, shift = At.Shift.AFTER))
	private void litematica_renderMainSection_Opaque(LevelRenderState state, ChunkSectionsToRender sections, float alpha, CallbackInfo ci) {
		((LevelRenderer) (Object) this).trace.add("schematic opaque(" + state + ", " + sections + ", " + alpha + ")");
	}

	@Inject(method = "lambda$addMainPass$0(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;F)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Ljava/util/List;Ljava/lang/String;)V",
					ordinal = 1, shift = At.Shift.AFTER))
	private void litematica_renderMainSection_Translucent(LevelRenderState state, ChunkSectionsToRender sections, float alpha, CallbackInfo ci) {
		((LevelRenderer) (Object) this).trace.add("schematic translucent(" + state + ", " + sections + ", " + alpha + ")");
	}
}
