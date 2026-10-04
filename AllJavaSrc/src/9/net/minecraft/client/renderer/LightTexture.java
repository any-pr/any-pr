package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.OptionalInt;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;

public class LightTexture implements AutoCloseable {
   public static final int FULL_BRIGHT = 15728880;
   public static final int FULL_SKY = 15728640;
   public static final int FULL_BLOCK = 240;
   private static final int TEXTURE_SIZE = 16;
   private static final int LIGHTMAP_UBO_SIZE = new Std140SizeCalculator()
      .putFloat()
      .putFloat()
      .putFloat()
      .putFloat()
      .putFloat()
      .putFloat()
      .putFloat()
      .putVec3()
      .putVec3()
      .get();
   private final GpuTexture texture;
   private final GpuTextureView textureView;
   private boolean updateLightTexture;
   private float blockLightRedFlicker;
   private final GameRenderer renderer;
   private final Minecraft minecraft;
   private final MappableRingBuffer ubo;
   private final RandomSource randomSource = RandomSource.create();

   public LightTexture(final GameRenderer renderer, final Minecraft minecraft) {
      this.renderer = renderer;
      this.minecraft = minecraft;
      GpuDevice device = RenderSystem.getDevice();
      this.texture = device.createTexture("Light Texture", 12, TextureFormat.RGBA8, 16, 16, 1, 1);
      this.textureView = device.createTextureView(this.texture);
      device.createCommandEncoder().clearColorTexture(this.texture, -1);
      this.ubo = new MappableRingBuffer(() -> "Lightmap UBO", 130, LIGHTMAP_UBO_SIZE);
   }

   public GpuTextureView getTextureView() {
      return this.textureView;
   }

   @Override
   public void close() {
      this.texture.close();
      this.textureView.close();
      this.ubo.close();
   }

   public void tick() {
      this.blockLightRedFlicker = this.blockLightRedFlicker
         + (this.randomSource.nextFloat() - this.randomSource.nextFloat()) * this.randomSource.nextFloat() * this.randomSource.nextFloat() * 0.1F;
      this.blockLightRedFlicker *= 0.9F;
      this.updateLightTexture = true;
   }

   private float calculateDarknessScale(final LivingEntity camera, final float darknessGamma, final float partialTickTime) {
      float darkness = 0.45F * darknessGamma;
      return Math.max(0.0F, Mth.cos((camera.tickCount - partialTickTime) * (float) Math.PI * 0.025F) * darkness);
   }

   public void updateLightTexture(final float partialTicks) {
      if (this.updateLightTexture) {
         this.updateLightTexture = false;
         ProfilerFiller profiler = Profiler.get();
         profiler.push("lightTex");
         ClientLevel level = this.minecraft.level;
         if (level != null) {
            Camera camera = this.minecraft.gameRenderer.getMainCamera();
            int skyLightColor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, partialTicks);
            float ambientLight = level.dimensionType().ambientLight();
            float skyFactor = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTicks);
            EndFlashState endFlashState = level.endFlashState();
            Vector3f ambientColor;
            if (endFlashState != null) {
               ambientColor = new Vector3f(0.99F, 1.12F, 1.0F);
               if (!this.minecraft.options.hideLightningFlash().get()) {
                  float intensity = endFlashState.getIntensity(partialTicks);
                  if (this.minecraft.gui.getBossOverlay().shouldCreateWorldFog()) {
                     skyFactor += intensity / 3.0F;
                  } else {
                     skyFactor += intensity;
                  }
               }
            } else {
               ambientColor = new Vector3f(1.0F, 1.0F, 1.0F);
            }

            float darknessEffectScale = this.minecraft.options.darknessEffectScale().get().floatValue();
            float darknessGamma = this.minecraft.player.getEffectBlendFactor(MobEffects.DARKNESS, partialTicks) * darknessEffectScale;
            float darknessScale = this.calculateDarknessScale(this.minecraft.player, darknessGamma, partialTicks) * darknessEffectScale;
            float waterVision = this.minecraft.player.getWaterVision();
            float nightVision;
            if (this.minecraft.player.hasEffect(MobEffects.NIGHT_VISION)) {
               nightVision = GameRenderer.getNightVisionScale(this.minecraft.player, partialTicks);
            } else if (waterVision > 0.0F && this.minecraft.player.hasEffect(MobEffects.CONDUIT_POWER)) {
               nightVision = waterVision;
            } else {
               nightVision = 0.0F;
            }

            float blockFactor = this.blockLightRedFlicker + 1.5F;
            float brightness = this.minecraft.options.gamma().get().floatValue();
            CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
            MappedView view = commandEncoder.mapBuffer(this.ubo.currentBuffer(), false, true);

            try {
               Std140Builder.intoBuffer(view.data())
                  .putFloat(ambientLight)
                  .putFloat(skyFactor)
                  .putFloat(blockFactor)
                  .putFloat(nightVision)
                  .putFloat(darknessScale)
                  .putFloat(this.renderer.getDarkenWorldAmount(partialTicks))
                  .putFloat(Math.max(0.0F, brightness - darknessGamma))
                  .putVec3(ARGB.vector3fFromRGB24(skyLightColor))
                  .putVec3(ambientColor);
            } catch (Throwable var24) {
               if (view != null) {
                  try {
                     view.close();
                  } catch (Throwable var22) {
                     var24.addSuppressed(var22);
                  }
               }

               throw var24;
            }

            if (view != null) {
               view.close();
            }

            RenderPass renderPass = commandEncoder.createRenderPass(() -> "Update light", this.textureView, OptionalInt.empty());

            try {
               renderPass.setPipeline(RenderPipelines.LIGHTMAP);
               RenderSystem.bindDefaultUniforms(renderPass);
               renderPass.setUniform("LightmapInfo", this.ubo.currentBuffer());
               renderPass.draw(0, 3);
            } catch (Throwable var23) {
               if (renderPass != null) {
                  try {
                     renderPass.close();
                  } catch (Throwable var21) {
                     var23.addSuppressed(var21);
                  }
               }

               throw var23;
            }

            if (renderPass != null) {
               renderPass.close();
            }

            this.ubo.rotate();
            profiler.pop();
         }
      }
   }

   public static float getBrightness(final DimensionType dimensionType, final int level) {
      return getBrightness(dimensionType.ambientLight(), level);
   }

   public static float getBrightness(final float ambientLight, final int level) {
      float v = level / 15.0F;
      float curvedV = v / (4.0F - 3.0F * v);
      return Mth.lerp(ambientLight, curvedV, 1.0F);
   }

   public static int pack(final int block, final int sky) {
      return block << 4 | sky << 20;
   }

   public static int block(final int lightCoords) {
      return lightCoords >>> 4 & 15;
   }

   public static int sky(final int lightCoords) {
      return lightCoords >>> 20 & 15;
   }

   public static int lightCoordsWithEmission(final int lightCoords, final int emission) {
      if (emission == 0) {
         return lightCoords;
      }

      int sky = Math.max(sky(lightCoords), emission);
      int block = Math.max(block(lightCoords), emission);
      return pack(block, sky);
   }
}
