package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;

public final class LevelSettings {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final String levelName;
   private final GameType gameType;
   private final boolean hardcore;
   private final Difficulty difficulty;
   private final boolean allowCommands;
   private final GameRules gameRules;
   private final WorldDataConfiguration dataConfiguration;

   public LevelSettings(
      final String levelName,
      final GameType gameType,
      final boolean hardcore,
      final Difficulty difficulty,
      final boolean allowCommands,
      final GameRules gameRules,
      final WorldDataConfiguration dataConfiguration
   ) {
      this.levelName = levelName;
      this.gameType = gameType;
      this.hardcore = hardcore;
      this.difficulty = difficulty;
      this.allowCommands = allowCommands;
      this.gameRules = gameRules;
      this.dataConfiguration = dataConfiguration;
   }

   public static LevelSettings parse(final Dynamic<?> input, final WorldDataConfiguration loadConfig) {
      GameType gameType = GameType.byId(input.get("GameType").asInt(0));
      return new LevelSettings(
         input.get("LevelName").asString(""),
         gameType,
         input.get("hardcore").asBoolean(false),
         input.get("Difficulty").asNumber().map(n -> Difficulty.byId(n.byteValue())).result().orElse(Difficulty.NORMAL),
         input.get("allowCommands").asBoolean(gameType == GameType.CREATIVE),
         (GameRules)GameRules.codec(loadConfig.enabledFeatures()).parse(input.get("game_rules").orElseEmptyMap()).resultOrPartial(LOGGER::warn).orElseThrow(),
         loadConfig
      );
   }

   public String levelName() {
      return this.levelName;
   }

   public GameType gameType() {
      return this.gameType;
   }

   public boolean hardcore() {
      return this.hardcore;
   }

   public Difficulty difficulty() {
      return this.difficulty;
   }

   public boolean allowCommands() {
      return this.allowCommands;
   }

   public GameRules gameRules() {
      return this.gameRules;
   }

   public WorldDataConfiguration getDataConfiguration() {
      return this.dataConfiguration;
   }

   public LevelSettings withGameType(final GameType gameType) {
      return new LevelSettings(this.levelName, gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, this.dataConfiguration);
   }

   public LevelSettings withDifficulty(final Difficulty difficulty) {
      return new LevelSettings(this.levelName, this.gameType, this.hardcore, difficulty, this.allowCommands, this.gameRules, this.dataConfiguration);
   }

   public LevelSettings withDataConfiguration(final WorldDataConfiguration dataConfiguration) {
      return new LevelSettings(this.levelName, this.gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, dataConfiguration);
   }

   public LevelSettings copy() {
      return new LevelSettings(
         this.levelName,
         this.gameType,
         this.hardcore,
         this.difficulty,
         this.allowCommands,
         this.gameRules.copy(this.dataConfiguration.enabledFeatures()),
         this.dataConfiguration
      );
   }
}
