package net.minecraft.world.entity.npc.villager;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.enchantment.providers.TradeRebalanceEnchantmentProviders;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;

public class VillagerTrades {
   private static final int DEFAULT_SUPPLY = 12;
   private static final int COMMON_ITEMS_SUPPLY = 16;
   private static final int UNCOMMON_ITEMS_SUPPLY = 3;
   private static final int XP_LEVEL_1_SELL = 1;
   private static final int XP_LEVEL_1_BUY = 2;
   private static final int XP_LEVEL_2_SELL = 5;
   private static final int XP_LEVEL_2_BUY = 10;
   private static final int XP_LEVEL_3_SELL = 10;
   private static final int XP_LEVEL_3_BUY = 20;
   private static final int XP_LEVEL_4_SELL = 15;
   private static final int XP_LEVEL_4_BUY = 30;
   private static final int XP_LEVEL_5_TRADE = 30;
   private static final float LOW_TIER_PRICE_MULTIPLIER = 0.05F;
   private static final float HIGH_TIER_PRICE_MULTIPLIER = 0.2F;
   public static final Map<ResourceKey<VillagerProfession>, Int2ObjectMap<VillagerTrades.ItemListing[]>> TRADES = Util.make(
      Maps.newHashMap(),
      map -> {
         map.put(
            VillagerProfession.FARMER,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.WHEAT, 20, 16, 2),
                     new VillagerTrades.EmeraldForItems(Items.POTATO, 26, 16, 2),
                     new VillagerTrades.EmeraldForItems(Items.CARROT, 22, 16, 2),
                     new VillagerTrades.EmeraldForItems(Items.BEETROOT, 15, 16, 2),
                     new VillagerTrades.ItemsForEmeralds(Items.BREAD, 1, 6, 16, 1)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Blocks.PUMPKIN, 6, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Items.PUMPKIN_PIE, 1, 4, 5),
                     new VillagerTrades.ItemsForEmeralds(Items.APPLE, 1, 4, 16, 5)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.ItemsForEmeralds(Items.COOKIE, 3, 18, 10), new VillagerTrades.EmeraldForItems(Blocks.MELON, 4, 12, 20)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.ItemsForEmeralds(Blocks.CAKE, 1, 1, 12, 15),
                     new VillagerTrades.SuspiciousStewForEmerald(MobEffects.NIGHT_VISION, 100, 15),
                     new VillagerTrades.SuspiciousStewForEmerald(MobEffects.JUMP_BOOST, 160, 15),
                     new VillagerTrades.SuspiciousStewForEmerald(MobEffects.WEAKNESS, 140, 15),
                     new VillagerTrades.SuspiciousStewForEmerald(MobEffects.BLINDNESS, 120, 15),
                     new VillagerTrades.SuspiciousStewForEmerald(MobEffects.POISON, 280, 15),
                     new VillagerTrades.SuspiciousStewForEmerald(MobEffects.SATURATION, 7, 15)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.ItemsForEmeralds(Items.GOLDEN_CARROT, 3, 3, 30),
                     new VillagerTrades.ItemsForEmeralds(Items.GLISTERING_MELON_SLICE, 4, 3, 30)
                  }
               )
            )
         );
         map.put(
            VillagerProfession.FISHERMAN,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.STRING, 20, 16, 2),
                     new VillagerTrades.EmeraldForItems(Items.COAL, 10, 16, 2),
                     new VillagerTrades.ItemsAndEmeraldsToItems(Items.COD, 6, 1, Items.COOKED_COD, 6, 16, 1, 0.05F),
                     new VillagerTrades.ItemsForEmeralds(Items.COD_BUCKET, 3, 1, 16, 1)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.COD, 15, 16, 10),
                     new VillagerTrades.ItemsAndEmeraldsToItems(Items.SALMON, 6, 1, Items.COOKED_SALMON, 6, 16, 5, 0.05F),
                     new VillagerTrades.ItemsForEmeralds(Items.CAMPFIRE, 2, 1, 5)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.SALMON, 13, 16, 20),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.FISHING_ROD, 3, 3, 10, 0.2F)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{new VillagerTrades.EmeraldForItems(Items.TROPICAL_FISH, 6, 12, 30)},
                  5,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.PUFFERFISH, 4, 12, 30),
                     new VillagerTrades.EmeraldsForVillagerTypeItem(
                        1,
                        12,
                        30,
                        ImmutableMap.builder()
                           .put(VillagerType.PLAINS, Items.OAK_BOAT)
                           .put(VillagerType.TAIGA, Items.SPRUCE_BOAT)
                           .put(VillagerType.SNOW, Items.SPRUCE_BOAT)
                           .put(VillagerType.DESERT, Items.JUNGLE_BOAT)
                           .put(VillagerType.JUNGLE, Items.JUNGLE_BOAT)
                           .put(VillagerType.SAVANNA, Items.ACACIA_BOAT)
                           .put(VillagerType.SWAMP, Items.DARK_OAK_BOAT)
                           .build()
                     )
                  }
               )
            )
         );
         map.put(
            VillagerProfession.SHEPHERD,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Blocks.WHITE_WOOL, 18, 16, 2),
                     new VillagerTrades.EmeraldForItems(Blocks.BROWN_WOOL, 18, 16, 2),
                     new VillagerTrades.EmeraldForItems(Blocks.BLACK_WOOL, 18, 16, 2),
                     new VillagerTrades.EmeraldForItems(Blocks.GRAY_WOOL, 18, 16, 2),
                     new VillagerTrades.ItemsForEmeralds(Items.SHEARS, 2, 1, 1)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.WHITE_DYE, 12, 16, 10),
                     new VillagerTrades.EmeraldForItems(Items.GRAY_DYE, 12, 16, 10),
                     new VillagerTrades.EmeraldForItems(Items.BLACK_DYE, 12, 16, 10),
                     new VillagerTrades.EmeraldForItems(Items.LIGHT_BLUE_DYE, 12, 16, 10),
                     new VillagerTrades.EmeraldForItems(Items.LIME_DYE, 12, 16, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIME_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PINK_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.RED_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_WOOL, 1, 1, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIME_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PINK_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.RED_CARPET, 1, 4, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_CARPET, 1, 4, 16, 5)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.YELLOW_DYE, 12, 16, 20),
                     new VillagerTrades.EmeraldForItems(Items.LIGHT_GRAY_DYE, 12, 16, 20),
                     new VillagerTrades.EmeraldForItems(Items.ORANGE_DYE, 12, 16, 20),
                     new VillagerTrades.EmeraldForItems(Items.RED_DYE, 12, 16, 20),
                     new VillagerTrades.EmeraldForItems(Items.PINK_DYE, 12, 16, 20),
                     new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.RED_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIME_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PINK_BED, 3, 1, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_BED, 3, 1, 12, 10)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.BROWN_DYE, 12, 16, 30),
                     new VillagerTrades.EmeraldForItems(Items.PURPLE_DYE, 12, 16, 30),
                     new VillagerTrades.EmeraldForItems(Items.BLUE_DYE, 12, 16, 30),
                     new VillagerTrades.EmeraldForItems(Items.GREEN_DYE, 12, 16, 30),
                     new VillagerTrades.EmeraldForItems(Items.MAGENTA_DYE, 12, 16, 30),
                     new VillagerTrades.EmeraldForItems(Items.CYAN_DYE, 12, 16, 30),
                     new VillagerTrades.ItemsForEmeralds(Items.WHITE_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.BLUE_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.LIGHT_BLUE_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.RED_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.PINK_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.GREEN_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.LIME_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.GRAY_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.BLACK_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.PURPLE_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.MAGENTA_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.CYAN_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.BROWN_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.YELLOW_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.ORANGE_BANNER, 3, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Items.LIGHT_GRAY_BANNER, 3, 1, 12, 15)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{new VillagerTrades.ItemsForEmeralds(Items.PAINTING, 2, 3, 30)}
               )
            )
         );
         map.put(
            VillagerProfession.FLETCHER,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.STICK, 32, 16, 2),
                     new VillagerTrades.ItemsForEmeralds(Items.ARROW, 1, 16, 1),
                     new VillagerTrades.ItemsAndEmeraldsToItems(Blocks.GRAVEL, 10, 1, Items.FLINT, 10, 12, 1, 0.05F)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.FLINT, 26, 12, 10), new VillagerTrades.ItemsForEmeralds(Items.BOW, 2, 1, 5)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.STRING, 14, 16, 20), new VillagerTrades.ItemsForEmeralds(Items.CROSSBOW, 3, 1, 10)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.FEATHER, 24, 16, 30), new VillagerTrades.EnchantedItemForEmeralds(Items.BOW, 2, 3, 15)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.TRIPWIRE_HOOK, 8, 12, 30),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.CROSSBOW, 3, 3, 15),
                     new VillagerTrades.TippedArrowForItemsAndEmeralds(Items.ARROW, 5, Items.TIPPED_ARROW, 5, 2, 12, 30)
                  }
               )
            )
         );
         map.put(
            VillagerProfession.LIBRARIAN,
            toIntMap(
               ImmutableMap.builder()
                  .put(
                     1,
                     new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.PAPER, 24, 16, 2),
                        new VillagerTrades.EnchantBookForEmeralds(1, EnchantmentTags.TRADEABLE),
                        new VillagerTrades.ItemsForEmeralds(Blocks.BOOKSHELF, 9, 1, 12, 1)
                     }
                  )
                  .put(
                     2,
                     new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.BOOK, 4, 12, 10),
                        new VillagerTrades.EnchantBookForEmeralds(5, EnchantmentTags.TRADEABLE),
                        new VillagerTrades.ItemsForEmeralds(Items.LANTERN, 1, 1, 5)
                     }
                  )
                  .put(
                     3,
                     new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.INK_SAC, 5, 12, 20),
                        new VillagerTrades.EnchantBookForEmeralds(10, EnchantmentTags.TRADEABLE),
                        new VillagerTrades.ItemsForEmeralds(Items.GLASS, 1, 4, 10)
                     }
                  )
                  .put(
                     4,
                     new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.WRITABLE_BOOK, 2, 12, 30),
                        new VillagerTrades.EnchantBookForEmeralds(15, EnchantmentTags.TRADEABLE),
                        new VillagerTrades.ItemsForEmeralds(Items.CLOCK, 5, 1, 15),
                        new VillagerTrades.ItemsForEmeralds(Items.COMPASS, 4, 1, 15)
                     }
                  )
                  .put(5, new VillagerTrades.ItemListing[]{new VillagerTrades.ItemsForEmeralds(Items.NAME_TAG, 20, 1, 30)})
                  .build()
            )
         );
         map.put(
            VillagerProfession.CARTOGRAPHER,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.PAPER, 24, 12, 2), new VillagerTrades.ItemsForEmeralds(Items.MAP, 7, 1, 12, 1, 0.05F)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.GLASS_PANE, 11, 12, 10),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.TreasureMapForEmeralds(
                           8, StructureTags.ON_TAIGA_VILLAGE_MAPS, "filled_map.village_taiga", MapDecorationTypes.TAIGA_VILLAGE, 12, 5
                        ),
                        VillagerType.SWAMP,
                        VillagerType.SNOW,
                        VillagerType.PLAINS
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.TreasureMapForEmeralds(
                           8, StructureTags.ON_SWAMP_EXPLORER_MAPS, "filled_map.explorer_swamp", MapDecorationTypes.SWAMP_HUT, 12, 5
                        ),
                        VillagerType.TAIGA,
                        VillagerType.SNOW,
                        VillagerType.JUNGLE
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.TreasureMapForEmeralds(
                           8, StructureTags.ON_SNOWY_VILLAGE_MAPS, "filled_map.village_snowy", MapDecorationTypes.SNOWY_VILLAGE, 12, 5
                        ),
                        VillagerType.TAIGA,
                        VillagerType.SWAMP
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.TreasureMapForEmeralds(
                           8, StructureTags.ON_SAVANNA_VILLAGE_MAPS, "filled_map.village_savanna", MapDecorationTypes.SAVANNA_VILLAGE, 12, 5
                        ),
                        VillagerType.PLAINS,
                        VillagerType.JUNGLE,
                        VillagerType.DESERT
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.TreasureMapForEmeralds(
                           8, StructureTags.ON_PLAINS_VILLAGE_MAPS, "filled_map.village_plains", MapDecorationTypes.PLAINS_VILLAGE, 12, 5
                        ),
                        VillagerType.TAIGA,
                        VillagerType.SNOW,
                        VillagerType.SAVANNA,
                        VillagerType.DESERT
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.TreasureMapForEmeralds(
                           8, StructureTags.ON_JUNGLE_EXPLORER_MAPS, "filled_map.explorer_jungle", MapDecorationTypes.JUNGLE_TEMPLE, 12, 5
                        ),
                        VillagerType.SWAMP,
                        VillagerType.SAVANNA,
                        VillagerType.DESERT
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.TreasureMapForEmeralds(
                           8, StructureTags.ON_DESERT_VILLAGE_MAPS, "filled_map.village_desert", MapDecorationTypes.DESERT_VILLAGE, 12, 5
                        ),
                        VillagerType.SAVANNA,
                        VillagerType.JUNGLE
                     )
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.COMPASS, 1, 12, 20),
                     new VillagerTrades.TreasureMapForEmeralds(
                        13, StructureTags.ON_OCEAN_EXPLORER_MAPS, "filled_map.monument", MapDecorationTypes.OCEAN_MONUMENT, 12, 10
                     ),
                     new VillagerTrades.TreasureMapForEmeralds(
                        12, StructureTags.ON_TRIAL_CHAMBERS_MAPS, "filled_map.trial_chambers", MapDecorationTypes.TRIAL_CHAMBERS, 12, 10
                     )
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.ItemsForEmeralds(Items.ITEM_FRAME, 7, 1, 12, 15, 0.05F),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.BLUE_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.SNOW, VillagerType.TAIGA
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.WHITE_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.SNOW, VillagerType.PLAINS
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.RED_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.SNOW, VillagerType.SAVANNA
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.GREEN_BANNER, 2, 1, 12, 15, 0.05F),
                        VillagerType.DESERT,
                        VillagerType.SAVANNA,
                        VillagerType.JUNGLE
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.LIME_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.DESERT, VillagerType.TAIGA
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.PURPLE_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.TAIGA, VillagerType.SWAMP
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.CYAN_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.DESERT, VillagerType.SNOW
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.YELLOW_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.PLAINS, VillagerType.JUNGLE
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.ORANGE_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.SAVANNA, VillagerType.DESERT
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.BROWN_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.PLAINS, VillagerType.JUNGLE
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.MAGENTA_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.SAVANNA
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.LIGHT_BLUE_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.SNOW, VillagerType.SWAMP
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.PINK_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.TAIGA, VillagerType.PLAINS
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.GRAY_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.DESERT
                     ),
                     VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                        new VillagerTrades.ItemsForEmeralds(Items.BLACK_BANNER, 2, 1, 12, 15, 0.05F), VillagerType.SWAMP
                     )
                  },
                  5,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.ItemsForEmeralds(Items.GLOBE_BANNER_PATTERN, 8, 1, 12, 30, 0.05F),
                     new VillagerTrades.TreasureMapForEmeralds(
                        14, StructureTags.ON_WOODLAND_EXPLORER_MAPS, "filled_map.mansion", MapDecorationTypes.WOODLAND_MANSION, 12, 30
                     )
                  }
               )
            )
         );
         map.put(
            VillagerProfession.CLERIC,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.ROTTEN_FLESH, 32, 16, 2), new VillagerTrades.ItemsForEmeralds(Items.REDSTONE, 1, 2, 1)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.GOLD_INGOT, 3, 12, 10), new VillagerTrades.ItemsForEmeralds(Items.LAPIS_LAZULI, 1, 1, 5)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.RABBIT_FOOT, 2, 12, 20), new VillagerTrades.ItemsForEmeralds(Blocks.GLOWSTONE, 4, 1, 12, 10)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.TURTLE_SCUTE, 4, 12, 30),
                     new VillagerTrades.EmeraldForItems(Items.GLASS_BOTTLE, 9, 12, 30),
                     new VillagerTrades.ItemsForEmeralds(Items.ENDER_PEARL, 5, 1, 15)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.NETHER_WART, 22, 12, 30), new VillagerTrades.ItemsForEmeralds(Items.EXPERIENCE_BOTTLE, 3, 1, 30)
                  }
               )
            )
         );
         map.put(
            VillagerProfession.ARMORER,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.COAL, 15, 16, 2),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_LEGGINGS), 7, 1, 12, 1, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_BOOTS), 4, 1, 12, 1, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_HELMET), 5, 1, 12, 1, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_CHESTPLATE), 9, 1, 12, 1, 0.2F)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.IRON_INGOT, 4, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.BELL), 36, 1, 12, 5, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.CHAINMAIL_BOOTS), 1, 1, 12, 5, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.CHAINMAIL_LEGGINGS), 3, 1, 12, 5, 0.2F)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.LAVA_BUCKET, 1, 12, 20),
                     new VillagerTrades.EmeraldForItems(Items.DIAMOND, 1, 12, 20),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.CHAINMAIL_HELMET), 1, 1, 12, 10, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.CHAINMAIL_CHESTPLATE), 4, 1, 12, 10, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.SHIELD), 5, 1, 12, 10, 0.2F)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_LEGGINGS, 14, 3, 15, 0.2F),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_BOOTS, 8, 3, 15, 0.2F)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_HELMET, 8, 3, 30, 0.2F),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_CHESTPLATE, 16, 3, 30, 0.2F)
                  }
               )
            )
         );
         map.put(
            VillagerProfession.WEAPONSMITH,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.COAL, 15, 16, 2),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_AXE), 3, 1, 12, 1, 0.2F),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_SWORD, 2, 3, 1)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.IRON_INGOT, 4, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.BELL), 36, 1, 12, 5, 0.2F)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{new VillagerTrades.EmeraldForItems(Items.FLINT, 24, 12, 20)},
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.DIAMOND, 1, 12, 30),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_AXE, 12, 3, 15, 0.2F)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_SWORD, 8, 3, 30, 0.2F)}
               )
            )
         );
         map.put(
            VillagerProfession.TOOLSMITH,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.COAL, 15, 16, 2),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.STONE_AXE), 1, 1, 12, 1, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.STONE_SHOVEL), 1, 1, 12, 1, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.STONE_PICKAXE), 1, 1, 12, 1, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.STONE_HOE), 1, 1, 12, 1, 0.2F)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.IRON_INGOT, 4, 12, 10),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.BELL), 36, 1, 12, 5, 0.2F)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.FLINT, 30, 12, 20),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_AXE, 1, 3, 10, 0.2F),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_SHOVEL, 2, 3, 10, 0.2F),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_PICKAXE, 3, 3, 10, 0.2F),
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.DIAMOND_HOE), 4, 1, 3, 10, 0.2F)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.DIAMOND, 1, 12, 30),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_AXE, 12, 3, 15, 0.2F),
                     new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_SHOVEL, 5, 3, 15, 0.2F)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_PICKAXE, 13, 3, 30, 0.2F)}
               )
            )
         );
         map.put(
            VillagerProfession.BUTCHER,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.CHICKEN, 14, 16, 2),
                     new VillagerTrades.EmeraldForItems(Items.PORKCHOP, 7, 16, 2),
                     new VillagerTrades.EmeraldForItems(Items.RABBIT, 4, 16, 2),
                     new VillagerTrades.ItemsForEmeralds(Items.RABBIT_STEW, 1, 1, 1)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.COAL, 15, 16, 2),
                     new VillagerTrades.ItemsForEmeralds(Items.COOKED_PORKCHOP, 1, 5, 16, 5),
                     new VillagerTrades.ItemsForEmeralds(Items.COOKED_CHICKEN, 1, 8, 16, 5)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.MUTTON, 7, 16, 20), new VillagerTrades.EmeraldForItems(Items.BEEF, 10, 16, 20)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{new VillagerTrades.EmeraldForItems(Items.DRIED_KELP_BLOCK, 10, 12, 30)},
                  5,
                  new VillagerTrades.ItemListing[]{new VillagerTrades.EmeraldForItems(Items.SWEET_BERRIES, 10, 12, 30)}
               )
            )
         );
         map.put(
            VillagerProfession.LEATHERWORKER,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.LEATHER, 6, 16, 2),
                     new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_LEGGINGS, 3),
                     new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_CHESTPLATE, 7)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.FLINT, 26, 12, 10),
                     new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_HELMET, 5, 12, 5),
                     new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_BOOTS, 4, 12, 5)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.RABBIT_HIDE, 9, 12, 20), new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_CHESTPLATE, 7)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.TURTLE_SCUTE, 4, 12, 30),
                     new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_HORSE_ARMOR, 6, 12, 15)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.SADDLE), 6, 1, 12, 30, 0.2F),
                     new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_HELMET, 5, 12, 30)
                  }
               )
            )
         );
         map.put(
            VillagerProfession.MASON,
            toIntMap(
               ImmutableMap.of(
                  1,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.CLAY_BALL, 10, 16, 2), new VillagerTrades.ItemsForEmeralds(Items.BRICK, 1, 10, 16, 1)
                  },
                  2,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Blocks.STONE, 20, 16, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.CHISELED_STONE_BRICKS, 1, 4, 16, 5)
                  },
                  3,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Blocks.GRANITE, 16, 16, 20),
                     new VillagerTrades.EmeraldForItems(Blocks.ANDESITE, 16, 16, 20),
                     new VillagerTrades.EmeraldForItems(Blocks.DIORITE, 16, 16, 20),
                     new VillagerTrades.ItemsForEmeralds(Blocks.DRIPSTONE_BLOCK, 1, 4, 16, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.POLISHED_ANDESITE, 1, 4, 16, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.POLISHED_DIORITE, 1, 4, 16, 10),
                     new VillagerTrades.ItemsForEmeralds(Blocks.POLISHED_GRANITE, 1, 4, 16, 10)
                  },
                  4,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.EmeraldForItems(Items.QUARTZ, 12, 12, 30),
                     new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.RED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PINK_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIME_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.RED_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PINK_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.LIME_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                     new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_GLAZED_TERRACOTTA, 1, 1, 12, 15)
                  },
                  5,
                  new VillagerTrades.ItemListing[]{
                     new VillagerTrades.ItemsForEmeralds(Blocks.QUARTZ_PILLAR, 1, 1, 12, 30),
                     new VillagerTrades.ItemsForEmeralds(Blocks.QUARTZ_BLOCK, 1, 1, 12, 30)
                  }
               )
            )
         );
      }
   );
   public static final List<Pair<VillagerTrades.ItemListing[], Integer>> WANDERING_TRADER_TRADES = ImmutableList.builder()
      .add(
         Pair.of(
            new VillagerTrades.ItemListing[]{
               new VillagerTrades.EmeraldForItems(potionCost(Potions.WATER), 2, 1, 1),
               new VillagerTrades.EmeraldForItems(Items.WATER_BUCKET, 1, 2, 1, 2),
               new VillagerTrades.EmeraldForItems(Items.MILK_BUCKET, 1, 2, 1, 2),
               new VillagerTrades.EmeraldForItems(Items.FERMENTED_SPIDER_EYE, 1, 2, 1, 3),
               new VillagerTrades.EmeraldForItems(Items.BAKED_POTATO, 4, 2, 1),
               new VillagerTrades.EmeraldForItems(Items.HAY_BLOCK, 1, 2, 1)
            },
            2
         )
      )
      .add(
         Pair.of(
            new VillagerTrades.ItemListing[]{
               new VillagerTrades.ItemsForEmeralds(Items.PACKED_ICE, 1, 1, 6, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BLUE_ICE, 6, 1, 6, 1),
               new VillagerTrades.ItemsForEmeralds(Items.GUNPOWDER, 1, 4, 2, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PODZOL, 3, 3, 6, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.ACACIA_LOG, 1, 8, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.BIRCH_LOG, 1, 8, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.DARK_OAK_LOG, 1, 8, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.JUNGLE_LOG, 1, 8, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.OAK_LOG, 1, 8, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.SPRUCE_LOG, 1, 8, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.CHERRY_LOG, 1, 8, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.MANGROVE_LOG, 1, 8, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Blocks.PALE_OAK_LOG, 1, 8, 4, 1),
               new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_PICKAXE, 1, 1, 1, 0.2F),
               new VillagerTrades.ItemsForEmeralds(potion(Potions.LONG_INVISIBILITY), 5, 1, 1, 1)
            },
            2
         )
      )
      .add(
         Pair.of(
            new VillagerTrades.ItemListing[]{
               new VillagerTrades.ItemsForEmeralds(Items.TROPICAL_FISH_BUCKET, 3, 1, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PUFFERFISH_BUCKET, 3, 1, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Items.SEA_PICKLE, 2, 1, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.SLIME_BALL, 4, 1, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.GLOWSTONE, 2, 1, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.NAUTILUS_SHELL, 5, 1, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.FERN, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.SUGAR_CANE, 1, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PUMPKIN, 1, 1, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Items.KELP, 3, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.CACTUS, 3, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.DANDELION, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.POPPY, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BLUE_ORCHID, 1, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.ALLIUM, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.AZURE_BLUET, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.RED_TULIP, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.ORANGE_TULIP, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.WHITE_TULIP, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PINK_TULIP, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.OXEYE_DAISY, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.CORNFLOWER, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.LILY_OF_THE_VALLEY, 1, 1, 7, 1),
               new VillagerTrades.ItemsForEmeralds(Items.OPEN_EYEBLOSSOM, 1, 1, 7, 1),
               new VillagerTrades.ItemsForEmeralds(Items.WHEAT_SEEDS, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BEETROOT_SEEDS, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PUMPKIN_SEEDS, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.MELON_SEEDS, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.ACACIA_SAPLING, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BIRCH_SAPLING, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.DARK_OAK_SAPLING, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.JUNGLE_SAPLING, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.OAK_SAPLING, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.SPRUCE_SAPLING, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.CHERRY_SAPLING, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PALE_OAK_SAPLING, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.MANGROVE_PROPAGULE, 5, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.RED_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.WHITE_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BLUE_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PINK_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BLACK_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.GREEN_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.LIGHT_GRAY_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.MAGENTA_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.YELLOW_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.GRAY_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PURPLE_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.LIGHT_BLUE_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.LIME_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.ORANGE_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BROWN_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.CYAN_DYE, 1, 3, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BRAIN_CORAL_BLOCK, 3, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BUBBLE_CORAL_BLOCK, 3, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.FIRE_CORAL_BLOCK, 3, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.HORN_CORAL_BLOCK, 3, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.TUBE_CORAL_BLOCK, 3, 1, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.VINE, 1, 3, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PALE_HANGING_MOSS, 1, 3, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Items.BROWN_MUSHROOM, 1, 3, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Items.RED_MUSHROOM, 1, 3, 4, 1),
               new VillagerTrades.ItemsForEmeralds(Items.LILY_PAD, 1, 5, 2, 1),
               new VillagerTrades.ItemsForEmeralds(Items.SMALL_DRIPLEAF, 1, 2, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.SAND, 1, 8, 8, 1),
               new VillagerTrades.ItemsForEmeralds(Items.RED_SAND, 1, 4, 6, 1),
               new VillagerTrades.ItemsForEmeralds(Items.POINTED_DRIPSTONE, 1, 2, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.ROOTED_DIRT, 1, 2, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.MOSS_BLOCK, 1, 2, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.PALE_MOSS_BLOCK, 1, 2, 5, 1),
               new VillagerTrades.ItemsForEmeralds(Items.WILDFLOWERS, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.DRY_TALL_GRASS, 1, 1, 12, 1),
               new VillagerTrades.ItemsForEmeralds(Items.FIREFLY_BUSH, 3, 1, 12, 1)
            },
            5
         )
      )
      .build();
   public static final Map<ResourceKey<VillagerProfession>, Int2ObjectMap<VillagerTrades.ItemListing[]>> EXPERIMENTAL_TRADES = Map.of(
      VillagerProfession.LIBRARIAN,
      toIntMap(
         ImmutableMap.builder()
            .put(
               1,
               new VillagerTrades.ItemListing[]{
                  new VillagerTrades.EmeraldForItems(Items.PAPER, 24, 16, 2),
                  commonBooks(1),
                  new VillagerTrades.ItemsForEmeralds(Blocks.BOOKSHELF, 9, 1, 12, 1)
               }
            )
            .put(
               2,
               new VillagerTrades.ItemListing[]{
                  new VillagerTrades.EmeraldForItems(Items.BOOK, 4, 12, 10), commonBooks(5), new VillagerTrades.ItemsForEmeralds(Items.LANTERN, 1, 1, 5)
               }
            )
            .put(
               3,
               new VillagerTrades.ItemListing[]{
                  new VillagerTrades.EmeraldForItems(Items.INK_SAC, 5, 12, 20), commonBooks(10), new VillagerTrades.ItemsForEmeralds(Items.GLASS, 1, 4, 10)
               }
            )
            .put(
               4,
               new VillagerTrades.ItemListing[]{
                  new VillagerTrades.EmeraldForItems(Items.WRITABLE_BOOK, 2, 12, 30),
                  new VillagerTrades.ItemsForEmeralds(Items.CLOCK, 5, 1, 15),
                  new VillagerTrades.ItemsForEmeralds(Items.COMPASS, 4, 1, 15)
               }
            )
            .put(5, new VillagerTrades.ItemListing[]{specialBooks(), new VillagerTrades.ItemsForEmeralds(Items.NAME_TAG, 20, 1, 30)})
            .build()
      ),
      VillagerProfession.ARMORER,
      toIntMap(
         ImmutableMap.builder()
            .put(
               1,
               new VillagerTrades.ItemListing[]{
                  new VillagerTrades.EmeraldForItems(Items.COAL, 15, 12, 2), new VillagerTrades.EmeraldForItems(Items.IRON_INGOT, 5, 12, 2)
               }
            )
            .put(
               2,
               new VillagerTrades.ItemListing[]{
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.IRON_BOOTS, 4, 1, 12, 5, 0.05F),
                     VillagerType.DESERT,
                     VillagerType.PLAINS,
                     VillagerType.SAVANNA,
                     VillagerType.SNOW,
                     VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.CHAINMAIL_BOOTS, 4, 1, 12, 5, 0.05F), VillagerType.JUNGLE, VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.IRON_HELMET, 5, 1, 12, 5, 0.05F),
                     VillagerType.DESERT,
                     VillagerType.PLAINS,
                     VillagerType.SAVANNA,
                     VillagerType.SNOW,
                     VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.CHAINMAIL_HELMET, 5, 1, 12, 5, 0.05F), VillagerType.JUNGLE, VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.IRON_LEGGINGS, 7, 1, 12, 5, 0.05F),
                     VillagerType.DESERT,
                     VillagerType.PLAINS,
                     VillagerType.SAVANNA,
                     VillagerType.SNOW,
                     VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.CHAINMAIL_LEGGINGS, 7, 1, 12, 5, 0.05F), VillagerType.JUNGLE, VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.IRON_CHESTPLATE, 9, 1, 12, 5, 0.05F),
                     VillagerType.DESERT,
                     VillagerType.PLAINS,
                     VillagerType.SAVANNA,
                     VillagerType.SNOW,
                     VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.CHAINMAIL_CHESTPLATE, 9, 1, 12, 5, 0.05F), VillagerType.JUNGLE, VillagerType.SWAMP
                  )
               }
            )
            .put(
               3,
               new VillagerTrades.ItemListing[]{
                  new VillagerTrades.EmeraldForItems(Items.LAVA_BUCKET, 1, 12, 20),
                  new VillagerTrades.ItemsForEmeralds(Items.SHIELD, 5, 1, 12, 10, 0.05F),
                  new VillagerTrades.ItemsForEmeralds(Items.BELL, 36, 1, 12, 10, 0.2F)
               }
            )
            .put(
               4,
               new VillagerTrades.ItemListing[]{
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.IRON_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_BOOTS_4),
                     VillagerType.DESERT
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_HELMET_4
                     ),
                     VillagerType.DESERT
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_LEGGINGS, 11, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_LEGGINGS_4
                     ),
                     VillagerType.DESERT
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_CHESTPLATE, 13, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_CHESTPLATE_4
                     ),
                     VillagerType.DESERT
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.IRON_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_BOOTS_4),
                     VillagerType.PLAINS
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_HELMET_4
                     ),
                     VillagerType.PLAINS
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_LEGGINGS, 11, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_LEGGINGS_4
                     ),
                     VillagerType.PLAINS
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_CHESTPLATE, 13, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_CHESTPLATE_4
                     ),
                     VillagerType.PLAINS
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_BOOTS, 2, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_BOOTS_4
                     ),
                     VillagerType.SAVANNA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_HELMET, 3, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_HELMET_4
                     ),
                     VillagerType.SAVANNA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_LEGGINGS, 5, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_LEGGINGS_4
                     ),
                     VillagerType.SAVANNA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.IRON_CHESTPLATE, 7, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_CHESTPLATE_4
                     ),
                     VillagerType.SAVANNA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.IRON_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SNOW_ARMORER_BOOTS_4),
                     VillagerType.SNOW
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(Items.IRON_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SNOW_ARMORER_HELMET_4),
                     VillagerType.SNOW
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_BOOTS_4
                     ),
                     VillagerType.JUNGLE
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_HELMET_4
                     ),
                     VillagerType.JUNGLE
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_LEGGINGS, 11, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_LEGGINGS_4
                     ),
                     VillagerType.JUNGLE
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_CHESTPLATE, 13, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_CHESTPLATE_4
                     ),
                     VillagerType.JUNGLE
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_BOOTS_4
                     ),
                     VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_HELMET_4
                     ),
                     VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_LEGGINGS, 11, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_LEGGINGS_4
                     ),
                     VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_CHESTPLATE, 13, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_CHESTPLATE_4
                     ),
                     VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(Items.DIAMOND_BOOTS, 1, 4, Items.DIAMOND_LEGGINGS, 1, 3, 15, 0.05F), VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(Items.DIAMOND_LEGGINGS, 1, 4, Items.DIAMOND_CHESTPLATE, 1, 3, 15, 0.05F), VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(Items.DIAMOND_HELMET, 1, 4, Items.DIAMOND_BOOTS, 1, 3, 15, 0.05F), VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(Items.DIAMOND_CHESTPLATE, 1, 2, Items.DIAMOND_HELMET, 1, 3, 15, 0.05F), VillagerType.TAIGA
                  )
               }
            )
            .put(
               5,
               new VillagerTrades.ItemListing[]{
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 4, 16, Items.DIAMOND_CHESTPLATE, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_CHESTPLATE_5
                     ),
                     VillagerType.DESERT
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 3, 16, Items.DIAMOND_LEGGINGS, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_LEGGINGS_5
                     ),
                     VillagerType.DESERT
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 3, 16, Items.DIAMOND_LEGGINGS, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_LEGGINGS_5
                     ),
                     VillagerType.PLAINS
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 2, 12, Items.DIAMOND_BOOTS, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_BOOTS_5
                     ),
                     VillagerType.PLAINS
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 2, 6, Items.DIAMOND_HELMET, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_HELMET_5
                     ),
                     VillagerType.SAVANNA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 3, 8, Items.DIAMOND_CHESTPLATE, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_CHESTPLATE_5
                     ),
                     VillagerType.SAVANNA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 2, 12, Items.DIAMOND_BOOTS, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SNOW_ARMORER_BOOTS_5
                     ),
                     VillagerType.SNOW
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 3, 12, Items.DIAMOND_HELMET, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SNOW_ARMORER_HELMET_5
                     ),
                     VillagerType.SNOW
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_HELMET, 9, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_HELMET_5
                     ),
                     VillagerType.JUNGLE
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_BOOTS, 8, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_BOOTS_5
                     ),
                     VillagerType.JUNGLE
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_HELMET, 9, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_HELMET_5
                     ),
                     VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsForEmeralds(
                        Items.CHAINMAIL_BOOTS, 8, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_BOOTS_5
                     ),
                     VillagerType.SWAMP
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 4, 18, Items.DIAMOND_CHESTPLATE, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_TAIGA_ARMORER_CHESTPLATE_5
                     ),
                     VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.ItemsAndEmeraldsToItems(
                        Items.DIAMOND, 3, 18, Items.DIAMOND_LEGGINGS, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_TAIGA_ARMORER_LEGGINGS_5
                     ),
                     VillagerType.TAIGA
                  ),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(new VillagerTrades.EmeraldForItems(Items.DIAMOND_BLOCK, 1, 12, 30, 42), VillagerType.TAIGA),
                  VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                     new VillagerTrades.EmeraldForItems(Items.IRON_BLOCK, 1, 12, 30, 4),
                     VillagerType.DESERT,
                     VillagerType.JUNGLE,
                     VillagerType.PLAINS,
                     VillagerType.SAVANNA,
                     VillagerType.SNOW,
                     VillagerType.SWAMP
                  )
               }
            )
            .build()
      )
   );

   private static VillagerTrades.ItemListing commonBooks(final int villagerXP) {
      return new VillagerTrades.TypeSpecificTrade(
         ImmutableMap.builder()
            .put(VillagerType.DESERT, new VillagerTrades.EnchantBookForEmeralds(villagerXP, EnchantmentTags.TRADES_DESERT_COMMON))
            .put(VillagerType.JUNGLE, new VillagerTrades.EnchantBookForEmeralds(villagerXP, EnchantmentTags.TRADES_JUNGLE_COMMON))
            .put(VillagerType.PLAINS, new VillagerTrades.EnchantBookForEmeralds(villagerXP, EnchantmentTags.TRADES_PLAINS_COMMON))
            .put(VillagerType.SAVANNA, new VillagerTrades.EnchantBookForEmeralds(villagerXP, EnchantmentTags.TRADES_SAVANNA_COMMON))
            .put(VillagerType.SNOW, new VillagerTrades.EnchantBookForEmeralds(villagerXP, EnchantmentTags.TRADES_SNOW_COMMON))
            .put(VillagerType.SWAMP, new VillagerTrades.EnchantBookForEmeralds(villagerXP, EnchantmentTags.TRADES_SWAMP_COMMON))
            .put(VillagerType.TAIGA, new VillagerTrades.EnchantBookForEmeralds(villagerXP, EnchantmentTags.TRADES_TAIGA_COMMON))
            .build()
      );
   }

   private static VillagerTrades.ItemListing specialBooks() {
      return new VillagerTrades.TypeSpecificTrade(
         ImmutableMap.builder()
            .put(VillagerType.DESERT, new VillagerTrades.EnchantBookForEmeralds(30, 3, 3, EnchantmentTags.TRADES_DESERT_SPECIAL))
            .put(VillagerType.JUNGLE, new VillagerTrades.EnchantBookForEmeralds(30, 2, 2, EnchantmentTags.TRADES_JUNGLE_SPECIAL))
            .put(VillagerType.PLAINS, new VillagerTrades.EnchantBookForEmeralds(30, 3, 3, EnchantmentTags.TRADES_PLAINS_SPECIAL))
            .put(VillagerType.SAVANNA, new VillagerTrades.EnchantBookForEmeralds(30, 3, 3, EnchantmentTags.TRADES_SAVANNA_SPECIAL))
            .put(VillagerType.SNOW, new VillagerTrades.EnchantBookForEmeralds(30, EnchantmentTags.TRADES_SNOW_SPECIAL))
            .put(VillagerType.SWAMP, new VillagerTrades.EnchantBookForEmeralds(30, EnchantmentTags.TRADES_SWAMP_SPECIAL))
            .put(VillagerType.TAIGA, new VillagerTrades.EnchantBookForEmeralds(30, 2, 2, EnchantmentTags.TRADES_TAIGA_SPECIAL))
            .build()
      );
   }

   private static Int2ObjectMap<VillagerTrades.ItemListing[]> toIntMap(final ImmutableMap<Integer, VillagerTrades.ItemListing[]> source) {
      return new Int2ObjectOpenHashMap(source);
   }

   private static ItemCost potionCost(final Holder<Potion> potion) {
      return new ItemCost(Items.POTION).withComponents(b -> b.expect(DataComponents.POTION_CONTENTS, new PotionContents(potion)));
   }

   private static ItemStack potion(final Holder<Potion> potion) {
      return PotionContents.createItemStack(Items.POTION, potion);
   }

   private static class DyedArmorForEmeralds implements VillagerTrades.ItemListing {
      private final Item item;
      private final int value;
      private final int maxUses;
      private final int villagerXp;

      public DyedArmorForEmeralds(final Item item, final int value) {
         this(item, value, 12, 1);
      }

      public DyedArmorForEmeralds(final Item item, final int value, final int maxUses, final int villagerXp) {
         this.item = item;
         this.value = value;
         this.maxUses = maxUses;
         this.villagerXp = villagerXp;
      }

      @Override
      public MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         ItemCost emeraldCost = new ItemCost(Items.EMERALD, this.value);
         ItemStack resultItemStack = new ItemStack(this.item);
         if (resultItemStack.is(ItemTags.DYEABLE)) {
            List<DyeItem> dyes = Lists.newArrayList();
            dyes.add(getRandomDye(random));
            if (random.nextFloat() > 0.7F) {
               dyes.add(getRandomDye(random));
            }

            if (random.nextFloat() > 0.8F) {
               dyes.add(getRandomDye(random));
            }

            resultItemStack = DyedItemColor.applyDyes(resultItemStack, dyes);
         }

         return new MerchantOffer(emeraldCost, resultItemStack, this.maxUses, this.villagerXp, 0.2F);
      }

      private static DyeItem getRandomDye(final RandomSource random) {
         return DyeItem.byColor(DyeColor.byId(random.nextInt(16)));
      }
   }

   private static class EmeraldForItems implements VillagerTrades.ItemListing {
      private final ItemCost itemStack;
      private final int maxUses;
      private final int villagerXp;
      private final int emeraldAmount;
      private final float priceMultiplier;

      public EmeraldForItems(final ItemLike item, final int itemAmount, final int maxUses, final int villagerXp) {
         this(item, itemAmount, maxUses, villagerXp, 1);
      }

      public EmeraldForItems(final ItemLike item, final int itemAmount, final int maxUses, final int villagerXp, final int emeraldAmount) {
         this(new ItemCost(item.asItem(), itemAmount), maxUses, villagerXp, emeraldAmount);
      }

      public EmeraldForItems(final ItemCost itemStack, final int maxUses, final int villagerXp, final int emeraldAmount) {
         this.itemStack = itemStack;
         this.maxUses = maxUses;
         this.villagerXp = villagerXp;
         this.emeraldAmount = emeraldAmount;
         this.priceMultiplier = 0.05F;
      }

      @Override
      public MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         return new MerchantOffer(this.itemStack, new ItemStack(Items.EMERALD, this.emeraldAmount), this.maxUses, this.villagerXp, this.priceMultiplier);
      }
   }

   private static class EmeraldsForVillagerTypeItem implements VillagerTrades.ItemListing {
      private final Map<ResourceKey<VillagerType>, Item> trades;
      private final int cost;
      private final int maxUses;
      private final int villagerXp;

      public EmeraldsForVillagerTypeItem(final int cost, final int maxUses, final int villagerXp, final Map<ResourceKey<VillagerType>, Item> trades) {
         BuiltInRegistries.VILLAGER_TYPE.registryKeySet().stream().filter(t -> !trades.containsKey(t)).findAny().ifPresent(t -> {
            throw new IllegalStateException("Missing trade for villager type: " + t);
         });
         this.trades = trades;
         this.cost = cost;
         this.maxUses = maxUses;
         this.villagerXp = villagerXp;
      }

      @Override
      public @Nullable MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         if (merchant instanceof VillagerDataHolder holder) {
            ResourceKey<VillagerType> type = holder.getVillagerData().type().unwrapKey().orElse(null);
            if (type == null) {
               return null;
            }

            ItemCost cost = new ItemCost(this.trades.get(type), this.cost);
            return new MerchantOffer(cost, new ItemStack(Items.EMERALD), this.maxUses, this.villagerXp, 0.05F);
         } else {
            return null;
         }
      }
   }

   private static class EnchantBookForEmeralds implements VillagerTrades.ItemListing {
      private final int villagerXp;
      private final TagKey<Enchantment> tradeableEnchantments;
      private final int minLevel;
      private final int maxLevel;

      public EnchantBookForEmeralds(final int villagerXp, final TagKey<Enchantment> enchantments) {
         this(villagerXp, 0, Integer.MAX_VALUE, enchantments);
      }

      public EnchantBookForEmeralds(final int villagerXp, final int minLevel, final int maxLevel, final TagKey<Enchantment> enchantments) {
         this.minLevel = minLevel;
         this.maxLevel = maxLevel;
         this.villagerXp = villagerXp;
         this.tradeableEnchantments = enchantments;
      }

      @Override
      public MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         Optional<Holder<Enchantment>> selected = serverLevel.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getRandomElementOf(this.tradeableEnchantments, random);
         int cost;
         ItemStack book;
         if (!selected.isEmpty()) {
            Holder<Enchantment> holder = selected.get();
            Enchantment enchantment = holder.value();
            int min = Math.max(enchantment.getMinLevel(), this.minLevel);
            int max = Math.min(enchantment.getMaxLevel(), this.maxLevel);
            int level = Mth.nextInt(random, min, max);
            book = EnchantmentHelper.createBook(new EnchantmentInstance(holder, level));
            cost = 2 + random.nextInt(5 + level * 10) + 3 * level;
            if (holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
               cost *= 2;
            }

            if (cost > 64) {
               cost = 64;
            }
         } else {
            cost = 1;
            book = new ItemStack(Items.BOOK);
         }

         return new MerchantOffer(new ItemCost(Items.EMERALD, cost), Optional.of(new ItemCost(Items.BOOK)), book, 12, this.villagerXp, 0.2F);
      }
   }

   private static class EnchantedItemForEmeralds implements VillagerTrades.ItemListing {
      private final ItemStack itemStack;
      private final int baseEmeraldCost;
      private final int maxUses;
      private final int villagerXp;
      private final float priceMultiplier;

      public EnchantedItemForEmeralds(final Item item, final int baseEmeraldCost, final int maxUses, final int villagerXp) {
         this(item, baseEmeraldCost, maxUses, villagerXp, 0.05F);
      }

      public EnchantedItemForEmeralds(final Item item, final int baseEmeraldCost, final int maxUses, final int villagerXp, final float priceMultiplier) {
         this.itemStack = new ItemStack(item);
         this.baseEmeraldCost = baseEmeraldCost;
         this.maxUses = maxUses;
         this.villagerXp = villagerXp;
         this.priceMultiplier = priceMultiplier;
      }

      @Override
      public MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         int enchantmentCost = 5 + random.nextInt(15);
         RegistryAccess registryAccess = serverLevel.registryAccess();
         Optional<HolderSet.Named<Enchantment>> tag = registryAccess.lookupOrThrow(Registries.ENCHANTMENT).get(EnchantmentTags.ON_TRADED_EQUIPMENT);
         ItemStack resultItemStack = EnchantmentHelper.enchantItem(random, new ItemStack(this.itemStack.getItem()), enchantmentCost, registryAccess, tag);
         int totalCost = Math.min(this.baseEmeraldCost + enchantmentCost, 64);
         ItemCost emeraldCost = new ItemCost(Items.EMERALD, totalCost);
         return new MerchantOffer(emeraldCost, resultItemStack, this.maxUses, this.villagerXp, this.priceMultiplier);
      }
   }

   private static class FailureItemListing implements VillagerTrades.ItemListing {
      @Override
      public MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         return null;
      }
   }

   public interface ItemListing {
      @Nullable MerchantOffer getOffer(ServerLevel serverLevel, Entity merchant, RandomSource random);
   }

   private static class ItemsAndEmeraldsToItems implements VillagerTrades.ItemListing {
      private final ItemCost fromItem;
      private final int emeraldCost;
      private final ItemStack toItem;
      private final int maxUses;
      private final int villagerXp;
      private final float priceMultiplier;
      private final Optional<ResourceKey<EnchantmentProvider>> enchantmentProvider;

      public ItemsAndEmeraldsToItems(
         final ItemLike fromItem,
         final int fromCount,
         final int emeraldCost,
         final Item toItem,
         final int toCount,
         final int maxUses,
         final int villagerXp,
         final float priceMultiplier
      ) {
         this(fromItem, fromCount, emeraldCost, new ItemStack(toItem), toCount, maxUses, villagerXp, priceMultiplier);
      }

      private ItemsAndEmeraldsToItems(
         final ItemLike fromItem,
         final int fromCount,
         final int emeraldCost,
         final ItemStack toItem,
         final int toCount,
         final int maxUses,
         final int villagerXp,
         final float priceMultiplier
      ) {
         this(new ItemCost(fromItem, fromCount), emeraldCost, toItem.copyWithCount(toCount), maxUses, villagerXp, priceMultiplier, Optional.empty());
      }

      private ItemsAndEmeraldsToItems(
         final ItemLike fromItem,
         final int fromCount,
         final int emeraldCost,
         final ItemLike toItem,
         final int toCount,
         final int maxUses,
         final int villagerXp,
         final float priceMultiplier,
         final ResourceKey<EnchantmentProvider> enchantmentProvider
      ) {
         this(
            new ItemCost(fromItem, fromCount),
            emeraldCost,
            new ItemStack(toItem, toCount),
            maxUses,
            villagerXp,
            priceMultiplier,
            Optional.of(enchantmentProvider)
         );
      }

      public ItemsAndEmeraldsToItems(
         final ItemCost fromItem,
         final int emeraldCost,
         final ItemStack toItem,
         final int maxUses,
         final int villagerXp,
         final float priceMultiplier,
         final Optional<ResourceKey<EnchantmentProvider>> enchantmentProvider
      ) {
         this.fromItem = fromItem;
         this.emeraldCost = emeraldCost;
         this.toItem = toItem;
         this.maxUses = maxUses;
         this.villagerXp = villagerXp;
         this.priceMultiplier = priceMultiplier;
         this.enchantmentProvider = enchantmentProvider;
      }

      @Override
      public @Nullable MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         ItemStack item = this.toItem.copy();
         this.enchantmentProvider
            .ifPresent(
               provider -> EnchantmentHelper.enchantItemFromProvider(
                  item,
                  serverLevel.registryAccess(),
                  (ResourceKey<EnchantmentProvider>)provider,
                  serverLevel.getCurrentDifficultyAt(merchant.blockPosition()),
                  random
               )
            );
         return new MerchantOffer(
            new ItemCost(Items.EMERALD, this.emeraldCost), Optional.of(this.fromItem), item, 0, this.maxUses, this.villagerXp, this.priceMultiplier
         );
      }
   }

   private static class ItemsForEmeralds implements VillagerTrades.ItemListing {
      private final ItemStack itemStack;
      private final int emeraldCost;
      private final int maxUses;
      private final int villagerXp;
      private final float priceMultiplier;
      private final Optional<ResourceKey<EnchantmentProvider>> enchantmentProvider;

      public ItemsForEmeralds(final Block block, final int emeraldCost, final int numberOfItems, final int maxUses, final int villagerXp) {
         this(new ItemStack(block), emeraldCost, numberOfItems, maxUses, villagerXp);
      }

      public ItemsForEmeralds(final Item item, final int emeraldCost, final int numberOfItems, final int villagerXp) {
         this(new ItemStack(item), emeraldCost, numberOfItems, 12, villagerXp);
      }

      public ItemsForEmeralds(final Item item, final int emeraldCost, final int numberOfItems, final int maxUses, final int villagerXp) {
         this(new ItemStack(item), emeraldCost, numberOfItems, maxUses, villagerXp);
      }

      public ItemsForEmeralds(final ItemStack itemStack, final int emeraldCost, final int numberOfItems, final int maxUses, final int villagerXp) {
         this(itemStack, emeraldCost, numberOfItems, maxUses, villagerXp, 0.05F);
      }

      public ItemsForEmeralds(
         final Item item, final int emeraldCost, final int numberOfItems, final int maxUses, final int villagerXp, final float priceMultiplier
      ) {
         this(new ItemStack(item), emeraldCost, numberOfItems, maxUses, villagerXp, priceMultiplier);
      }

      public ItemsForEmeralds(
         final Item item,
         final int emeraldCost,
         final int numberOfItems,
         final int maxUses,
         final int villagerXp,
         final float priceMultiplier,
         final ResourceKey<EnchantmentProvider> enchantmentProvider
      ) {
         this(new ItemStack(item), emeraldCost, numberOfItems, maxUses, villagerXp, priceMultiplier, Optional.of(enchantmentProvider));
      }

      public ItemsForEmeralds(
         final ItemStack itemStack, final int emeraldCost, final int numberOfItems, final int maxUses, final int villagerXp, final float priceMultiplier
      ) {
         this(itemStack, emeraldCost, numberOfItems, maxUses, villagerXp, priceMultiplier, Optional.empty());
      }

      public ItemsForEmeralds(
         final ItemStack itemStack,
         final int emeraldCost,
         final int numberOfItems,
         final int maxUses,
         final int villagerXp,
         final float priceMultiplier,
         final Optional<ResourceKey<EnchantmentProvider>> enchantmentProvider
      ) {
         this.itemStack = itemStack;
         this.emeraldCost = emeraldCost;
         this.itemStack.setCount(numberOfItems);
         this.maxUses = maxUses;
         this.villagerXp = villagerXp;
         this.priceMultiplier = priceMultiplier;
         this.enchantmentProvider = enchantmentProvider;
      }

      @Override
      public MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         ItemStack item = this.itemStack.copy();
         this.enchantmentProvider
            .ifPresent(
               provider -> EnchantmentHelper.enchantItemFromProvider(
                  item,
                  serverLevel.registryAccess(),
                  (ResourceKey<EnchantmentProvider>)provider,
                  serverLevel.getCurrentDifficultyAt(merchant.blockPosition()),
                  random
               )
            );
         return new MerchantOffer(new ItemCost(Items.EMERALD, this.emeraldCost), item, this.maxUses, this.villagerXp, this.priceMultiplier);
      }
   }

   private static class SuspiciousStewForEmerald implements VillagerTrades.ItemListing {
      private final SuspiciousStewEffects effects;
      private final int xp;
      private final float priceMultiplier;

      public SuspiciousStewForEmerald(final Holder<MobEffect> effect, final int duration, final int xp) {
         this(new SuspiciousStewEffects(List.of(new SuspiciousStewEffects.Entry(effect, duration))), xp, 0.05F);
      }

      public SuspiciousStewForEmerald(final SuspiciousStewEffects effects, final int xp, final float priceMultiplier) {
         this.effects = effects;
         this.xp = xp;
         this.priceMultiplier = priceMultiplier;
      }

      @Override
      public @Nullable MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW, 1);
         stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.effects);
         return new MerchantOffer(new ItemCost(Items.EMERALD), stew, 12, this.xp, this.priceMultiplier);
      }
   }

   private static class TippedArrowForItemsAndEmeralds implements VillagerTrades.ItemListing {
      private final ItemStack toItem;
      private final int toCount;
      private final int emeraldCost;
      private final int maxUses;
      private final int villagerXp;
      private final Item fromItem;
      private final int fromCount;
      private final float priceMultiplier;

      public TippedArrowForItemsAndEmeralds(
         final Item fromItem, final int fromCount, final Item toItem, final int toCount, final int emeraldCost, final int maxUses, final int villagerXp
      ) {
         this.toItem = new ItemStack(toItem);
         this.emeraldCost = emeraldCost;
         this.maxUses = maxUses;
         this.villagerXp = villagerXp;
         this.fromItem = fromItem;
         this.fromCount = fromCount;
         this.toCount = toCount;
         this.priceMultiplier = 0.05F;
      }

      @Override
      public MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         ItemCost emeralds = new ItemCost(Items.EMERALD, this.emeraldCost);
         List<Holder<Potion>> potions = BuiltInRegistries.POTION
            .listElements()
            .filter(potionx -> !((Potion)potionx.value()).getEffects().isEmpty() && serverLevel.potionBrewing().isBrewablePotion(potionx))
            .collect(Collectors.toList());
         Holder<Potion> potion = Util.getRandom(potions, random);
         ItemStack resultItemStack = new ItemStack(this.toItem.getItem(), this.toCount);
         resultItemStack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
         return new MerchantOffer(
            emeralds, Optional.of(new ItemCost(this.fromItem, this.fromCount)), resultItemStack, this.maxUses, this.villagerXp, this.priceMultiplier
         );
      }
   }

   private static class TreasureMapForEmeralds implements VillagerTrades.ItemListing {
      private final int emeraldCost;
      private final TagKey<Structure> destination;
      private final String displayName;
      private final Holder<MapDecorationType> destinationType;
      private final int maxUses;
      private final int villagerXp;

      public TreasureMapForEmeralds(
         final int value,
         final TagKey<Structure> destination,
         final String displayName,
         final Holder<MapDecorationType> destinationType,
         final int maxUses,
         final int villagerXp
      ) {
         this.emeraldCost = value;
         this.destination = destination;
         this.displayName = displayName;
         this.destinationType = destinationType;
         this.maxUses = maxUses;
         this.villagerXp = villagerXp;
      }

      @Override
      public @Nullable MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         BlockPos nearest = serverLevel.findNearestMapStructure(this.destination, merchant.blockPosition(), 100, true);
         if (nearest != null) {
            ItemStack map = MapItem.create(serverLevel, nearest.getX(), nearest.getZ(), (byte)2, true, true);
            MapItem.renderBiomePreviewMap(serverLevel, map);
            MapItemSavedData.addTargetDecoration(map, nearest, "+", this.destinationType);
            map.set(DataComponents.ITEM_NAME, Component.translatable(this.displayName));
            return new MerchantOffer(
               new ItemCost(Items.EMERALD, this.emeraldCost), Optional.of(new ItemCost(Items.COMPASS)), map, this.maxUses, this.villagerXp, 0.2F
            );
         } else {
            return null;
         }
      }
   }

   private record TypeSpecificTrade(Map<ResourceKey<VillagerType>, VillagerTrades.ItemListing> trades) implements VillagerTrades.ItemListing {
      @SafeVarargs
      public static VillagerTrades.TypeSpecificTrade oneTradeInBiomes(final VillagerTrades.ItemListing trade, final ResourceKey<VillagerType>... villageTypes) {
         return new VillagerTrades.TypeSpecificTrade(Arrays.stream(villageTypes).collect(Collectors.toMap(villageType -> villageType, villageType -> trade)));
      }

      @Override
      public @Nullable MerchantOffer getOffer(final ServerLevel serverLevel, final Entity merchant, final RandomSource random) {
         if (merchant instanceof VillagerDataHolder holder) {
            ResourceKey<VillagerType> typeKey = holder.getVillagerData().type().unwrapKey().orElse(null);
            if (typeKey == null) {
               return null;
            }

            VillagerTrades.ItemListing itemListing = this.trades.get(typeKey);
            return itemListing == null ? null : itemListing.getOffer(serverLevel, merchant, random);
         } else {
            return null;
         }
      }
   }
}
