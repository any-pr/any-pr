package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Brightness(int block, int sky) {
   public static final Codec<Integer> LIGHT_VALUE_CODEC = ExtraCodecs.intRange(0, 15);
   public static final Codec<Brightness> CODEC = RecordCodecBuilder.create(
      i -> i.group(LIGHT_VALUE_CODEC.fieldOf("block").forGetter(Brightness::block), LIGHT_VALUE_CODEC.fieldOf("sky").forGetter(Brightness::sky))
         .apply(i, Brightness::new)
   );
   public static final Brightness FULL_BRIGHT = new Brightness(15, 15);

   public static int pack(final int block, final int sky) {
      return block << 4 | sky << 20;
   }

   public int pack() {
      return pack(this.block, this.sky);
   }

   public static int block(final int packed) {
      return packed >> 4 & 65535;
   }

   public static int sky(final int packed) {
      return packed >> 20 & 65535;
   }

   public static Brightness unpack(final int packed) {
      return new Brightness(block(packed), sky(packed));
   }
}
