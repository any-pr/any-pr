package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;

public interface DensityFunction {
   Codec<DensityFunction> DIRECT_CODEC = DensityFunctions.DIRECT_CODEC;
   Codec<Holder<DensityFunction>> CODEC = RegistryFileCodec.create(Registries.DENSITY_FUNCTION, DIRECT_CODEC);
   Codec<DensityFunction> HOLDER_HELPER_CODEC = CODEC.xmap(
      DensityFunctions.HolderHolder::new, value -> value instanceof DensityFunctions.HolderHolder holder ? holder.function() : new Holder.Direct<>(value)
   );

   double compute(final DensityFunction.FunctionContext context);

   void fillArray(final double[] output, final DensityFunction.ContextProvider contextProvider);

   DensityFunction mapAll(final DensityFunction.Visitor visitor);

   double minValue();

   double maxValue();

   KeyDispatchDataCodec<? extends DensityFunction> codec();

   default DensityFunction clamp(final double min, final double max) {
      return new DensityFunctions.Clamp(this, min, max);
   }

   default DensityFunction abs() {
      return DensityFunctions.map(this, DensityFunctions.Mapped.Type.ABS);
   }

   default DensityFunction square() {
      return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUARE);
   }

   default DensityFunction cube() {
      return DensityFunctions.map(this, DensityFunctions.Mapped.Type.CUBE);
   }

   default DensityFunction halfNegative() {
      return DensityFunctions.map(this, DensityFunctions.Mapped.Type.HALF_NEGATIVE);
   }

   default DensityFunction quarterNegative() {
      return DensityFunctions.map(this, DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
   }

   default DensityFunction invert() {
      return DensityFunctions.map(this, DensityFunctions.Mapped.Type.INVERT);
   }

   default DensityFunction squeeze() {
      return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUEEZE);
   }

   interface ContextProvider {
      DensityFunction.FunctionContext forIndex(int index);

      void fillAllDirectly(double[] output, DensityFunction function);
   }

   interface FunctionContext {
      int blockX();

      int blockY();

      int blockZ();

      default Blender getBlender() {
         return Blender.empty();
      }
   }

   record NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData, @Nullable NormalNoise noise) {
      public static final Codec<DensityFunction.NoiseHolder> CODEC = NormalNoise.NoiseParameters.CODEC
         .xmap(data -> new DensityFunction.NoiseHolder(data, null), DensityFunction.NoiseHolder::noiseData);

      public NoiseHolder(final Holder<NormalNoise.NoiseParameters> noiseData) {
         this(noiseData, null);
      }

      public double getValue(final double x, final double y, final double z) {
         return this.noise == null ? 0.0 : this.noise.getValue(x, y, z);
      }

      public double maxValue() {
         return this.noise == null ? 2.0 : this.noise.maxValue();
      }
   }

   interface SimpleFunction extends DensityFunction {
      @Override
      default void fillArray(final double[] output, final DensityFunction.ContextProvider contextProvider) {
         contextProvider.fillAllDirectly(output, this);
      }

      @Override
      default DensityFunction mapAll(final DensityFunction.Visitor visitor) {
         return visitor.apply(this);
      }
   }

   record SinglePointContext(int blockX, int blockY, int blockZ) implements DensityFunction.FunctionContext {
   }

   interface Visitor {
      DensityFunction apply(DensityFunction input);

      default DensityFunction.NoiseHolder visitNoise(final DensityFunction.NoiseHolder noise) {
         return noise;
      }
   }
}
