package net.minecraft.client.gui.components.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public class DebugEntryLookingAtFluid implements DebugScreenEntry {
   private static final Identifier GROUP = Identifier.withDefaultNamespace("looking_at_fluid");

   @Override
   public void display(
      final DebugScreenDisplayer displayer,
      final @Nullable Level serverOrClientLevel,
      final @Nullable LevelChunk clientChunk,
      final @Nullable LevelChunk serverChunk
   ) {
      Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
      Level clientOrServerLevel = SharedConstants.DEBUG_SHOW_SERVER_DEBUG_VALUES ? serverOrClientLevel : Minecraft.getInstance().level;
      if (cameraEntity != null && clientOrServerLevel != null) {
         HitResult liquid = cameraEntity.pick(20.0, 0.0F, true);
         List<String> result = new ArrayList<>();
         if (liquid.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult)liquid).getBlockPos();
            FluidState fluidState = clientOrServerLevel.getFluidState(pos);
            result.add(ChatFormatting.UNDERLINE + "Targeted Fluid: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
            result.add(String.valueOf(BuiltInRegistries.FLUID.getKey(fluidState.getType())));

            for (Entry<Property<?>, Comparable<?>> entry : fluidState.getValues().entrySet()) {
               result.add(this.getPropertyValueString(entry));
            }

            fluidState.getTags().map(e -> "#" + e.location()).forEach(result::add);
         }

         displayer.addToGroup(GROUP, result);
      }
   }

   private String getPropertyValueString(final Entry<Property<?>, Comparable<?>> entry) {
      Property<?> property = entry.getKey();
      Comparable<?> value = entry.getValue();
      String valueString = Util.getPropertyName(property, value);
      if (Boolean.TRUE.equals(value)) {
         valueString = ChatFormatting.GREEN + valueString;
      } else if (Boolean.FALSE.equals(value)) {
         valueString = ChatFormatting.RED + valueString;
      }

      return property.getName() + ": " + valueString;
   }
}
