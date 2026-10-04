package net.minecraft.client.resources.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

public class ModelManager implements PreparableReloadListener {
   public static final Identifier BLOCK_OR_ITEM = Identifier.withDefaultNamespace("block_or_item");
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
   private Map<Identifier, ItemModel> bakedItemStackModels = Map.of();
   private Map<Identifier, ClientItem.Properties> itemProperties = Map.of();
   private final AtlasManager atlasManager;
   private final PlayerSkinRenderCache playerSkinRenderCache;
   private final BlockModelShaper blockModelShaper;
   private final BlockColors blockColors;
   private EntityModelSet entityModelSet = EntityModelSet.EMPTY;
   private SpecialBlockModelRenderer specialBlockModelRenderer = SpecialBlockModelRenderer.EMPTY;
   private ModelBakery.MissingModels missingModels;
   private Object2IntMap<BlockState> modelGroups = Object2IntMaps.emptyMap();

   public ModelManager(final BlockColors blockColors, final AtlasManager atlasManager, final PlayerSkinRenderCache playerSkinRenderCache) {
      this.blockColors = blockColors;
      this.atlasManager = atlasManager;
      this.playerSkinRenderCache = playerSkinRenderCache;
      this.blockModelShaper = new BlockModelShaper(this);
   }

   public BlockStateModel getMissingBlockStateModel() {
      return this.missingModels.block();
   }

   public ItemModel getItemModel(final Identifier id) {
      return this.bakedItemStackModels.getOrDefault(id, this.missingModels.item());
   }

   public ClientItem.Properties getItemProperties(final Identifier id) {
      return this.itemProperties.getOrDefault(id, ClientItem.Properties.DEFAULT);
   }

   public BlockModelShaper getBlockModelShaper() {
      return this.blockModelShaper;
   }

   @Override
   public final CompletableFuture<Void> reload(
      final PreparableReloadListener.SharedState currentReload,
      final Executor taskExecutor,
      final PreparableReloadListener.PreparationBarrier preparationBarrier,
      final Executor reloadExecutor
   ) {
      ResourceManager manager = currentReload.resourceManager();
      CompletableFuture<EntityModelSet> entityModelSet = CompletableFuture.supplyAsync(EntityModelSet::vanilla, taskExecutor);
      CompletableFuture<SpecialBlockModelRenderer> specialBlockModelRenderer = entityModelSet.thenApplyAsync(
         entityModels -> SpecialBlockModelRenderer.vanilla(
            new SpecialModelRenderer.BakingContext.Simple(entityModels, this.atlasManager, this.playerSkinRenderCache)
         ),
         taskExecutor
      );
      CompletableFuture<Map<Identifier, UnbakedModel>> modelCache = loadBlockModels(manager, taskExecutor);
      CompletableFuture<BlockStateModelLoader.LoadedModels> blockStateModels = BlockStateModelLoader.loadBlockStates(manager, taskExecutor);
      CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> itemStackModels = ClientItemInfoLoader.scheduleLoad(manager, taskExecutor);
      CompletableFuture<ModelManager.ResolvedModels> modelDiscovery = CompletableFuture.allOf(modelCache, blockStateModels, itemStackModels)
         .thenApplyAsync(unused -> discoverModelDependencies(modelCache.join(), blockStateModels.join(), itemStackModels.join()), taskExecutor);
      CompletableFuture<Object2IntMap<BlockState>> modelGroups = blockStateModels.thenApplyAsync(
         models -> buildModelGroups(this.blockColors, models), taskExecutor
      );
      AtlasManager.PendingStitchResults pendingStitches = currentReload.get(AtlasManager.PENDING_STITCH);
      CompletableFuture<SpriteLoader.Preparations> pendingBlockAtlasSprites = pendingStitches.get(AtlasIds.BLOCKS);
      CompletableFuture<SpriteLoader.Preparations> pendingItemAtlasSprites = pendingStitches.get(AtlasIds.ITEMS);
      return CompletableFuture.allOf(
            pendingBlockAtlasSprites,
            pendingItemAtlasSprites,
            modelDiscovery,
            modelGroups,
            blockStateModels,
            itemStackModels,
            entityModelSet,
            specialBlockModelRenderer,
            modelCache
         )
         .thenComposeAsync(
            unused -> {
               SpriteLoader.Preparations blockAtlasSprites = pendingBlockAtlasSprites.join();
               SpriteLoader.Preparations itemAtlasSprites = pendingItemAtlasSprites.join();
               ModelManager.ResolvedModels resolvedModels = modelDiscovery.join();
               Object2IntMap<BlockState> groups = modelGroups.join();
               Set<Identifier> unreferencedModels = Sets.difference(modelCache.join().keySet(), resolvedModels.models.keySet());
               if (!unreferencedModels.isEmpty()) {
                  LOGGER.debug(
                     "Unreferenced models: \n{}", unreferencedModels.stream().sorted().map(modelId -> "\t" + modelId + "\n").collect(Collectors.joining())
                  );
               }

               ModelBakery bakery = new ModelBakery(
                  entityModelSet.join(),
                  this.atlasManager,
                  this.playerSkinRenderCache,
                  blockStateModels.join().models(),
                  itemStackModels.join().contents(),
                  resolvedModels.models(),
                  resolvedModels.missing()
               );
               return loadModels(blockAtlasSprites, itemAtlasSprites, bakery, groups, entityModelSet.join(), specialBlockModelRenderer.join(), taskExecutor);
            },
            taskExecutor
         )
         .thenCompose(preparationBarrier::wait)
         .thenAcceptAsync(this::apply, reloadExecutor);
   }

