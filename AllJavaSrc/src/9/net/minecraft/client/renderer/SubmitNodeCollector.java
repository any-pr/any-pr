package net.minecraft.client.renderer;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jspecify.annotations.Nullable;

public interface SubmitNodeCollector extends OrderedSubmitNodeCollector {
   OrderedSubmitNodeCollector order(int order);

   interface CustomGeometryRenderer {
      void render(Pose pose, VertexConsumer buffer);
   }

   interface ParticleGroupRenderer {
      QuadParticleRenderState.@Nullable PreparedBuffers prepare(ParticleFeatureRenderer.ParticleBufferCache buffer);

      void render(
         QuadParticleRenderState.PreparedBuffers buffers,
         ParticleFeatureRenderer.ParticleBufferCache bufferCache,
         RenderPass renderPass,
         TextureManager textureManager,
         boolean isTranslucent
      );
   }
}
