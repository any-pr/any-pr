package net.minecraft.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public class LegacyStuffWrapper {
   @Deprecated
   public static int[] getPixels(final ResourceManager resourceManager, final Identifier location) throws IOException {
      int[] var4;
      try (InputStream resource = resourceManager.open(location)) {
         NativeImage image = NativeImage.read(resource);

         try {
            var4 = image.makePixelArray();
         } catch (Throwable var8) {
            if (image != null) {
               try {
                  image.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }
            }

            throw var8;
         }

         if (image != null) {
            image.close();
         }
      }

      return var4;
   }
}
