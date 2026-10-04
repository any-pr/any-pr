package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass.Draw;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;

public record ChunkSectionsToRender(
   GpuTextureView textureView,
   EnumMap<ChunkSectionLayer, List<Draw<GpuBufferSlice[]>>> drawsPerLayer,
   int maxIndicesRequired,
   GpuBufferSlice[] chunkSectionInfos
) {
   public void renderGroup(final ChunkSectionLayerGroup group, final GpuSampler sampler) {
      AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(Mode.QUADS);
      GpuBuffer defaultIndexBuffer = this.maxIndicesRequired == 0 ? null : autoIndices.getBuffer(this.maxIndicesRequired);
      IndexType defaultIndexType = this.maxIndicesRequired == 0 ? null : autoIndices.type();
      ChunkSectionLayer[] layers = group.layers();
      Minecraft minecraft = Minecraft.getInstance();
      boolean wireframe = SharedConstants.DEBUG_HOTKEYS && minecraft.wireframe;
      RenderTarget renderTarget = group.outputTarget();
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(
            () -> "Section layers for " + group.label(),
            renderTarget.getColorTextureView(),
            OptionalInt.empty(),
            renderTarget.getDepthTextureView(),
            OptionalDouble.empty()
         );

      try {
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.bindTexture(
            "Sampler2", minecraft.gameRenderer.lightTexture().getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
         );

         for (ChunkSectionLayer layer : layers) {
            List<Draw<GpuBufferSlice[]>> draws = this.drawsPerLayer.get(layer);
            if (!draws.isEmpty()) {
               if (layer == ChunkSectionLayer.TRANSLUCENT) {
                  draws = draws.reversed();
               }

               renderPass.setPipeline(wireframe ? RenderPipelines.WIREFRAME : layer.pipeline());
               renderPass.bindTexture("Sampler0", this.textureView, sampler);
               renderPass.drawMultipleIndexed(draws, defaultIndexBuffer, defaultIndexType, List.of("ChunkSection"), this.chunkSectionInfos);
            }
         }
      } catch (Throwable var17) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var16) {
               var17.addSuppressed(var16);
            }
         }

         throw var17;
      }

      if (renderPass != null) {
         renderPass.close();
      }
   }
}
