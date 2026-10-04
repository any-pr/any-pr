package net.minecraft.world.entity.animal.feline;

import java.util.List;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.variant.MoonBrightnessCheck;
import net.minecraft.world.entity.variant.PriorityProvider;
import net.minecraft.world.entity.variant.SpawnPrioritySelectors;
import net.minecraft.world.entity.variant.StructureCheck;
import net.minecraft.world.level.levelgen.structure.Structure;

public interface CatVariants {
   ResourceKey<CatVariant> TABBY = createKey("tabby");
   ResourceKey<CatVariant> BLACK = createKey("black");
   ResourceKey<CatVariant> RED = createKey("red");
   ResourceKey<CatVariant> SIAMESE = createKey("siamese");
   ResourceKey<CatVariant> BRITISH_SHORTHAIR = createKey("british_shorthair");
   ResourceKey<CatVariant> CALICO = createKey("calico");
   ResourceKey<CatVariant> PERSIAN = createKey("persian");
   ResourceKey<CatVariant> RAGDOLL = createKey("ragdoll");
   ResourceKey<CatVariant> WHITE = createKey("white");
   ResourceKey<CatVariant> JELLIE = createKey("jellie");
   ResourceKey<CatVariant> ALL_BLACK = createKey("all_black");

   private static ResourceKey<CatVariant> createKey(final String name) {
      return ResourceKey.create(Registries.CAT_VARIANT, Identifier.withDefaultNamespace(name));
   }

   static void bootstrap(final BootstrapContext<CatVariant> context) {
      HolderGetter<Structure> structures = context.lookup(Registries.STRUCTURE);
      registerForAnyConditions(context, TABBY, "entity/cat/tabby");
      registerForAnyConditions(context, BLACK, "entity/cat/black");
      registerForAnyConditions(context, RED, "entity/cat/red");
      registerForAnyConditions(context, SIAMESE, "entity/cat/siamese");
      registerForAnyConditions(context, BRITISH_SHORTHAIR, "entity/cat/british_shorthair");
      registerForAnyConditions(context, CALICO, "entity/cat/calico");
      registerForAnyConditions(context, PERSIAN, "entity/cat/persian");
      registerForAnyConditions(context, RAGDOLL, "entity/cat/ragdoll");
      registerForAnyConditions(context, WHITE, "entity/cat/white");
      registerForAnyConditions(context, JELLIE, "entity/cat/jellie");
      register(
         context,
         ALL_BLACK,
         "entity/cat/all_black",
         new SpawnPrioritySelectors(
            List.of(
               new PriorityProvider.Selector<>(new StructureCheck(structures.getOrThrow(StructureTags.CATS_SPAWN_AS_BLACK)), 1),
               new PriorityProvider.Selector<>(new MoonBrightnessCheck(MinMaxBounds.Doubles.atLeast(0.9)), 0)
            )
         )
      );
   }

   private static void registerForAnyConditions(final BootstrapContext<CatVariant> context, final ResourceKey<CatVariant> name, final String texture) {
      register(context, name, texture, SpawnPrioritySelectors.fallback(0));
   }

   private static void register(
      final BootstrapContext<CatVariant> context, final ResourceKey<CatVariant> name, final String texture, final SpawnPrioritySelectors spawnConditions
   ) {
      context.register(name, new CatVariant(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(texture)), spawnConditions));
   }
}
