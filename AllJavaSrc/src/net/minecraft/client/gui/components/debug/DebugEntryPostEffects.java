package net.minecraft.client.gui.components.debug;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


@OnlyIn(Dist.CLIENT)
public class DebugEntryPostEffects implements DebugScreenEntry {
   @Override
   public void display(
      final DebugScreenDisplayer displayer,
      final @Nullable Level serverOrClientLevel,
      final @Nullable LevelChunk clientChunk,
      final @Nullable LevelChunk serverChunk
   ) {
      Minecraft minecraft = Minecraft.getInstance();
      // 🔧 26.2 API：currentPostEffect()（26.4 的 getAppliedPostEffects() 26.2 无）
      Identifier effectId = minecraft.gameRenderer.currentPostEffect();
      List<Identifier> effectIds = effectId != null ? java.util.List.of(effectId) : java.util.Collections.emptyList();
      if (!effectIds.isEmpty()) {
         displayer.addFactToGroup(
            DebugGroups.MISC, "Post Effects", fact -> fact.value(effectIds.stream().map(Identifier::toString).collect(Collectors.joining(", ")))
         );
      }
   }
}
