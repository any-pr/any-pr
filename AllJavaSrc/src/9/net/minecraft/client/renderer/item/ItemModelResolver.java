package net.minecraft.client.renderer.item;

import java.util.function.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class ItemModelResolver {
   private final Function<Identifier, ItemModel> modelGetter;
   private final Function<Identifier, ClientItem.Properties> clientProperties;

   public ItemModelResolver(final ModelManager modelManager) {
      this.modelGetter = modelManager::getItemModel;
      this.clientProperties = modelManager::getItemProperties;
   }

   public void updateForLiving(final ItemStackRenderState output, final ItemStack item, final ItemDisplayContext displayContext, final LivingEntity entity) {
      this.updateForTopItem(output, item, displayContext, entity.level(), entity, entity.getId() + displayContext.ordinal());
   }

   public void updateForNonLiving(final ItemStackRenderState output, final ItemStack item, final ItemDisplayContext displayContext, final Entity entity) {
      this.updateForTopItem(output, item, displayContext, entity.level(), null, entity.getId());
   }

   public void updateForTopItem(
      final ItemStackRenderState output,
      final ItemStack item,
      final ItemDisplayContext displayContext,
      final @Nullable Level level,
      final @Nullable ItemOwner owner,
      final int seed
   ) {
      output.clear();
      if (!item.isEmpty()) {
         output.displayContext = displayContext;
         this.appendItemLayers(output, item, displayContext, level, owner, seed);
      }
   }

   public void appendItemLayers(
      final ItemStackRenderState output,
      final ItemStack item,
      final ItemDisplayContext displayContext,
      final @Nullable Level level,
      final @Nullable ItemOwner owner,
      final int seed
   ) {
      Identifier modelId = item.get(DataComponents.ITEM_MODEL);
      if (modelId != null) {
         output.setOversizedInGui(this.clientProperties.apply(modelId).oversizedInGui());
         this.modelGetter.apply(modelId).update(output, item, this, displayContext, level instanceof ClientLevel clientLevel ? clientLevel : null, owner, seed);
      }
   }

   public boolean shouldPlaySwapAnimation(final ItemStack stack) {
      Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
      return modelId == null ? true : this.clientProperties.apply(modelId).handAnimationOnSwap();
   }

   public float swapAnimationScale(final ItemStack stack) {
      Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
      return modelId == null ? 1.0F : this.clientProperties.apply(modelId).swapAnimationScale();
   }
}
