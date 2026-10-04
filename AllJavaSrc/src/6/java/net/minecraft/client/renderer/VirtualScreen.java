package net.minecraft.client.renderer;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class VirtualScreen implements AutoCloseable {
   private final Minecraft minecraft;
   private final ScreenManager screenManager;

   public VirtualScreen(final Minecraft minecraft) {
      this.minecraft = minecraft;
      this.screenManager = new ScreenManager(Monitor::new);
   }

   public Window newWindow(final DisplayData displayData, final @Nullable String fullscreenVideoModeString, final String title) {
      return new Window(this.minecraft, this.screenManager, displayData, fullscreenVideoModeString, title);
   }

   @Override
   public void close() {
      this.screenManager.shutdown();
   }
}
