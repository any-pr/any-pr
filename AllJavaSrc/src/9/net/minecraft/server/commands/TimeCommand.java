package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class TimeCommand {
   public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("time")
                     .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                  .then(
                     ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("set")
                                    .then(Commands.literal("day").executes(c -> setTime((CommandSourceStack)c.getSource(), 1000))))
                                 .then(Commands.literal("noon").executes(c -> setTime((CommandSourceStack)c.getSource(), 6000))))
                              .then(Commands.literal("night").executes(c -> setTime((CommandSourceStack)c.getSource(), 13000))))
                           .then(Commands.literal("midnight").executes(c -> setTime((CommandSourceStack)c.getSource(), 18000))))
                        .then(
                           Commands.argument("time", TimeArgument.time())
                              .executes(c -> setTime((CommandSourceStack)c.getSource(), IntegerArgumentType.getInteger(c, "time")))
                        )
                  ))
               .then(
                  Commands.literal("add")
                     .then(
                        Commands.argument("time", TimeArgument.time())
                           .executes(c -> addTime((CommandSourceStack)c.getSource(), IntegerArgumentType.getInteger(c, "time")))
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("query")
                        .then(
                           Commands.literal("daytime")
                              .executes(c -> queryTime((CommandSourceStack)c.getSource(), getDayTime(((CommandSourceStack)c.getSource()).getLevel())))
                        ))
                     .then(
                        Commands.literal("gametime")
                           .executes(
                              c -> queryTime(
                                 (CommandSourceStack)c.getSource(), (int)(((CommandSourceStack)c.getSource()).getLevel().getGameTime() % 2147483647L)
                              )
                           )
                     ))
                  .then(
                     Commands.literal("day")
                        .executes(
                           c -> queryTime((CommandSourceStack)c.getSource(), (int)(((CommandSourceStack)c.getSource()).getLevel().getDayCount() % 2147483647L))
                        )
                  )
            )
      );
   }

   private static int getDayTime(final ServerLevel level) {
      return (int)(level.getDayTime() % 24000L);
   }

   private static int queryTime(final CommandSourceStack source, final int time) {
      source.sendSuccess(() -> Component.translatable("commands.time.query", time), false);
      return time;
   }

   public static int setTime(final CommandSourceStack source, final int time) {
      for (ServerLevel level : source.getServer().getAllLevels()) {
         level.setDayTime(time);
      }

      source.getServer().forceTimeSynchronization();
      source.sendSuccess(() -> Component.translatable("commands.time.set", time), true);
      return getDayTime(source.getLevel());
   }

   public static int addTime(final CommandSourceStack source, final int time) {
      for (ServerLevel level : source.getServer().getAllLevels()) {
         level.setDayTime(level.getDayTime() + time);
      }

      source.getServer().forceTimeSynchronization();
      int newTime = getDayTime(source.getLevel());
      source.sendSuccess(() -> Component.translatable("commands.time.set", newTime), true);
      return newTime;
   }
}
