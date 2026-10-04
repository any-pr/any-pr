package net.minecraft.network.chat.contents.data;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public record BlockDataSource(String posPattern, @Nullable Coordinates compiledPos) implements DataSource {
   public static final MapCodec<BlockDataSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
      i -> i.group(Codec.STRING.fieldOf("block").forGetter(BlockDataSource::posPattern)).apply(i, BlockDataSource::new)
   );

   public BlockDataSource(final String pos) {
      this(pos, compilePos(pos));
   }

   private static @Nullable Coordinates compilePos(final String pos) {
      try {
         return BlockPosArgument.blockPos().parse(new StringReader(pos));
      } catch (CommandSyntaxException e) {
         return null;
      }
   }

   @Override
   public Stream<CompoundTag> getData(final CommandSourceStack sender) {
      if (this.compiledPos != null) {
         ServerLevel level = sender.getLevel();
         BlockPos pos = this.compiledPos.getBlockPos(sender);
         if (level.isLoaded(pos)) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
               return Stream.of(entity.saveWithFullMetadata(sender.registryAccess()));
            }
         }
      }

      return Stream.empty();
   }

   @Override
   public MapCodec<BlockDataSource> codec() {
      return MAP_CODEC;
   }

   @Override
   public String toString() {
      return "block=" + this.posPattern;
   }

   @Override
   public boolean equals(final Object o) {
      return this == o ? true : o instanceof BlockDataSource that && this.posPattern.equals(that.posPattern);
   }

   @Override
   public int hashCode() {
      return this.posPattern.hashCode();
   }
}
