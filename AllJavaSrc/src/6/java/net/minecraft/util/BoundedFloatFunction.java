package net.minecraft.util;

import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import java.util.function.Function;

public interface BoundedFloatFunction<C> {
   BoundedFloatFunction<Float> IDENTITY = createUnlimited(input -> input);

   float apply(final C c);

   float minValue();

   float maxValue();

   static BoundedFloatFunction<Float> createUnlimited(final Float2FloatFunction function) {
      return new BoundedFloatFunction<Float>() {
         public float apply(final Float aFloat) {
            return (Float)function.apply(aFloat);
         }

         @Override
         public float minValue() {
            return Float.NEGATIVE_INFINITY;
         }

         @Override
         public float maxValue() {
            return Float.POSITIVE_INFINITY;
         }
      };
   }

   default <C2> BoundedFloatFunction<C2> comap(final Function<C2, C> function) {
      final BoundedFloatFunction<C> outer = this;
      return new BoundedFloatFunction<C2>() {
         @Override
         public float apply(final C2 c2) {
            return outer.apply(function.apply(c2));
         }

         @Override
         public float minValue() {
            return outer.minValue();
         }

         @Override
         public float maxValue() {
            return outer.maxValue();
         }
      };
   }
}
