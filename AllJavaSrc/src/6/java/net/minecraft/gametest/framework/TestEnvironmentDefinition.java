package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;

public interface TestEnvironmentDefinition {
   Codec<TestEnvironmentDefinition> DIRECT_CODEC = BuiltInRegistries.TEST_ENVIRONMENT_DEFINITION_TYPE
      .byNameCodec()
      .dispatch(TestEnvironmentDefinition::codec, c -> c);
   Codec<Holder<TestEnvironmentDefinition>> CODEC = RegistryFileCodec.create(Registries.TEST_ENVIRONMENT, DIRECT_CODEC);

   static MapCodec<? extends TestEnvironmentDefinition> bootstrap(final Registry<MapCodec<? extends TestEnvironmentDefinition>> registry) {
      Registry.register(registry, "all_of", TestEnvironmentDefinition.AllOf.CODEC);
      Registry.register(registry, "game_rules", TestEnvironmentDefinition.SetGameRules.CODEC);
      Registry.register(registry, "time_of_day", TestEnvironmentDefinition.TimeOfDay.CODEC);
      Registry.register(registry, "weather", TestEnvironmentDefinition.Weather.CODEC);
      return Registry.register(registry, "function", TestEnvironmentDefinition.Functions.CODEC);
   }

   void setup(ServerLevel level);

   default void teardown(final ServerLevel level) {
   }

   MapCodec<? extends TestEnvironmentDefinition> codec();

   record AllOf(List<Holder<TestEnvironmentDefinition>> definitions) implements TestEnvironmentDefinition {
      public static final MapCodec<TestEnvironmentDefinition.AllOf> CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(TestEnvironmentDefinition.CODEC.listOf().fieldOf("definitions").forGetter(TestEnvironmentDefinition.AllOf::definitions))
            .apply(i, TestEnvironmentDefinition.AllOf::new)
      );

      public AllOf(final TestEnvironmentDefinition... defs) {
         this(Arrays.stream(defs).map(Holder::direct).toList());
      }

      @Override
      public void setup(final ServerLevel level) {
         this.definitions.forEach(b -> b.value().setup(level));
      }

      @Override
      public void teardown(final ServerLevel level) {
         this.definitions.forEach(b -> b.value().teardown(level));
      }

      @Override
      public MapCodec<TestEnvironmentDefinition.AllOf> codec() {
         return CODEC;
      }
   }

   record Functions(Optional<Identifier> setupFunction, Optional<Identifier> teardownFunction) implements TestEnvironmentDefinition {
      private static final Logger LOGGER = LogUtils.getLogger();
      public static final MapCodec<TestEnvironmentDefinition.Functions> CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(
               Identifier.CODEC.optionalFieldOf("setup").forGetter(TestEnvironmentDefinition.Functions::setupFunction),
               Identifier.CODEC.optionalFieldOf("teardown").forGetter(TestEnvironmentDefinition.Functions::teardownFunction)
            )
            .apply(i, TestEnvironmentDefinition.Functions::new)
      );

      @Override
      public void setup(final ServerLevel level) {
         this.setupFunction.ifPresent(p -> run(level, p));
      }

      @Override
      public void teardown(final ServerLevel level) {
         this.teardownFunction.ifPresent(p -> run(level, p));
      }

      private static void run(final ServerLevel level, final Identifier functionId) {
         MinecraftServer server = level.getServer();
         ServerFunctionManager functions = server.getFunctions();
         Optional<CommandFunction<CommandSourceStack>> function = functions.get(functionId);
         if (function.isPresent()) {
            CommandSourceStack source = server.createCommandSourceStack()
               .withPermission(LevelBasedPermissionSet.GAMEMASTER)
               .withSuppressedOutput()
               .withLevel(level);
            functions.execute(function.get(), source);
         } else {
            LOGGER.error("Test Batch failed for non-existent function {}", functionId);
         }
      }

      @Override
      public MapCodec<TestEnvironmentDefinition.Functions> codec() {
         return CODEC;
      }
   }

   record SetGameRules(GameRuleMap gameRulesMap) implements TestEnvironmentDefinition {
      public static final MapCodec<TestEnvironmentDefinition.SetGameRules> CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(GameRuleMap.CODEC.fieldOf("rules").forGetter(TestEnvironmentDefinition.SetGameRules::gameRulesMap))
            .apply(i, TestEnvironmentDefinition.SetGameRules::new)
      );

      @Override
      public void setup(final ServerLevel level) {
         GameRules gameRules = level.getGameRules();
         MinecraftServer server = level.getServer();
         gameRules.setAll(this.gameRulesMap, server);
      }

      @Override
      public void teardown(final ServerLevel level) {
         this.gameRulesMap.keySet().forEach(gameRule -> this.resetRule(level, (GameRule<?>)gameRule));
      }

      private <T> void resetRule(final ServerLevel level, final GameRule<T> gameRule) {
         level.getGameRules().set(gameRule, gameRule.defaultValue(), level.getServer());
      }

      @Override
      public MapCodec<TestEnvironmentDefinition.SetGameRules> codec() {
         return CODEC;
      }
   }

   record TimeOfDay(int time) implements TestEnvironmentDefinition {
      public static final MapCodec<TestEnvironmentDefinition.TimeOfDay> CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(ExtraCodecs.NON_NEGATIVE_INT.fieldOf("time").forGetter(TestEnvironmentDefinition.TimeOfDay::time))
            .apply(i, TestEnvironmentDefinition.TimeOfDay::new)
      );

      @Override
      public void setup(final ServerLevel level) {
         level.setDayTime(this.time);
      }

      @Override
      public MapCodec<TestEnvironmentDefinition.TimeOfDay> codec() {
         return CODEC;
      }
   }

   record Weather(TestEnvironmentDefinition.Weather.Type weather) implements TestEnvironmentDefinition {
      public static final MapCodec<TestEnvironmentDefinition.Weather> CODEC = RecordCodecBuilder.mapCodec(
         i -> i.group(TestEnvironmentDefinition.Weather.Type.CODEC.fieldOf("weather").forGetter(TestEnvironmentDefinition.Weather::weather))
            .apply(i, TestEnvironmentDefinition.Weather::new)
      );

      @Override
      public void setup(final ServerLevel level) {
         this.weather.apply(level);
      }

      @Override
      public void teardown(final ServerLevel level) {
         level.resetWeatherCycle();
      }

      @Override
      public MapCodec<TestEnvironmentDefinition.Weather> codec() {
         return CODEC;
      }

      public enum Type implements StringRepresentable {
         CLEAR("clear", 100000, 0, false, false),
         RAIN("rain", 0, 100000, true, false),
         THUNDER("thunder", 0, 100000, true, true);

         public static final Codec<TestEnvironmentDefinition.Weather.Type> CODEC = StringRepresentable.fromEnum(TestEnvironmentDefinition.Weather.Type::values);
         private final String id;
         private final int clearTime;
         private final int rainTime;
         private final boolean raining;
         private final boolean thundering;

         Type(final String id, final int clearTime, final int rainTime, final boolean raining, final boolean thundering) {
            this.id = id;
            this.clearTime = clearTime;
            this.rainTime = rainTime;
            this.raining = raining;
            this.thundering = thundering;
         }

         void apply(final ServerLevel level) {
            level.setWeatherParameters(this.clearTime, this.rainTime, this.raining, this.thundering);
         }

         @Override
         public String getSerializedName() {
            return this.id;
         }
      }
   }
}
