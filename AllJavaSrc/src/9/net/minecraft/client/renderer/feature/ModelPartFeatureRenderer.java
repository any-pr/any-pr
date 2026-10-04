package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.ModelBakery;

public class ModelPartFeatureRenderer {
   private final PoseStack poseStack = new PoseStack();

   public void render(
      final SubmitNodeCollection nodeCollection,
      final MultiBufferSource.BufferSource bufferSource,
      final OutlineBufferSource outlineBufferSource,
      final MultiBufferSource.BufferSource crumblingBufferSource
   ) {
      ModelPartFeatureRenderer.Storage storage = nodeCollection.getModelPartSubmits();

      for (Entry<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> entry : storage.modelPartSubmits.entrySet()) {
         RenderType renderType = entry.getKey();
         List<SubmitNodeStorage.ModelPartSubmit> modelPartSubmits = entry.getValue();
         VertexConsumer buffer = bufferSource.getBuffer(renderType);

         for (SubmitNodeStorage.ModelPartSubmit modelPartSubmit : modelPartSubmits) {
            VertexConsumer actualBuffer;
            if (modelPartSubmit.sprite() != null) {
               if (modelPartSubmit.hasFoil()) {
                  actualBuffer = modelPartSubmit.sprite().wrap(ItemRenderer.getFoilBuffer(bufferSource, renderType, modelPartSubmit.sheeted(), true));
               } else {
                  actualBuffer = modelPartSubmit.sprite().wrap(buffer);
               }
            } else if (modelPartSubmit.hasFoil()) {
               actualBuffer = ItemRenderer.getFoilBuffer(bufferSource, renderType, modelPartSubmit.sheeted(), true);
            } else {
               actualBuffer = buffer;
            }

            this.poseStack.last().set(modelPartSubmit.pose());
            modelPartSubmit.modelPart()
               .render(this.poseStack, actualBuffer, modelPartSubmit.lightCoords(), modelPartSubmit.overlayCoords(), modelPartSubmit.tintedColor());
            if (modelPartSubmit.outlineColor() != 0 && (renderType.outline().isPresent() || renderType.isOutline())) {
               outlineBufferSource.setColor(modelPartSubmit.outlineColor());
               VertexConsumer outlineBuffer = outlineBufferSource.getBuffer(renderType);
               modelPartSubmit.modelPart()
                  .render(
                     this.poseStack,
                     modelPartSubmit.sprite() == null ? outlineBuffer : modelPartSubmit.sprite().wrap(outlineBuffer),
                     modelPartSubmit.lightCoords(),
                     modelPartSubmit.overlayCoords(),
                     modelPartSubmit.tintedColor()
                  );
            }

            if (modelPartSubmit.crumblingOverlay() != null) {
               VertexConsumer breakingBuffer = new SheetedDecalTextureGenerator(
                  crumblingBufferSource.getBuffer(ModelBakery.DESTROY_TYPES.get(modelPartSubmit.crumblingOverlay().progress())),
                  modelPartSubmit.crumblingOverlay().cameraPose(),
                  1.0F
               );
               modelPartSubmit.modelPart()
                  .render(this.poseStack, breakingBuffer, modelPartSubmit.lightCoords(), modelPartSubmit.overlayCoords(), modelPartSubmit.tintedColor());
            }
         }
      }
   }

   public static class Storage {
      private final Map<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> modelPartSubmits = new HashMap<>();
      private final Set<RenderType> modelPartSubmitsUsage = new ObjectOpenHashSet();

      public void add(final RenderType renderType, final SubmitNodeStorage.ModelPartSubmit submit) {
         this.modelPartSubmits.computeIfAbsent(renderType, ignored -> new ArrayList<>()).add(submit);
      }

      public void clear() {
         for (Entry<RenderType, List<SubmitNodeStorage.ModelPartSubmit>> entry : this.modelPartSubmits.entrySet()) {
            if (!entry.getValue().isEmpty()) {
               this.modelPartSubmitsUsage.add(entry.getKey());
               entry.getValue().clear();
            }
         }
      }

      public void endFrame() {
         this.modelPartSubmits.keySet().removeIf(renderType -> !this.modelPartSubmitsUsage.contains(renderType));
         this.modelPartSubmitsUsage.clear();
      }
   }
}
