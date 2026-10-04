package net.minecraft.client.gui.components.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


@OnlyIn(Dist.CLIENT)
public class DebugEntryEntityRenderStats implements DebugScreenEntry {
   @Override
   public void display(
      final DebugScreenDisplayer displayer,
      final @Nullable Level serverOrClientLevel,
      final @Nullable LevelChunk clientChunk,
      final @Nullable LevelChunk serverChunk
   ) {
      ClientLevel clientLevel = Minecraft.getInstance().level;
      if (clientLevel != null) {
         LevelExtractor levelExtractor = Minecraft.getInstance().levelExtractor;
         // 🔧 26.2 API：entityStatistics() 返回 String（26.4 的 getRenderedEntityCount/getTotalEntityCount 26.2 无）
         String stats = Minecraft.getInstance().levelExtractor.entityStatistics();
         if (stats != null) {
            displayer.addToGroup(DebugGroups.MISC, stats);
         }
         displayer.addFactToGroup(DebugGroups.MISC, "Simulation Distance", fact -> fact.value(clientLevel.getServerSimulationDistance()));
      }
   }

   @Override
   public boolean isAllowed(final boolean reducedDebugInfo) {
      return true;
   }
}
