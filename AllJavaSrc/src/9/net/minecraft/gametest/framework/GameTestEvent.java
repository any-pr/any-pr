package net.minecraft.gametest.framework;

import org.jspecify.annotations.Nullable;

class GameTestEvent {
   public final @Nullable Long expectedDelay;
   public final Runnable assertion;

   private GameTestEvent(final @Nullable Long expectedDelay, final Runnable assertion) {
      this.expectedDelay = expectedDelay;
      this.assertion = assertion;
   }

   static GameTestEvent create(final Runnable runnable) {
      return new GameTestEvent(null, runnable);
   }

   static GameTestEvent create(final long expectedTick, final Runnable runnable) {
      return new GameTestEvent(expectedTick, runnable);
   }
}
