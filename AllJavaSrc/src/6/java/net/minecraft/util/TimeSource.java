package net.minecraft.util;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@FunctionalInterface
public interface TimeSource {
   long get(TimeUnit timeUnit);

   interface NanoTimeSource extends LongSupplier, TimeSource {
      @Override
      default long get(final TimeUnit timeUnit) {
         return timeUnit.convert(this.getAsLong(), TimeUnit.NANOSECONDS);
      }
   }
}
