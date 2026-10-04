package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.GpuOutOfMemoryException;
import com.mojang.blaze3d.GraphicsWorkarounds;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;

public class GlDevice implements GpuDevice {
   private static final Logger LOGGER = LogUtils.getLogger();
   protected static boolean USE_GL_ARB_vertex_attrib_binding = true;
   protected static boolean USE_GL_KHR_debug = true;
   protected static boolean USE_GL_EXT_debug_label = true;
   protected static boolean USE_GL_ARB_debug_output = true;
   protected static boolean USE_GL_ARB_direct_state_access = true;
   protected static boolean USE_GL_ARB_buffer_storage = true;
   private final CommandEncoder encoder;
   private final @Nullable GlDebug debugLog;
   private final GlDebugLabel debugLabels;
   private final int maxSupportedTextureSize;
   private final DirectStateAccess directStateAccess;
   private final ShaderSource defaultShaderSource;
   private final Map<RenderPipeline, GlRenderPipeline> pipelineCache = new IdentityHashMap<>();
   private final Map<GlDevice.ShaderCompilationKey, GlShaderModule> shaderCache = new HashMap<>();
   private final VertexArrayCache vertexArrayCache;
   private final BufferStorage bufferStorage;
   private final Set<String> enabledExtensions = new HashSet<>();
   private final int uniformOffsetAlignment;
   private final int maxSupportedAnisotropy;

   public GlDevice(
      final long windowHandle, final int debugLevel, final boolean synchronousLogs, final ShaderSource defaultShaderSource, final boolean wantsDebugLabels
   ) {
      GLFW.glfwMakeContextCurrent(windowHandle);
      GLCapabilities capabilities = GL.createCapabilities();
      int maxSize = getMaxSupportedTextureSize();
      GLFW.glfwSetWindowSizeLimits(windowHandle, -1, -1, maxSize, maxSize);
      GraphicsWorkarounds workarounds = GraphicsWorkarounds.get(this);
      this.debugLog = GlDebug.enableDebugCallback(debugLevel, synchronousLogs, this.enabledExtensions);
      this.debugLabels = GlDebugLabel.create(capabilities, wantsDebugLabels, this.enabledExtensions);
      this.vertexArrayCache = VertexArrayCache.create(capabilities, this.debugLabels, this.enabledExtensions);
      this.bufferStorage = BufferStorage.create(capabilities, this.enabledExtensions);
      this.directStateAccess = DirectStateAccess.create(capabilities, this.enabledExtensions, workarounds);
      this.maxSupportedTextureSize = maxSize;
      this.defaultShaderSource = defaultShaderSource;
      this.encoder = new GlCommandEncoder(this);
      this.uniformOffsetAlignment = GL11.glGetInteger(35380);
      GL11.glEnable(34895);
      GL11.glEnable(34370);
      if (capabilities.GL_EXT_texture_filter_anisotropic) {
         this.maxSupportedAnisotropy = Mth.floor(GL11.glGetFloat(34047));
         this.enabledExtensions.add("GL_EXT_texture_filter_anisotropic");
      } else {
         this.maxSupportedAnisotropy = 1;
      }
   }

   public GlDebugLabel debugLabels() {
      return this.debugLabels;
   }

   @Override
   public CommandEncoder createCommandEncoder() {
      return this.encoder;
   }

   @Override
   public int getMaxSupportedAnisotropy() {
      return this.maxSupportedAnisotropy;
   }

   @Override
   public GpuSampler createSampler(
      final AddressMode addressModeU,
      final AddressMode addressModeV,
      final FilterMode minFilter,
      final FilterMode magFilter,
      final int maxAnisotropy,
      final OptionalDouble maxLod
   ) {
      if (maxAnisotropy >= 1 && maxAnisotropy <= this.maxSupportedAnisotropy) {
         return new GlSampler(addressModeU, addressModeV, minFilter, magFilter, maxAnisotropy, maxLod);
      } else {
         throw new IllegalArgumentException(
            "maxAnisotropy out of range; must be >= 1 and <= " + this.getMaxSupportedAnisotropy() + ", but was " + maxAnisotropy
         );
      }
   }

