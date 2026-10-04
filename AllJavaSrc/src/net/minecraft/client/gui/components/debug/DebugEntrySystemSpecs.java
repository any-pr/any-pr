package net.minecraft.client.gui.components.debug;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceType;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


@OnlyIn(Dist.CLIENT)
public class DebugEntrySystemSpecs implements DebugScreenEntry {

   public static String getCpuInfo() {
      // 🔧 MCRe：委托 GLX._getCpuInfo()（含 Android 回退链 SOC_MODEL→HARDWARE→核数兜底）。
      // 修复前本方法是旧 GLX 代码的副本且 catch 块为空 → oshi 在 Android 上读 /sys 被 SELinux
      // 拒绝（AccessDeniedException）→ 恒 <unknown>。GLX 自带 cpuInfo 缓存，零开销。
      return com.mojang.blaze3d.platform.GLX._getCpuInfo();
   }

   @Override
   public void display(
      final DebugScreenDisplayer displayer,
      final @Nullable Level serverOrClientLevel,
      final @Nullable LevelChunk clientChunk,
      final @Nullable LevelChunk serverChunk
   ) {
      DeviceInfo deviceInfo = RenderSystem.getDevice().getDeviceInfo();
      Window window = Minecraft.getInstance().getWindow();
      displayer.addFactToGroup(DebugGroups.SYSTEM_SPECS, "Java", fact -> fact.value(System.getProperty("java.version")));
      displayer.addFactToGroup(DebugGroups.SYSTEM_SPECS, "CPU", fact -> fact.value(getCpuInfo()));
      displayer.addFactToGroup(
         DebugGroups.SYSTEM_SPECS,
         "Display",
         fact -> fact.value(window.getWidth()).text("x").value(window.getHeight()).text(" (").value(deviceInfo.vendorName()).text(")")
      );
      displayer.addFactToGroup(
         DebugGroups.SYSTEM_SPECS,
         "Window",
         fact -> fact.value(window.getScreenWidth())
            .text("x")
            .value(window.getScreenHeight())
            .text(" (")
            .formattedValue("%.2f", (double) window.getGuiScale())
            .text("x pixel density)")
      );
      displayer.addToGroup(
         DebugGroups.SYSTEM_SPECS,
         List.of(
            String.format(Locale.ROOT, "%s%s", deviceInfo.name(), this.typeName(deviceInfo.type())),
            String.format(Locale.ROOT, "%s %s", deviceInfo.backendName(), this.firstLine(deviceInfo.driverInfo()))
         )
      );
   }

   private String firstLine(final String value) {
      return value.lines().findFirst().orElse(value);
   }

   private String typeName(final DeviceType type) {
      return switch (type) {
         case OTHER -> "";
         case INTEGRATED -> " (iGPU)";
         case DISCRETE -> " (dGPU)";
         case VIRTUAL -> " (vGPU)";
         case CPU -> " (software)";
      };
   }

   @Override
   public boolean isAllowed(final boolean reducedDebugInfo) {
      return true;
   }
}
