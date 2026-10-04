package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class MultiPartModel implements BlockStateModel {
   private final MultiPartModel.SharedBakedState shared;
   private final BlockState blockState;
   private @Nullable List<BlockStateModel> models;

   private MultiPartModel(final MultiPartModel.SharedBakedState shared, final BlockState blockState) {
      this.shared = shared;
      this.blockState = blockState;
   }

   @Override
   public TextureAtlasSprite particleIcon() {
      return this.shared.particleIcon;
   }

   @Override
   public void collectParts(final RandomSource random, final List<BlockModelPart> output) {
      if (this.models == null) {
         this.models = this.shared.selectModels(this.blockState);
      }

      long seed = random.nextLong();

      for (BlockStateModel model : this.models) {
         random.setSeed(seed);
         model.collectParts(random, output);
      }
   }

   public record Selector<T>(Predicate<BlockState> condition, T model) {
      public <S> MultiPartModel.Selector<S> with(final S newModel) {
         return new MultiPartModel.Selector<>(this.condition, newModel);
      }
   }

   private static final class SharedBakedState {
      private final List<MultiPartModel.Selector<BlockStateModel>> selectors;
      private final TextureAtlasSprite particleIcon;
      private final Map<BitSet, List<BlockStateModel>> subsets = new ConcurrentHashMap<>();

      private static BlockStateModel getFirstModel(final List<MultiPartModel.Selector<BlockStateModel>> selectors) {
         if (selectors.isEmpty()) {
            throw new IllegalArgumentException("Model must have at least one selector");
         } else {
            return selectors.getFirst().model();
         }
      }

      public SharedBakedState(final List<MultiPartModel.Selector<BlockStateModel>> selectors) {
         this.selectors = selectors;
         BlockStateModel firstModel = getFirstModel(selectors);
         this.particleIcon = firstModel.particleIcon();
      }

      public List<BlockStateModel> selectModels(final BlockState state) {
         BitSet selectedModels = new BitSet();

         for (int i = 0; i < this.selectors.size(); i++) {
            if (this.selectors.get(i).condition.test(state)) {
               selectedModels.set(i);
            }
         }

         return this.subsets.computeIfAbsent(selectedModels, selected -> {
            Builder<BlockStateModel> result = ImmutableList.builder();

            for (int ix = 0; ix < this.selectors.size(); ix++) {
               if (selected.get(ix)) {
                  result.add(this.selectors.get(ix).model);
               }
            }

            return result.build();
         });
      }
   }

   public static class Unbaked implements BlockStateModel.UnbakedRoot {
      private final List<MultiPartModel.Selector<BlockStateModel.Unbaked>> selectors;
      private final ModelBaker.SharedOperationKey<MultiPartModel.SharedBakedState> sharedStateKey = new ModelBaker.SharedOperationKey<MultiPartModel.SharedBakedState>(
         
      ) {
         public MultiPartModel.SharedBakedState compute(final ModelBaker modelBakery) {
            Builder<MultiPartModel.Selector<BlockStateModel>> selectors = ImmutableList.builderWithExpectedSize(Unbaked.this.selectors.size());

            for (MultiPartModel.Selector<BlockStateModel.Unbaked> selector : Unbaked.this.selectors) {
               selectors.add(selector.with(selector.model.bake(modelBakery)));
            }

            return new MultiPartModel.SharedBakedState(selectors.build());
         }
      };

      public Unbaked(final List<MultiPartModel.Selector<BlockStateModel.Unbaked>> selectors) {
         this.selectors = selectors;
      }

      @Override
      public Object visualEqualityGroup(final BlockState blockState) {
         IntList triggeredSelectors = new IntArrayList();

         for (int i = 0; i < this.selectors.size(); i++) {
            if (this.selectors.get(i).condition.test(blockState)) {
               triggeredSelectors.add(i);
            }
         }

         record Key(MultiPartModel.Unbaked model, IntList selectors) {
         }

         return new Key(this, triggeredSelectors);
      }

      @Override
      public void resolveDependencies(final ResolvableModel.Resolver resolver) {
         this.selectors.forEach(s -> s.model.resolveDependencies(resolver));
      }

      @Override
      public BlockStateModel bake(final BlockState blockState, final ModelBaker modelBakery) {
         MultiPartModel.SharedBakedState shared = modelBakery.compute(this.sharedStateKey);
         return new MultiPartModel(shared, blockState);
      }
   }
}
