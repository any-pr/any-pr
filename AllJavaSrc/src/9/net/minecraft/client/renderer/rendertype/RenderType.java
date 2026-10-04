package net.minecraft.client.renderer.rendertype;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class RenderType {
   private static final int MEGABYTE = 1048576;
   public static final int BIG_BUFFER_SIZE = 4194304;
   public static final int SMALL_BUFFER_SIZE = 786432;
   public static final int TRANSIENT_BUFFER_SIZE = 1536;
   private final RenderSetup state;
   private final Optional<RenderType> outline;
   protected final String name;

   private RenderType(final String name, final RenderSetup state) {
      this.name = name;
      this.state = state;
      this.outline = state.outlineProperty == RenderSetup.OutlineProperty.AFFECTS_OUTLINE
         ? state.textures.values().stream().findFirst().map(texture -> RenderTypes.OUTLINE.apply(texture.location(), state.pipeline.isCull()))
         : Optional.empty();
   }

   static RenderType create(final String name, final RenderSetup state) {
      return new RenderType(name, state);
   }

   @Override
   public String toString() {
      return "RenderType[" + this.name + ":" + this.state + "]";
   }

   public void draw(final MeshData mesh) {
      Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
      Consumer<Matrix4fStack> modelViewModifier = this.state.layeringTransform.getModifier();
      if (modelViewModifier != null) {
         modelViewStack.pushMatrix();
         modelViewModifier.accept(modelViewStack);
      }

      GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
         .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), this.state.textureTransform.getMatrix());
      Map<String, RenderSetup.TextureAndSampler> textures = this.state.getTextures();
      MeshData var6 = mesh;

      try {
         GpuBuffer vertices = this.state.pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
         GpuBuffer indices;
         IndexType indexType;
         if (mesh.indexBuffer() == null) {
            AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
            indices = autoIndices.getBuffer(mesh.drawState().indexCount());
            indexType = autoIndices.type();
         } else {
            indices = this.state.pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
            indexType = mesh.drawState().indexType();
         }

         RenderTarget renderTarget = this.state.outputTarget.getRenderTarget();
         GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
            ? RenderSystem.outputColorTextureOverride
            : renderTarget.getColorTextureView();
         GpuTextureView depthTexture = renderTarget.useDepth
            ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : renderTarget.getDepthTextureView())
            : null;
         RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "Immediate draw for " + this.name, colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty());

         try {
            renderPass.setPipeline(this.state.pipeline);
            ScissorState scissorState = RenderSystem.getScissorStateForRenderTypeDraws();
            if (scissorState.enabled()) {
               renderPass.enableScissor(scissorState.x(), scissorState.y(), scissorState.width(), scissorState.height());
            }

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices);

            for (Entry<String, RenderSetup.TextureAndSampler> entry : textures.entrySet()) {
               renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
            }

            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
         } catch (Throwable var19) {
            if (renderPass != null) {
               try {
                  renderPass.close();
               } catch (Throwable var18) {
                  var19.addSuppressed(var18);
               }
            }

            throw var19;
         }

         if (renderPass != null) {
            renderPass.close();
         }
      } catch (Throwable var20) {
         if (var6 != null) {
            try {
               var6.close();
            } catch (Throwable var17) {
               var20.addSuppressed(var17);
            }
         }

         throw var20;
      }

      if (var6 != null) {
         var6.close();
      }

      if (modelViewModifier != null) {
         modelViewStack.popMatrix();
      }
   }

   public int bufferSize() {
      return this.state.bufferSize;
   }

   public VertexFormat format() {
      return this.state.pipeline.getVertexFormat();
   }

   public Mode mode() {
      return this.state.pipeline.getVertexFormatMode();
   }

   public Optional<RenderType> outline() {
      return this.outline;
   }

   public boolean isOutline() {
      return this.state.outlineProperty == RenderSetup.OutlineProperty.IS_OUTLINE;
   }

   public RenderPipeline pipeline() {
      return this.state.pipeline;
   }

   public boolean affectsCrumbling() {
      return this.state.affectsCrumbling;
   }

   public boolean canConsolidateConsecutiveGeometry() {
      return !this.mode().connectedPrimitives;
   }

   public boolean sortOnUpload() {
      return this.state.sortOnUpload;
   }
}