   @Override
   public GpuTexture createTexture(
      final @Nullable Supplier<String> label,
      @GpuTexture.Usage final int usage,
      final TextureFormat format,
      final int width,
      final int height,
      final int depthOrLayers,
      final int mipLevels
   ) {
      return this.createTexture(this.debugLabels.exists() && label != null ? label.get() : null, usage, format, width, height, depthOrLayers, mipLevels);
   }

   @Override
   public GpuTexture createTexture(
      @Nullable String label,
      @GpuTexture.Usage final int usage,
      final TextureFormat format,
      final int width,
      final int height,
      final int depthOrLayers,
      final int mipLevels
   ) {
      if (mipLevels < 1) {
         throw new IllegalArgumentException("mipLevels must be at least 1");
      }

      if (depthOrLayers < 1) {
         throw new IllegalArgumentException("depthOrLayers must be at least 1");
      }

      boolean isCubemap = (usage & 16) != 0;
      if (isCubemap) {
         if (width != height) {
            throw new IllegalArgumentException("Cubemap compatible textures must be square, but size is " + width + "x" + height);
         }

         if (depthOrLayers % 6 != 0) {
            throw new IllegalArgumentException("Cubemap compatible textures must have a layer count with a multiple of 6, was " + depthOrLayers);
         }

         if (depthOrLayers > 6) {
            throw new UnsupportedOperationException("Array textures are not yet supported");
         }
      } else if (depthOrLayers > 1) {
         throw new UnsupportedOperationException("Array or 3D textures are not yet supported");
      }

      GlStateManager.clearGlErrors();
      int id = GlStateManager._genTexture();
      if (label == null) {
         label = String.valueOf(id);
      }

      int target;
      if (isCubemap) {
         GL11.glBindTexture(34067, id);
         target = 34067;
      } else {
         GlStateManager._bindTexture(id);
         target = 3553;
      }

      GlStateManager._texParameter(target, 33085, mipLevels - 1);
      GlStateManager._texParameter(target, 33082, 0);
      GlStateManager._texParameter(target, 33083, mipLevels - 1);
      if (format.hasDepthAspect()) {
         GlStateManager._texParameter(target, 34892, 0);
      }

      if (isCubemap) {
         for (int cubeTarget : GlConst.CUBEMAP_TARGETS) {
            for (int i = 0; i < mipLevels; i++) {
               GlStateManager._texImage2D(
                  cubeTarget, i, GlConst.toGlInternalId(format), width >> i, height >> i, 0, GlConst.toGlExternalId(format), GlConst.toGlType(format), null
               );
            }
         }
      } else {
         for (int i = 0; i < mipLevels; i++) {
            GlStateManager._texImage2D(
               target, i, GlConst.toGlInternalId(format), width >> i, height >> i, 0, GlConst.toGlExternalId(format), GlConst.toGlType(format), null
            );
         }
      }

      int error = GlStateManager._getError();
      if (error == 1285) {
         throw new GpuOutOfMemoryException("Could not allocate texture of " + width + "x" + height + " for " + label);
      }

      if (error != 0) {
         throw new IllegalStateException("OpenGL error " + error);
      }

      GlTexture texture = new GlTexture(usage, label, format, width, height, depthOrLayers, mipLevels, id);
      this.debugLabels.applyLabel(texture);
      return texture;
   }

   @Override
   public GpuTextureView createTextureView(final GpuTexture texture) {
      return this.createTextureView(texture, 0, texture.getMipLevels());
   }

   @Override
   public GpuTextureView createTextureView(final GpuTexture texture, final int baseMipLevel, final int mipLevels) {
      if (texture.isClosed()) {
         throw new IllegalArgumentException("Can't create texture view with closed texture");
      } else if (baseMipLevel >= 0 && baseMipLevel + mipLevels <= texture.getMipLevels()) {
         return new GlTextureView((GlTexture)texture, baseMipLevel, mipLevels);
      } else {
         throw new IllegalArgumentException(
            mipLevels + " mip levels starting from " + baseMipLevel + " would be out of range for texture with only " + texture.getMipLevels() + " mip levels"
         );
      }
   }

   @Override
   public GpuBuffer createBuffer(final @Nullable Supplier<String> label, @GpuBuffer.Usage final int usage, final long size) {
      if (size <= 0L) {
         throw new IllegalArgumentException("Buffer size must be greater than zero");
      }

      GlStateManager.clearGlErrors();
      GlBuffer buffer = this.bufferStorage.createBuffer(this.directStateAccess, label, usage, size);
      int error = GlStateManager._getError();
      if (error == 1285) {
         throw new GpuOutOfMemoryException("Could not allocate buffer of " + size + " for " + label);
      }

      if (error != 0) {
         throw new IllegalStateException("OpenGL error " + error);
      }

      this.debugLabels.applyLabel(buffer);
      return buffer;
   }

