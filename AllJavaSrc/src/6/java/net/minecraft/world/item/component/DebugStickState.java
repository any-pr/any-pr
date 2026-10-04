package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public record DebugStickState(Map<Holder<Block>, Property<?>> properties) {
   public static final DebugStickState EMPTY = new DebugStickState(Map.of());
   
   @SuppressWarnings("unchecked")
   public static final Codec<DebugStickState> CODEC = Codec.dispatchedMap(
         BuiltInRegistries.BLOCK.holderByNameCodec(), block -> Codec.STRING.comapFlatMap(name -> {
            Property<?> property = ((Block)block.value()).getStateDefinition().getProperty(name);
            return property != null
               ? DataResult.success(property)
               : DataResult.error(() -> "No property on " + block.getRegisteredName() + " with name: " + name);
         }, Property::getName)
      )
      // ===== 修改：使用显式 lambda 代替所有方法引用 =====
      .xmap(map -> new DebugStickState(map), state -> state.properties());

   public DebugStickState withProperty(final Holder<Block> block, final Property<?> property) {
      return new DebugStickState(Util.copyAndPut(this.properties, block, property));
   }
}