   private static CompletableFuture<Map<Identifier, UnbakedModel>> loadBlockModels(final ResourceManager manager, final Executor executor) {
      return CompletableFuture.<Map<Identifier, Resource>>supplyAsync(() -> MODEL_LISTER.listMatchingResources(manager), executor)
         .thenCompose(
            resources -> {
               List<CompletableFuture<Pair<Identifier, BlockModel>>> result = new ArrayList<>(resources.size());

               for (Entry<Identifier, Resource> resource : resources.entrySet()) {
                  result.add(CompletableFuture.supplyAsync(() -> {
                     Identifier modelId = MODEL_LISTER.fileToId(resource.getKey());

                     try (Reader reader = resource.getValue().openAsReader()) {
                        return Pair.of(modelId, BlockModel.fromStream(reader));
                     } catch (Exception e) {
                        LOGGER.error("Failed to load model {}", resource.getKey(), e);
                        return null;
                     }
                  }, executor));
               }

               return Util.sequence(result)
                  .thenApply(pairs -> pairs.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond)));
            }
         );
   }

   private static ModelManager.ResolvedModels discoverModelDependencies(
      final Map<Identifier, UnbakedModel> allModels,
      final BlockStateModelLoader.LoadedModels blockStateModels,
      final ClientItemInfoLoader.LoadedClientInfos itemInfos
   ) {
      try (Zone ignored = Profiler.get().zone("dependencies")) {
         ModelDiscovery result = new ModelDiscovery(allModels, MissingBlockModel.missingModel());
         result.addSpecialModel(ItemModelGenerator.GENERATED_ITEM_MODEL_ID, new ItemModelGenerator());
         blockStateModels.models().values().forEach(result::addRoot);
         itemInfos.contents().values().forEach(info -> result.addRoot(info.model()));
         return new ModelManager.ResolvedModels(result.missingModel(), result.resolve());
      }
   }

   private static CompletableFuture<ModelManager.ReloadState> loadModels(
      final SpriteLoader.Preparations blockAtlas,
      final SpriteLoader.Preparations itemAtlas,
      final ModelBakery bakery,
      final Object2IntMap<BlockState> modelGroups,
      final EntityModelSet entityModelSet,
      final SpecialBlockModelRenderer specialBlockModelRenderer,
      final Executor taskExecutor
   ) {
      final Multimap<String, Material> missingMaterials = Multimaps.synchronizedMultimap(HashMultimap.create());
      final Multimap<String, String> missingReferences = Multimaps.synchronizedMultimap(HashMultimap.create());
      return bakery.bakeModels(new SpriteGetter() {
            private final TextureAtlasSprite blockMissing = blockAtlas.missing();
            private final TextureAtlasSprite itemMissing = itemAtlas.missing();

            @Override
            public TextureAtlasSprite get(final Material material, final ModelDebugName name) {
               Identifier atlasId = material.atlasLocation();
               boolean itemOrBlock = atlasId.equals(ModelManager.BLOCK_OR_ITEM);
               boolean onlyItem = atlasId.equals(TextureAtlas.LOCATION_ITEMS);
               boolean onlyBlock = atlasId.equals(TextureAtlas.LOCATION_BLOCKS);
               if (itemOrBlock || onlyItem) {
                  TextureAtlasSprite result = itemAtlas.getSprite(material.texture());
                  if (result != null) {
                     return result;
                  }
               }

               if (itemOrBlock || onlyBlock) {
                  TextureAtlasSprite result = blockAtlas.getSprite(material.texture());
                  if (result != null) {
                     return result;
                  }
               }

               missingMaterials.put(name.debugName(), material);
               return onlyItem ? this.itemMissing : this.blockMissing;
            }

            @Override
            public TextureAtlasSprite reportMissingReference(final String reference, final ModelDebugName responsibleModel) {
               missingReferences.put(responsibleModel.debugName(), reference);
               return this.blockMissing;
            }
         }, taskExecutor)
         .thenApply(
            bakingResult -> {
               missingMaterials.asMap()
                  .forEach(
                     (location, materials) -> LOGGER.warn(
                        "Missing textures in model {}:\n{}",
                        location,
                        materials.stream()
                           .sorted(Material.COMPARATOR)
                           .map(m -> "    " + m.atlasLocation() + ":" + m.texture())
                           .collect(Collectors.joining("\n"))
                     )
                  );
               missingReferences.asMap()
                  .forEach(
                     (location, references) -> LOGGER.warn(
                        "Missing texture references in model {}:\n{}",
                        location,
                        references.stream().sorted().map(reference -> "    " + reference).collect(Collectors.joining("\n"))
                     )
                  );
               Map<BlockState, BlockStateModel> modelByStateCache = createBlockStateToModelDispatch(
                  bakingResult.blockStateModels(), bakingResult.missingModels().block()
               );
               return new ModelManager.ReloadState(bakingResult, modelGroups, modelByStateCache, entityModelSet, specialBlockModelRenderer);
            }
         );
   }

   private static Map<BlockState, BlockStateModel> createBlockStateToModelDispatch(
      final Map<BlockState, BlockStateModel> bakedModels, final BlockStateModel missingModel
   ) {
      try (Zone ignored = Profiler.get().zone("block state dispatch")) {
         Map<BlockState, BlockStateModel> modelByStateCache = new IdentityHashMap<>(bakedModels);

         for (Block block : BuiltInRegistries.BLOCK) {
            block.getStateDefinition().getPossibleStates().forEach(state -> {
               if (bakedModels.putIfAbsent(state, missingModel) == null) {
                  LOGGER.warn("Missing model for variant: '{}'", state);
               }
            });
         }

         return modelByStateCache;
      }
   }

   private static Object2IntMap<BlockState> buildModelGroups(final BlockColors blockColors, final BlockStateModelLoader.LoadedModels blockStateModels) {
      try (Zone ignored = Profiler.get().zone("block groups")) {
         return ModelGroupCollector.build(blockColors, blockStateModels);
      }
   }

   private void apply(final ModelManager.ReloadState preparations) {
      ModelBakery.BakingResult bakedModels = preparations.bakedModels;
      this.bakedItemStackModels = bakedModels.itemStackModels();
      this.itemProperties = bakedModels.itemProperties();
      this.modelGroups = preparations.modelGroups;
      this.missingModels = bakedModels.missingModels();
      this.blockModelShaper.replaceCache(preparations.modelCache);
      this.specialBlockModelRenderer = preparations.specialBlockModelRenderer;
      this.entityModelSet = preparations.entityModelSet;
   }

   public boolean requiresRender(final BlockState oldState, final BlockState newState) {
      if (oldState == newState) {
         return false;
      }

      int oldModelGroup = this.modelGroups.getInt(oldState);
      if (oldModelGroup != -1) {
         int newModelGroup = this.modelGroups.getInt(newState);
         if (oldModelGroup == newModelGroup) {
            FluidState oldFluidState = oldState.getFluidState();
            FluidState newFluidState = newState.getFluidState();
            return oldFluidState != newFluidState;
         }
      }

      return true;
   }

   public SpecialBlockModelRenderer specialBlockModelRenderer() {
      return this.specialBlockModelRenderer;
   }

   public Supplier<EntityModelSet> entityModels() {
      return () -> this.entityModelSet;
   }

   private record ReloadState(
      ModelBakery.BakingResult bakedModels,
      Object2IntMap<BlockState> modelGroups,
      Map<BlockState, BlockStateModel> modelCache,
      EntityModelSet entityModelSet,
      SpecialBlockModelRenderer specialBlockModelRenderer
   ) {
   }

   private record ResolvedModels(ResolvedModel missing, Map<Identifier, ResolvedModel> models) {
   }
}
