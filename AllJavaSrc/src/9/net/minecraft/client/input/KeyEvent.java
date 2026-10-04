package net.minecraft.client.input;

import com.mojang.blaze3d.platform.InputConstants.Value;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public record KeyEvent(@Value int key, int scancode, @InputWithModifiers.Modifiers int modifiers) implements InputWithModifiers {
   @Override
   public int input() {
      return this.key;
   }

   @Retention(RetentionPolicy.CLASS)
   @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.TYPE_USE})
   public @interface Action {
   }
}
