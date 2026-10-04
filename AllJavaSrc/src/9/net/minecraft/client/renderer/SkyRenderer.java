package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.AutoStorageIndexBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.math.Axis;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class SkyRenderer implements AutoCloseable {
   private static final Identifier SUN_SPRITE = Identifier.withDefaultNamespace("sun");
   private static final Identifier END_FLASH_SPRITE = Identifier.withDefaultNamespace("end_flash");
   private static final Identifier END_SKY_LOCATION = Identifier.withDefaultNamespace("textures/environment/end_sky.png");
   private static final float SKY_DISC_RADIUS = 512.0F;
   private static final int SKY_VERTICES = 10;
   private static final int STAR_COUNT = 1500;
   private static final float SUN_SIZE = 30.0F;
   private static final float SUN_HEIGHT = 100.0F;
   private static final float MOON_SIZE = 20.0F;
   private static final float MOON_HEIGHT = 100.0F;
   private static final int SUNRISE_STEPS = 16;
   private static final int END_SKY_QUAD_COUNT = 6;
   private static final float END_FLASH_HEIGHT = 100.0F;
   private static final float END_FLASH_SCALE = 60.0F;
   private final TextureAtlas celestialsAtlas;
   private final GpuBuffer starBuffer;
   private final GpuBuffer topSkyBuffer;
   private final GpuBuffer bottomSkyBuffer;
   private final GpuBuffer endSkyBuffer;
   private final GpuBuffer sunBuffer;
   private final GpuBuffer moonBuffer;
   private final GpuBuffer sunriseBuffer;
   private final GpuBuffer endFlashBuffer;
   private final AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(Mode.QUADS);
   private final AbstractTexture endSkyTexture;
   private int starIndexCount;

   public SkyRenderer(final TextureManager textureManager, final AtlasManager atlasManager) {
      this.celestialsAtlas = atlasManager.getAtlasOrThrow(AtlasIds.CELESTIALS);
      this.starBuffer = this.buildStars();
      this.endSkyBuffer = buildEndSky();
      this.endSkyTexture = this.getTexture(textureManager, END_SKY_LOCATION);
      this.endFlashBuffer = buildEndFlashQuad(this.celestialsAtlas);
      this.sunBuffer = buildSunQuad(this.celestialsAtlas);
      this.moonBuffer = buildMoonPhases(this.celestialsAtlas);
      this.sunriseBuffer = this.buildSunriseFan();
      ByteBufferBuilder builder = ByteBufferBuilder.exactlySized(10 * DefaultVertexFormat.POSITION.getVertexSize());

      try {
         BufferBuilder bufferBuilder = new BufferBuilder(builder, Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
         this.buildSkyDisc(bufferBuilder, 16.0F);
         MeshData meshData = bufferBuilder.buildOrThrow();

         try {
            this.topSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Top sky vertex buffer", 32, meshData.vertexBuffer());
         } catch (Throwable var12) {
            if (meshData != null) {
               try {
                  meshData.close();
               } catch (Throwable var10) {
                  var12.addSuppressed(var10);
               }
            }

            throw var12;
         }

         if (meshData != null) {
            meshData.close();
         }

         bufferBuilder = new BufferBuilder(builder, Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
         this.buildSkyDisc(bufferBuilder, -16.0F);
         meshData = bufferBuilder.buildOrThrow();

         try {
            this.bottomSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Bottom sky vertex buffer", 32, meshData.vertexBuffer());
         } catch (Throwable var11) {
            if (meshData != null) {
               try {
                  meshData.close();
               } catch (Throwable var9) {
                  var11.addSuppressed(var9);
               }
            }

            throw var11;
         }

         if (meshData != null) {
            meshData.close();
         }
      } catch (Throwable var13) {
         if (builder != null) {
            try {
               builder.close();
            } catch (Throwable var8) {
               var13.addSuppressed(var8);
            }
         }

         throw var13;
      }

      if (builder != null) {
         builder.close();
      }
   }

   private AbstractTexture getTexture(final TextureManager textureManager, final Identifier location) {
      return textureManager.getTexture(location);
   }

   private GpuBuffer buildSunriseFan() {
      int vertices = 18;
      int vtxSize = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
      ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(18 * vtxSize);

      GpuBuffer var16;
      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
         int centerColor = ARGB.white(1.0F);
         int ringColor = ARGB.white(0.0F);
         bufferBuilder.addVertex(0.0F, 100.0F, 0.0F).setColor(centerColor);

         for (int i = 0; i <= 16; i++) {
            float angle = i * (float) (Math.PI * 2) / 16.0F;
            float sinAngle = Mth.sin(angle);
            float cosAngle = Mth.cos(angle);
            bufferBuilder.addVertex(sinAngle * 120.0F, cosAngle * 120.0F, -cosAngle * 40.0F).setColor(ringColor);
         }

         MeshData mesh = bufferBuilder.buildOrThrow();

         try {
            var16 = RenderSystem.getDevice().createBuffer(() -> "Sunrise/Sunset fan", 32, mesh.vertexBuffer());
         } catch (Throwable var13) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var12) {
                  var13.addSuppressed(var12);
               }
            }

            throw var13;
         }

         if (mesh != null) {
            mesh.close();
         }
      } catch (Throwable var14) {
         if (byteBufferBuilder != null) {
            try {
               byteBufferBuilder.close();
            } catch (Throwable var11) {
               var14.addSuppressed(var11);
            }
         }

         throw var14;
      }

      if (byteBufferBuilder != null) {
         byteBufferBuilder.close();
      }

      return var16;
   }

   private static GpuBuffer buildSunQuad(final TextureAtlas atlas) {
      return buildCelestialQuad("Sun quad", atlas.getSprite(SUN_SPRITE));
   }

   private static GpuBuffer buildEndFlashQuad(final TextureAtlas atlas) {
      return buildCelestialQuad("End flash quad", atlas.getSprite(END_FLASH_SPRITE));
   }

   private static GpuBuffer buildCelestialQuad(final String name, final TextureAtlasSprite sprite) {
      VertexFormat format = DefaultVertexFormat.POSITION_TEX;
      ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(4 * format.getVertexSize());

      GpuBuffer var6;
      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, Mode.QUADS, format);
         bufferBuilder.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV0());
         bufferBuilder.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV0());
         bufferBuilder.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV1());
         bufferBuilder.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV1());
         MeshData mesh = bufferBuilder.buildOrThrow();

         try {
            var6 = RenderSystem.getDevice().createBuffer(() -> name, 32, mesh.vertexBuffer());
         } catch (Throwable var10) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (mesh != null) {
            mesh.close();
         }
      } catch (Throwable var11) {
         if (byteBufferBuilder != null) {
            try {
               byteBufferBuilder.close();
            } catch (Throwable var8) {
               var11.addSuppressed(var8);
            }
         }

         throw var11;
      }

      if (byteBufferBuilder != null) {
         byteBufferBuilder.close();
      }

      return var6;
   }

   private static GpuBuffer buildMoonPhases(final TextureAtlas atlas) {
      MoonPhase[] phases = MoonPhase.values();
      VertexFormat format = DefaultVertexFormat.POSITION_TEX;
      ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(phases.length * 4 * format.getVertexSize());

      GpuBuffer var15;
      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, Mode.QUADS, format);

         for (MoonPhase phase : phases) {
            TextureAtlasSprite sprite = atlas.getSprite(Identifier.withDefaultNamespace("moon/" + phase.getSerializedName()));
            bufferBuilder.addVertex(-1.0F, 0.0F, -1.0F).setUv(sprite.getU1(), sprite.getV1());
            bufferBuilder.addVertex(1.0F, 0.0F, -1.0F).setUv(sprite.getU0(), sprite.getV1());
            bufferBuilder.addVertex(1.0F, 0.0F, 1.0F).setUv(sprite.getU0(), sprite.getV0());
            bufferBuilder.addVertex(-1.0F, 0.0F, 1.0F).setUv(sprite.getU1(), sprite.getV0());
         }

         MeshData mesh = bufferBuilder.buildOrThrow();

         try {
            var15 = RenderSystem.getDevice().createBuffer(() -> "Moon phases", 32, mesh.vertexBuffer());
         } catch (Throwable var12) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var11) {
                  var12.addSuppressed(var11);
               }
            }

            throw var12;
         }

         if (mesh != null) {
            mesh.close();
         }
      } catch (Throwable var13) {
         if (byteBufferBuilder != null) {
            try {
               byteBufferBuilder.close();
            } catch (Throwable var10) {
               var13.addSuppressed(var10);
            }
         }

         throw var13;
      }

      if (byteBufferBuilder != null) {
         byteBufferBuilder.close();
      }

      return var15;
   }

   private GpuBuffer buildStars() {
      RandomSource random = RandomSource.create(10842L);
      float starDistance = 100.0F;
      ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 1500 * 4);

      GpuBuffer var19;
      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, Mode.QUADS, DefaultVertexFormat.POSITION);

         for (int i = 0; i < 1500; i++) {
            float x = random.nextFloat() * 2.0F - 1.0F;
            float y = random.nextFloat() * 2.0F - 1.0F;
            float z = random.nextFloat() * 2.0F - 1.0F;
            float starSize = 0.15F + random.nextFloat() * 0.1F;
            float lengthSq = Mth.lengthSquared(x, y, z);
            if (!(lengthSq <= 0.010000001F) && !(lengthSq >= 1.0F)) {
               Vector3f starCenter = new Vector3f(x, y, z).normalize(100.0F);
               float zRot = (float)(random.nextDouble() * (float) Math.PI * 2.0);
               Matrix3f rotation = new Matrix3f().rotateTowards(new Vector3f(starCenter).negate(), new Vector3f(0.0F, 1.0F, 0.0F)).rotateZ(-zRot);
               bufferBuilder.addVertex(new Vector3f(starSize, -starSize, 0.0F).mul(rotation).add(starCenter));
               bufferBuilder.addVertex(new Vector3f(starSize, starSize, 0.0F).mul(rotation).add(starCenter));
               bufferBuilder.addVertex(new Vector3f(-starSize, starSize, 0.0F).mul(rotation).add(starCenter));
               bufferBuilder.addVertex(new Vector3f(-starSize, -starSize, 0.0F).mul(rotation).add(starCenter));
            }
         }

         MeshData mesh = bufferBuilder.buildOrThrow();

         try {
            this.starIndexCount = mesh.drawState().indexCount();
            var19 = RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer", 40, mesh.vertexBuffer());
         } catch (Throwable var16) {
            if (mesh != null) {
               try {
                  mesh.close();
               } catch (Throwable var15) {
                  var16.addSuppressed(var15);
               }
            }

            throw var16;
         }

         if (mesh != null) {
            mesh.close();
         }
      } catch (Throwable var17) {
         if (byteBufferBuilder != null) {
            try {
               byteBufferBuilder.close();
            } catch (Throwable var14) {
               var17.addSuppressed(var14);
            }
         }

         throw var17;
      }

      if (byteBufferBuilder != null) {
         byteBufferBuilder.close();
      }

      return var19;
   }

   private void buildSkyDisc(final VertexConsumer builder, final float yy) {
      float x = Math.signum(yy) * 512.0F;
      builder.addVertex(0.0F, yy, 0.0F);

      for (int i = -180; i <= 180; i += 45) {
         builder.addVertex(x * Mth.cos(i * (float) (Math.PI / 180.0)), yy, 512.0F * Mth.sin(i * (float) (Math.PI / 180.0)));
      }
   }

   private static GpuBuffer buildEndSky() {
      ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());

      GpuBuffer var10;
      try {
         BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

         for (int i = 0; i < 6; i++) {
            Matrix4f pose = new Matrix4f();
            switch (i) {
               case 1:
                  pose.rotationX((float) (Math.PI / 2));
                  break;
               case 2:
                  pose.rotationX((float) (-Math.PI / 2));
                  break;
               case 3:
                  pose.rotationX((float) Math.PI);
                  break;
               case 4:
                  pose.rotationZ((float) (Math.PI / 2));
                  break;
               case 5:
                  pose.rotationZ((float) (-Math.PI / 2));
            }

            bufferBuilder.addVertex(pose, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(-14145496);
            bufferBuilder.addVertex(pose, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(-14145496);
            bufferBuilder.addVertex(pose, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(-14145496);
            bufferBuilder.addVertex(pose, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(-14145496);
         }

         MeshData meshData = bufferBuilder.buildOrThrow();

         try {
            var10 = RenderSystem.getDevice().createBuffer(() -> "End sky vertex buffer", 40, meshData.vertexBuffer());
         } catch (Throwable var7) {
            if (meshData != null) {
               try {
                  meshData.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (meshData != null) {
            meshData.close();
         }
      } catch (Throwable var8) {
         if (byteBufferBuilder != null) {
            try {
               byteBufferBuilder.close();
            } catch (Throwable var5) {
               var8.addSuppressed(var5);
            }
         }

         throw var8;
      }

      if (byteBufferBuilder != null) {
         byteBufferBuilder.close();
      }

      return var10;
   }

   public void renderSkyDisc(final int skyColor) {
      GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
         .writeTransform(RenderSystem.getModelViewMatrix(), ARGB.vector4fFromARGB32(skyColor), new Vector3f(), new Matrix4f());
      GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
      GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "Sky disc", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty());

      try {
         renderPass.setPipeline(RenderPipelines.SKY);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", dynamicTransforms);
         renderPass.setVertexBuffer(0, this.topSkyBuffer);
         renderPass.draw(0, 10);
      } catch (Throwable var9) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (renderPass != null) {
         renderPass.close();
      }
   }

   public void extractRenderState(final ClientLevel level, final float partialTicks, final Camera camera, final SkyRenderState state) {
      state.skybox = level.dimensionType().skybox();
      if (state.skybox != DimensionType.Skybox.NONE) {
         if (state.skybox == DimensionType.Skybox.END) {
            EndFlashState endFlashState = level.endFlashState();
            if (endFlashState != null) {
               state.endFlashIntensity = endFlashState.getIntensity(partialTicks);
               state.endFlashXAngle = endFlashState.getXAngle();
               state.endFlashYAngle = endFlashState.getYAngle();
            }
         } else {
            EnvironmentAttributeProbe attributeProbe = camera.attributeProbe();
            state.sunAngle = attributeProbe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks) * (float) (Math.PI / 180.0);
            state.moonAngle = attributeProbe.getValue(EnvironmentAttributes.MOON_ANGLE, partialTicks) * (float) (Math.PI / 180.0);
            state.starAngle = attributeProbe.getValue(EnvironmentAttributes.STAR_ANGLE, partialTicks) * (float) (Math.PI / 180.0);
            state.rainBrightness = 1.0F - level.getRainLevel(partialTicks);
            state.starBrightness = attributeProbe.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partialTicks);
            state.sunriseAndSunsetColor = camera.attributeProbe().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, partialTicks);
            state.moonPhase = attributeProbe.getValue(EnvironmentAttributes.MOON_PHASE, partialTicks);
            state.skyColor = attributeProbe.getValue(EnvironmentAttributes.SKY_COLOR, partialTicks);
            state.shouldRenderDarkDisc = this.shouldRenderDarkDisc(partialTicks, level);
         }
      }
   }

   private boolean shouldRenderDarkDisc(final float deltaPartialTick, final ClientLevel level) {
      return Minecraft.getInstance().player.getEyePosition(deltaPartialTick).y - level.getLevelData().getHorizonHeight(level) < 0.0;
   }

   public void renderDarkDisc() {
      Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushMatrix();
      modelViewStack.translate(0.0F, 12.0F, 0.0F);
      GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
         .writeTransform(modelViewStack, new Vector4f(0.0F, 0.0F, 0.0F, 1.0F), new Vector3f(), new Matrix4f());
      GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
      GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "Sky dark", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty());

      try {
         renderPass.setPipeline(RenderPipelines.SKY);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", dynamicTransforms);
         renderPass.setVertexBuffer(0, this.bottomSkyBuffer);
         renderPass.draw(0, 10);
      } catch (Throwable var9) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (renderPass != null) {
         renderPass.close();
      }

      modelViewStack.popMatrix();
   }

   public void renderSunMoonAndStars(
      final PoseStack poseStack,
      final float sunAngle,
      final float moonAngle,
      final float starAngle,
      final MoonPhase moonPhase,
      final float rainBrightness,
      final float starBrightness
   ) {
      poseStack.pushPose();
      poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
      poseStack.pushPose();
      poseStack.mulPose(Axis.XP.rotation(sunAngle));
      this.renderSun(rainBrightness, poseStack);
      poseStack.popPose();
      poseStack.pushPose();
      poseStack.mulPose(Axis.XP.rotation(moonAngle));
      this.renderMoon(moonPhase, rainBrightness, poseStack);
      poseStack.popPose();
      if (starBrightness > 0.0F) {
         poseStack.pushPose();
         poseStack.mulPose(Axis.XP.rotation(starAngle));
         this.renderStars(starBrightness, poseStack);
         poseStack.popPose();
      }

      poseStack.popPose();
   }

   private void renderSun(final float rainBrightness, final PoseStack poseStack) {
      Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushMatrix();
      modelViewStack.mul(poseStack.last().pose());
      modelViewStack.translate(0.0F, 100.0F, 0.0F);
      modelViewStack.scale(30.0F, 1.0F, 30.0F);
      GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
         .writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness), new Vector3f(), new Matrix4f());
      GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
      GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
      GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "Sky sun", color, OptionalInt.empty(), depth, OptionalDouble.empty());

      try {
         renderPass.setPipeline(RenderPipelines.CELESTIAL);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", dynamicTransforms);
         renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(), this.celestialsAtlas.getSampler());
         renderPass.setVertexBuffer(0, this.sunBuffer);
         renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
         renderPass.drawIndexed(0, 0, 6, 1);
      } catch (Throwable var12) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var11) {
               var12.addSuppressed(var11);
            }
         }

         throw var12;
      }

      if (renderPass != null) {
         renderPass.close();
      }

      modelViewStack.popMatrix();
   }

   private void renderMoon(final MoonPhase moonPhase, final float rainBrightness, final PoseStack poseStack) {
      int baseVertex = moonPhase.index() * 4;
      Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushMatrix();
      modelViewStack.mul(poseStack.last().pose());
      modelViewStack.translate(0.0F, 100.0F, 0.0F);
      modelViewStack.scale(20.0F, 1.0F, 20.0F);
      GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
         .writeTransform(modelViewStack, new Vector4f(1.0F, 1.0F, 1.0F, rainBrightness), new Vector3f(), new Matrix4f());
      GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
      GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
      GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "Sky moon", color, OptionalInt.empty(), depth, OptionalDouble.empty());

      try {
         renderPass.setPipeline(RenderPipelines.CELESTIAL);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", dynamicTransforms);
         renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(), this.celestialsAtlas.getSampler());
         renderPass.setVertexBuffer(0, this.moonBuffer);
         renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
         renderPass.drawIndexed(baseVertex, 0, 6, 1);
      } catch (Throwable var14) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }
         }

         throw var14;
      }

      if (renderPass != null) {
         renderPass.close();
      }

      modelViewStack.popMatrix();
   }

   private void renderStars(final float starBrightness, final PoseStack poseStack) {
      Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushMatrix();
      modelViewStack.mul(poseStack.last().pose());
      RenderPipeline renderPipeline = RenderPipelines.STARS;
      GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
      GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
      GpuBuffer indexBuffer = this.quadIndices.getBuffer(this.starIndexCount);
      GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
         .writeTransform(modelViewStack, new Vector4f(starBrightness, starBrightness, starBrightness, starBrightness), new Vector3f(), new Matrix4f());
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "Stars", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty());

      try {
         renderPass.setPipeline(renderPipeline);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", dynamicTransforms);
         renderPass.setVertexBuffer(0, this.starBuffer);
         renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
         renderPass.drawIndexed(0, 0, this.starIndexCount, 1);
      } catch (Throwable var13) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var12) {
               var13.addSuppressed(var12);
            }
         }

         throw var13;
      }

      if (renderPass != null) {
         renderPass.close();
      }

      modelViewStack.popMatrix();
   }

   public void renderSunriseAndSunset(final PoseStack poseStack, final float sunAngle, final int sunriseAndSunsetColor) {
      float alpha = ARGB.alphaFloat(sunriseAndSunsetColor);
      if (!(alpha <= 0.001F)) {
         poseStack.pushPose();
         poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
         float angle = Mth.sin(sunAngle) < 0.0F ? 180.0F : 0.0F;
         poseStack.mulPose(Axis.ZP.rotationDegrees(angle + 90.0F));
         Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
         modelViewStack.pushMatrix();
         modelViewStack.mul(poseStack.last().pose());
         modelViewStack.scale(1.0F, 1.0F, alpha);
         GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
            .writeTransform(modelViewStack, ARGB.vector4fFromARGB32(sunriseAndSunsetColor), new Vector3f(), new Matrix4f());
         GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
         GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
         RenderPass renderPass = RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(() -> "Sunrise sunset", color, OptionalInt.empty(), depth, OptionalDouble.empty());

         try {
            renderPass.setPipeline(RenderPipelines.SUNRISE_SUNSET);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, this.sunriseBuffer);
            renderPass.draw(0, 18);
         } catch (Throwable var14) {
            if (renderPass != null) {
               try {
                  renderPass.close();
               } catch (Throwable var13) {
                  var14.addSuppressed(var13);
               }
            }

            throw var14;
         }

         if (renderPass != null) {
            renderPass.close();
         }

         modelViewStack.popMatrix();
         poseStack.popPose();
      }
   }

   public void renderEndSky() {
      AutoStorageIndexBuffer autoIndices = RenderSystem.getSequentialBuffer(Mode.QUADS);
      GpuBuffer indexBuffer = autoIndices.getBuffer(36);
      GpuTextureView colorTexture = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
      GpuTextureView depthTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
      GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
         .writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "End sky", colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty());

      try {
         renderPass.setPipeline(RenderPipelines.END_SKY);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", dynamicTransforms);
         renderPass.bindTexture("Sampler0", this.endSkyTexture.getTextureView(), this.endSkyTexture.getSampler());
         renderPass.setVertexBuffer(0, this.endSkyBuffer);
         renderPass.setIndexBuffer(indexBuffer, autoIndices.type());
         renderPass.drawIndexed(0, 0, 36, 1);
      } catch (Throwable var10) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var9) {
               var10.addSuppressed(var9);
            }
         }

         throw var10;
      }

      if (renderPass != null) {
         renderPass.close();
      }
   }

   public void renderEndFlash(final PoseStack poseStack, final float intensity, final float xAngle, final float yAngle) {
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yAngle));
      poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - xAngle));
      Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
      modelViewStack.pushMatrix();
      modelViewStack.mul(poseStack.last().pose());
      modelViewStack.translate(0.0F, 100.0F, 0.0F);
      modelViewStack.scale(60.0F, 1.0F, 60.0F);
      GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
         .writeTransform(modelViewStack, new Vector4f(intensity, intensity, intensity, intensity), new Vector3f(), new Matrix4f());
      GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
      GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
      GpuBuffer indexBuffer = this.quadIndices.getBuffer(6);
      RenderPass renderPass = RenderSystem.getDevice()
         .createCommandEncoder()
         .createRenderPass(() -> "End flash", color, OptionalInt.empty(), depth, OptionalDouble.empty());

      try {
         renderPass.setPipeline(RenderPipelines.CELESTIAL);
         RenderSystem.bindDefaultUniforms(renderPass);
         renderPass.setUniform("DynamicTransforms", dynamicTransforms);
         renderPass.bindTexture("Sampler0", this.celestialsAtlas.getTextureView(), this.celestialsAtlas.getSampler());
         renderPass.setVertexBuffer(0, this.endFlashBuffer);
         renderPass.setIndexBuffer(indexBuffer, this.quadIndices.type());
         renderPass.drawIndexed(0, 0, 6, 1);
      } catch (Throwable var14) {
         if (renderPass != null) {
            try {
               renderPass.close();
            } catch (Throwable var13) {
               var14.addSuppressed(var13);
            }
         }

         throw var14;
      }

      if (renderPass != null) {
         renderPass.close();
      }

      modelViewStack.popMatrix();
   }

   @Override
   public void close() {
      this.sunBuffer.close();
      this.moonBuffer.close();
      this.starBuffer.close();
      this.topSkyBuffer.close();
      this.bottomSkyBuffer.close();
      this.endSkyBuffer.close();
      this.sunriseBuffer.close();
      this.endFlashBuffer.close();
   }
}
