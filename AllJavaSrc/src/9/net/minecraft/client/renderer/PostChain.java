package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.shaders.UniformType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class PostChain implements AutoCloseable {
   public static final Identifier MAIN_TARGET_ID = Identifier.withDefaultNamespace("main");
   private final List<PostPass> passes;
   private final Map<Identifier, PostChainConfig.InternalTarget> internalTargets;
   private final Set<Identifier> externalTargets;
   private final Map<Identifier, RenderTarget> persistentTargets = new HashMap<>();
   private final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer;

   private PostChain(
      final List<PostPass> passes,
      final Map<Identifier, PostChainConfig.InternalTarget> internalTargets,
      final Set<Identifier> externalTargets,
      final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer
   ) {
      this.passes = passes;
      this.internalTargets = internalTargets;
      this.externalTargets = externalTargets;
      this.projectionMatrixBuffer = projectionMatrixBuffer;
   }

   public static PostChain load(
      final PostChainConfig config,
      final TextureManager textureManager,
      final Set<Identifier> allowedExternalTargets,
      final Identifier id,
      final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer
   ) throws ShaderManager.CompilationException {
      Stream<Identifier> referencedTargets = config.passes().stream().flatMap(PostChainConfig.Pass::referencedTargets);
      Set<Identifier> referencedExternalTargets = referencedTargets.filter(targetId -> !config.internalTargets().containsKey(targetId))
         .collect(Collectors.toSet());
      Set<Identifier> invalidExternalTargets = Sets.difference(referencedExternalTargets, allowedExternalTargets);
      if (!invalidExternalTargets.isEmpty()) {
         throw new ShaderManager.CompilationException("Referenced external targets are not available in this context: " + invalidExternalTargets);
      }

      Builder<PostPass> passes = ImmutableList.builder();

      for (int i = 0; i < config.passes().size(); i++) {
         PostChainConfig.Pass pass = config.passes().get(i);
         passes.add(createPass(textureManager, pass, id.withSuffix("/" + i)));
      }

      return new PostChain(passes.build(), config.internalTargets(), referencedExternalTargets, projectionMatrixBuffer);
   }

   private static PostPass createPass(final TextureManager textureManager, final PostChainConfig.Pass config, final Identifier id) throws ShaderManager.CompilationException {
      com.mojang.blaze3d.pipeline.RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder(new Snippet[]{RenderPipelines.POST_PROCESSING_SNIPPET})
         .withFragmentShader(config.fragmentShaderId())
         .withVertexShader(config.vertexShaderId())
         .withLocation(id);

      for (PostChainConfig.Input input : config.inputs()) {
         pipelineBuilder.withSampler(input.samplerName() + "Sampler");
      }

      pipelineBuilder.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER);

      for (String uniformGroupName : config.uniforms().keySet()) {
         pipelineBuilder.withUniform(uniformGroupName, UniformType.UNIFORM_BUFFER);
      }

      RenderPipeline pipeline = pipelineBuilder.build();
      List<PostPass.Input> inputs = new ArrayList<>();

      for (PostChainConfig.Input input : config.inputs()) {
         switch (input) {
            case PostChainConfig.TextureInput(String samplerName, Identifier location, int width, int height, boolean bilinear):
               AbstractTexture texture = textureManager.getTexture(location.withPath(path -> "textures/effect/" + path + ".png"));
               inputs.add(new PostPass.TextureInput(samplerName, texture, width, height, bilinear));
               break;
            case PostChainConfig.TargetInput(String samplerName, Identifier targetId, boolean useDepthBuffer, boolean bilinear):
               inputs.add(new PostPass.TargetInput(samplerName, targetId, useDepthBuffer, bilinear));
               break;
            default:
               throw new MatchException(null, null);
         }
      }

      return new PostPass(pipeline, config.outputTarget(), config.uniforms(), inputs);
   }

   public void addToFrame(final FrameGraphBuilder frame, final int screenWidth, final int screenHeight, final PostChain.TargetBundle providedTargets) {
      GpuBufferSlice projectionBuffer = this.projectionMatrixBuffer.getBuffer(screenWidth, screenHeight);
      Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>(this.internalTargets.size() + this.externalTargets.size());

      for (Identifier id : this.externalTargets) {
         targets.put(id, providedTargets.getOrThrow(id));
      }

      for (Entry<Identifier, PostChainConfig.InternalTarget> entry : this.internalTargets.entrySet()) {
         Identifier id = entry.getKey();
         PostChainConfig.InternalTarget target = entry.getValue();
         RenderTargetDescriptor descriptor = new RenderTargetDescriptor(
            target.width().orElse(screenWidth), target.height().orElse(screenHeight), true, target.clearColor()
         );
         if (target.persistent()) {
            RenderTarget persistentTarget = this.getOrCreatePersistentTarget(id, descriptor);
            targets.put(id, frame.importExternal(id.toString(), persistentTarget));
         } else {
            targets.put(id, frame.createInternal(id.toString(), descriptor));
         }
      }

      for (PostPass pass : this.passes) {
         pass.addToFrame(frame, targets, projectionBuffer);
      }

      for (Identifier id : this.externalTargets) {
         providedTargets.replace(id, targets.get(id));
      }
   }

   @Deprecated
   public void process(final RenderTarget mainTarget, final GraphicsResourceAllocator resourceAllocator) {
      FrameGraphBuilder frame = new FrameGraphBuilder();
      PostChain.TargetBundle targets = PostChain.TargetBundle.of(MAIN_TARGET_ID, frame.importExternal("main", mainTarget));
      this.addToFrame(frame, mainTarget.width, mainTarget.height, targets);
      frame.execute(resourceAllocator);
   }

   private RenderTarget getOrCreatePersistentTarget(final Identifier id, final RenderTargetDescriptor descriptor) {
      RenderTarget target = this.persistentTargets.get(id);
      if (target == null || target.width != descriptor.width() || target.height != descriptor.height()) {
         if (target != null) {
            target.destroyBuffers();
         }

         target = descriptor.allocate();
         descriptor.prepare(target);
         this.persistentTargets.put(id, target);
      }

      return target;
   }

   @Override
   public void close() {
      this.persistentTargets.values().forEach(RenderTarget::destroyBuffers);
      this.persistentTargets.clear();

      for (PostPass pass : this.passes) {
         pass.close();
      }
   }

   public interface TargetBundle {
      static PostChain.TargetBundle of(final Identifier targetId, final ResourceHandle<RenderTarget> target) {
         return new PostChain.TargetBundle() {
            private ResourceHandle<RenderTarget> handle = target;

            @Override
            public void replace(final Identifier id, final ResourceHandle<RenderTarget> handle) {
               if (id.equals(targetId)) {
                  this.handle = handle;
               } else {
                  throw new IllegalArgumentException("No target with id " + id);
               }
            }

            @Override
            public @Nullable ResourceHandle<RenderTarget> get(final Identifier id) {
               return id.equals(targetId) ? this.handle : null;
            }
         };
      }

      void replace(Identifier id, ResourceHandle<RenderTarget> handle);

      @Nullable ResourceHandle<RenderTarget> get(Identifier id);

      default ResourceHandle<RenderTarget> getOrThrow(final Identifier id) {
         ResourceHandle<RenderTarget> handle = this.get(id);
         if (handle == null) {
            throw new IllegalArgumentException("Missing target with id " + id);
         } else {
            return handle;
         }
      }
   }
}