   @Override
   public GpuBuffer createBuffer(final @Nullable Supplier<String> label, @GpuBuffer.Usage final int usage, final ByteBuffer data) {
      if (!data.hasRemaining()) {
         throw new IllegalArgumentException("Buffer source must not be empty");
      }

      GlStateManager.clearGlErrors();
      long size = data.remaining();
      GlBuffer buffer = this.bufferStorage.createBuffer(this.directStateAccess, label, usage, data);
      int error = GlStateManager._getError();
      if (error == 1285) {
         throw new GpuOutOfMemoryException("Could not allocate buffer of " + size + " for " + label);
      }

      if (error != 0) {
         throw new IllegalStateException("OpenGL error " + error);
      }

      this.debugLabels.applyLabel(buffer);
      return buffer;
   }

   @Override
   public String getImplementationInformation() {
      return GLFW.glfwGetCurrentContext() == 0L
         ? "NO CONTEXT"
         : GlStateManager._getString(7937) + " GL version " + GlStateManager._getString(7938) + ", " + GlStateManager._getString(7936);
   }

   @Override
   public List<String> getLastDebugMessages() {
      return this.debugLog == null ? Collections.emptyList() : this.debugLog.getLastOpenGlDebugMessages();
   }

   @Override
   public boolean isDebuggingEnabled() {
      return this.debugLog != null;
   }

   @Override
   public String getRenderer() {
      return GlStateManager._getString(7937);
   }

   @Override
   public String getVendor() {
      return GlStateManager._getString(7936);
   }

   @Override
   public String getBackendName() {
      return "OpenGL";
   }

   @Override
   public String getVersion() {
      return GlStateManager._getString(7938);
   }

   private static int getMaxSupportedTextureSize() {
      int maxReported = GlStateManager._getInteger(3379);

      for (int texSize = Math.max(32768, maxReported); texSize >= 1024; texSize >>= 1) {
         GlStateManager._texImage2D(32868, 0, 6408, texSize, texSize, 0, 6408, 5121, null);
         int width = GlStateManager._getTexLevelParameter(32868, 0, 4096);
         if (width != 0) {
            return texSize;
         }
      }

      int maxSupportedTextureSize = Math.max(maxReported, 1024);
      LOGGER.info("Failed to determine maximum texture size by probing, trying GL_MAX_TEXTURE_SIZE = {}", maxSupportedTextureSize);
      return maxSupportedTextureSize;
   }

   @Override
   public int getMaxTextureSize() {
      return this.maxSupportedTextureSize;
   }

   @Override
   public int getUniformOffsetAlignment() {
      return this.uniformOffsetAlignment;
   }

   @Override
   public void clearPipelineCache() {
      for (GlRenderPipeline pipeline : this.pipelineCache.values()) {
         if (pipeline.program() != GlProgram.INVALID_PROGRAM) {
            pipeline.program().close();
         }
      }

      this.pipelineCache.clear();

      for (GlShaderModule shader : this.shaderCache.values()) {
         if (shader != GlShaderModule.INVALID_SHADER) {
            shader.close();
         }
      }

      this.shaderCache.clear();
      String glRenderer = GlStateManager._getString(7937);
      if (glRenderer.contains("AMD")) {
         sacrificeShaderToOpenGlAndAmd();
      }
   }

   private static void sacrificeShaderToOpenGlAndAmd() {
      int shader = GlStateManager.glCreateShader(35633);
      int program = GlStateManager.glCreateProgram();
      GlStateManager.glAttachShader(program, shader);
      GlStateManager.glDeleteShader(shader);
      GlStateManager.glDeleteProgram(program);
   }

   @Override
   public List<String> getEnabledExtensions() {
      return new ArrayList<>(this.enabledExtensions);
   }

   @Override
   public void close() {
      this.clearPipelineCache();
   }

   public DirectStateAccess directStateAccess() {
      return this.directStateAccess;
   }

