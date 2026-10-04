package net.minecraft.client.gui.components.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.Locale;


@OnlyIn(Dist.CLIENT)
public class DebugEntrySoundMood implements DebugScreenEntry {
   @Override
   public void display(
      final DebugScreenDisplayer displayer,
      final @Nullable Level serverOrClientLevel,
      final @Nullable LevelChunk clientChunk,
      final @Nullable LevelChunk serverChunk
   ) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null) {
         // 🔧 26.2 API：getChannelDebugString()（26.4 的 fillChannelDebug(DebugFact) 26.2 无）+ Mood%
         displayer.addFactToGroup(DebugGroups.MISC, "Sounds", fact -> fact.value(
             minecraft.getSoundManager().getChannelDebugString()
                 + String.format(Locale.ROOT, " (Mood %d%%)", Math.round(minecraft.player.getCurrentMood() * 100.0F))
         ));
         displayer.addFactToGroup(DebugGroups.MISC, "Mood", fact -> fact.formattedValue("%.2f", minecraft.player.getCurrentMood() * 100.0F).text("%"));
      }
   }
}