   protected GlRenderPipeline getOrCompilePipeline(final RenderPipeline pipeline) {
      return this.pipelineCache.computeIfAbsent(pipeline, p -> this.compilePipeline(p, this.defaultShaderSource));
   }

   protected GlShaderModule getOrCompileShader(final Identifier id, final ShaderType type, final ShaderDefines defines, final ShaderSource shaderSource) {
      GlDevice.ShaderCompilationKey key = new GlDevice.ShaderCompilationKey(id, type, defines);
      return this.shaderCache.computeIfAbsent(key, k -> this.compileShader(k, shaderSource));
   }

   public GlRenderPipeline precompilePipeline(final RenderPipeline pipeline, final @Nullable ShaderSource customShaderSource) {
      ShaderSource shaderSource = customShaderSource == null ? this.defaultShaderSource : customShaderSource;
      return this.pipelineCache.computeIfAbsent(pipeline, p -> this.compilePipeline(p, shaderSource));
   }

   private GlShaderModule compileShader(final GlDevice.ShaderCompilationKey key, final ShaderSource shaderSource) {
      String source = shaderSource.get(key.id, key.type);
      if (source == null) {
         LOGGER.error("Couldn't find source for {} shader ({})", key.type, key.id);
         return GlShaderModule.INVALID_SHADER;
      } else {
         String sourceWithDefines = GlslPreprocessor.injectDefines(source, key.defines);
         int shaderId = GlStateManager.glCreateShader(GlConst.toGl(key.type));
         GlStateManager.glShaderSource(shaderId, sourceWithDefines);
         GlStateManager.glCompileShader(shaderId);
         if (GlStateManager.glGetShaderi(shaderId, 35713) == 0) {
            String logInfo = StringUtils.trim(GlStateManager.glGetShaderInfoLog(shaderId, 32768));
            LOGGER.error("Couldn't compile {} shader ({}): {}", new Object[]{key.type.getName(), key.id, logInfo});
            return GlShaderModule.INVALID_SHADER;
         } else {
            GlShaderModule module = new GlShaderModule(shaderId, key.id, key.type);
            this.debugLabels.applyLabel(module);
            return module;
         }
      }
   }

   private GlProgram compileProgram(final RenderPipeline pipeline, final ShaderSource shaderSource) {
      GlShaderModule vertexShader = this.getOrCompileShader(pipeline.getVertexShader(), ShaderType.VERTEX, pipeline.getShaderDefines(), shaderSource);
      GlShaderModule fragmentShader = this.getOrCompileShader(pipeline.getFragmentShader(), ShaderType.FRAGMENT, pipeline.getShaderDefines(), shaderSource);
      if (vertexShader == GlShaderModule.INVALID_SHADER) {
         LOGGER.error("Couldn't compile pipeline {}: vertex shader {} was invalid", pipeline.getLocation(), pipeline.getVertexShader());
         return GlProgram.INVALID_PROGRAM;
      }

      if (fragmentShader == GlShaderModule.INVALID_SHADER) {
         LOGGER.error("Couldn't compile pipeline {}: fragment shader {} was invalid", pipeline.getLocation(), pipeline.getFragmentShader());
         return GlProgram.INVALID_PROGRAM;
      }

      try {
         GlProgram compiled = GlProgram.link(vertexShader, fragmentShader, pipeline.getVertexFormat(), pipeline.getLocation().toString());
         compiled.setupUniforms(pipeline.getUniforms(), pipeline.getSamplers());
         this.debugLabels.applyLabel(compiled);
         return compiled;
      } catch (ShaderManager.CompilationException e) {
         LOGGER.error("Couldn't compile program for pipeline {}: {}", pipeline.getLocation(), e);
         return GlProgram.INVALID_PROGRAM;
      }
   }

   private GlRenderPipeline compilePipeline(final RenderPipeline pipeline, final ShaderSource shaderSource) {
      return new GlRenderPipeline(pipeline, this.compileProgram(pipeline, shaderSource));
   }

   public VertexArrayCache vertexArrayCache() {
      return this.vertexArrayCache;
   }

   public BufferStorage getBufferStorage() {
      return this.bufferStorage;
   }

   private record ShaderCompilationKey(Identifier id, ShaderType type, ShaderDefines defines) {
      @Override
      public String toString() {
         String string = this.id + " (" + this.type + ")";
         return !this.defines.isEmpty() ? string + " with " + this.defines : string;
      }
   }
}
