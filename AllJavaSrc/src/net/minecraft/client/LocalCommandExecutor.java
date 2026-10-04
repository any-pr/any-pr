package net.minecraft.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * MCRe NoiseFarlands：客户端本地命令执行器。
 * 主界面/未连接世界时，聊天框仍可打开并执行本地命令（/help、/quit 等）。
 * 命令注册在类加载时初始化，不依赖服务器连接，随时可用。
 */
@OnlyIn(Dist.CLIENT)
public final class LocalCommandExecutor {
    private static final CommandDispatcher<SharedSuggestionProvider> DISPATCHER = new CommandDispatcher<>();
    private static final SharedSuggestionProvider SOURCE = new LocalSource();
    private static final List<String> COMMAND_NAMES = List.of("help", "say", "quit", "server_debug");
    // MCRe: keep a reference to the local DedicatedServer instance (started via /server_debug start)
    private static volatile net.minecraft.server.dedicated.DedicatedServer runningServer = null;

    private LocalCommandExecutor() {
    }

    /**
     * 🔧 MCRe：在 gameDir/TestServer/ 目录下启动专用服务器，避免污染 gameDir 根目录。
     * <p>所有服务端文件（server.properties、eula.txt、logs/、world/ 等）都会放在 TestServer 子目录。
     * <p>通过向 Main.main 传递 {@code --universe TestServer} 实现。
     */
    private static void startDebugServer(final String[] cliArgs, final java.util.Properties propUpdates) {
        try {
            // 准备 TestServer 目录
            java.nio.file.Path testServerDir = java.nio.file.Paths.get("TestServer");
            if (!java.nio.file.Files.exists(testServerDir)) {
                java.nio.file.Files.createDirectories(testServerDir);
            }

            // 写入/合并 server.properties 到 TestServer/
            java.nio.file.Path propPath = testServerDir.resolve("server.properties");
            java.util.Properties existing = new java.util.Properties();
            if (java.nio.file.Files.exists(propPath)) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(propPath)) {
                    existing.load(in);
                }
            }
            if (!propUpdates.isEmpty()) {
                existing.putAll(propUpdates);
            }
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(propPath)) {
                existing.store(out, null);
            }

            // 确保 eula.txt 存在
            java.nio.file.Path eulaPath = testServerDir.resolve("eula.txt");
            if (!java.nio.file.Files.exists(eulaPath)) {
                try (java.io.Writer writer = java.nio.file.Files.newBufferedWriter(eulaPath, java.nio.charset.StandardCharsets.UTF_8)) {
                    writer.write("eula=true\n");
                }
            }

            // 组装最终参数：加上 --universe TestServer
            java.util.List<String> finalArgs = new java.util.ArrayList<>(java.util.Arrays.asList(cliArgs));
            // 如果用户没显式指定 --universe，自动加上
            boolean hasUniverse = false;
            for (int i = 0; i < finalArgs.size(); i++) {
                if ("--universe".equals(finalArgs.get(i))) {
                    hasUniverse = true;
                    // 替换后面的值为 TestServer
                    if (i + 1 < finalArgs.size()) {
                        finalArgs.set(i + 1, "TestServer");
                    }
                    break;
                }
            }
            if (!hasUniverse) {
                finalArgs.add("--universe");
                finalArgs.add("TestServer");
            }

            // 在 TestServer 目录下启动（通过 universe 参数，Main 会自动用该目录）
            // 注意：Main 里的相对路径都是基于 universe 目录解析的，所以不需要改 working dir
            net.minecraft.server.Main.main(finalArgs.toArray(new String[0]));
            runningServer = net.minecraft.server.Main.getRunningServer();
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                Component.literal("Server started in TestServer/").withStyle(ChatFormatting.GREEN)
            );
        } catch (Throwable t) {
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                Component.literal("Server start failed: " + t.getMessage()).withStyle(ChatFormatting.RED)
            );
            t.printStackTrace();
        }
    }

    static {
        DISPATCHER.register(
            LiteralArgumentBuilder.<SharedSuggestionProvider>literal("help").executes(ctx -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("=== 本地命令（未进入世界）===").withStyle(ChatFormatting.GOLD)
                );
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("/help — 显示此帮助").withStyle(ChatFormatting.GRAY)
                );
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("/say <消息> — 在聊天框输出消息").withStyle(ChatFormatting.GRAY)
                );
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("/quit — 退出游戏").withStyle(ChatFormatting.GRAY)
                );
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("/server_debug <stop|start> <Options> — 用于调试改版在服务端层的改动").withStyle(ChatFormatting.GRAY)
                );
                return 1;
            })
        );
        // Register the root "/server_debug" command with three sub-commands:
        //   • /server_debug                →  prints usage help.
        //   • /server_debug stop           →  stops a running DedicatedServer (if any).
        //   • /server_debug start [options] →  launches a server in gameDir/TestServer/.
        //         Options: key=value → CLI arg if known Main option, else written to server.properties.
        //         Flags: --flag → passed as CLI if known, else set to true in properties.
        //   All server files (properties, eula, logs, world/) go into gameDir/TestServer/.
        DISPATCHER.register(
            LiteralArgumentBuilder.<SharedSuggestionProvider>literal("server_debug")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("Usage: /server_debug <start|stop> [options]").withStyle(ChatFormatting.YELLOW)
                    );
                    return 1;
                })
                // ------------------- STOP -------------------
                .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("stop")
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        net.minecraft.server.dedicated.DedicatedServer server = runningServer;
                        if (server != null) {
                            server.halt(true);
                            runningServer = null;
                            mc.gui.hud.getChat().addClientSystemMessage(
                                Component.literal("Server stopped.").withStyle(ChatFormatting.GREEN)
                            );
                        } else {
                            mc.gui.hud.getChat().addClientSystemMessage(
                                Component.literal("Server not running.").withStyle(ChatFormatting.RED)
                            );
                        }
                        return 1;
                    })
                )
                // ------------------- START -------------------
                .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("start")
                    .executes(ctx -> {
                        startDebugServer(new String[]{"--nogui"}, new java.util.Properties());
                        return 1;
                    })
                    .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("options", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String optStr = StringArgumentType.getString(ctx, "options");
                            java.util.List<String> args = new java.util.ArrayList<>();
                            java.util.Properties propUpdates = new java.util.Properties();
                            java.util.Set<String> knownCli = java.util.Set.of(
                                "nogui", "initSettings", "demo", "bonusChest",
                                "forceUpgrade", "eraseCache", "recreateRegionFiles",
                                "safeMode", "help", "universe", "world", "port",
                                "serverId", "jfrProfile", "pidFile"
                            );
                            if (!optStr.isBlank()) {
                                for (String token : optStr.split("\\s+")) {
                                    if (token.isEmpty()) continue;
                                    int eqIdx = token.indexOf('=');
                                    if (eqIdx > 0) {
                                        String key = token.substring(0, eqIdx);
                                        String value = token.substring(eqIdx + 1);
                                        if (knownCli.contains(key)) {
                                            args.add("--" + key);
                                            args.add(value);
                                        } else {
                                            propUpdates.setProperty(key, value);
                                        }
                                    } else {
                                        if (knownCli.contains(token)) {
                                            args.add("--" + token);
                                        } else {
                                            propUpdates.setProperty(token, "true");
                                        }
                                    }
                                }
                            }
                            if (!args.contains("--nogui")) {
                                args.add(0, "--nogui");
                            }
                            startDebugServer(args.toArray(new String[0]), propUpdates);
                            return 1;
                        })
                    )
                )
        );
    }

    public static CommandDispatcher<SharedSuggestionProvider> getDispatcher() {
        return DISPATCHER;
    }

    public static SharedSuggestionProvider getSource() {
        return SOURCE;
    }

    public static List<String> getCommandNames() {
        return COMMAND_NAMES;
    }

    /** 主界面/未连接时执行本地命令；未知命令显示红色提示 */
    public static void execute(final Minecraft minecraft, final String command) {
        try {
            ParseResults<SharedSuggestionProvider> parse = DISPATCHER.parse(new StringReader(command), SOURCE);
            DISPATCHER.execute(parse);
        } catch (CommandSyntaxException e) {
            minecraft.gui.hud.getChat().addClientSystemMessage(
                Component.literal("未知或不完整的命令：/" + command).withStyle(ChatFormatting.RED)
            );
        }
    }

    /** 最小 SharedSuggestionProvider 实现（主界面无连接/无世界数据） */
    private static final class LocalSource implements SharedSuggestionProvider {
        @Override
        public PermissionSet permissions() {
            return PermissionSet.NO_PERMISSIONS;
        }

        @Override
        public Collection<String> getOnlinePlayerNames() {
            return Collections.emptyList();
        }

        @Override
        public Collection<String> getAllTeams() {
            return Collections.emptyList();
        }

        @Override
        public Stream<Identifier> getAvailableSounds() {
            return Stream.empty();
        }

        @Override
        public CompletableFuture<Suggestions> customSuggestion(final CommandContext<?> context) {
            return Suggestions.empty();
        }

        @Override
        public Set<ResourceKey<Level>> levels() {
            return Collections.emptySet();
        }

        @Override
        public RegistryAccess registryAccess() {
            return RegistryAccess.EMPTY;
        }

        @Override
        public FeatureFlagSet enabledFeatures() {
            return FeatureFlagSet.of();
        }

        @Override
        public CompletableFuture<Suggestions> suggestRegistryElements(
            final ResourceKey<? extends Registry<?>> key,
            final SharedSuggestionProvider.ElementSuggestionType elements,
            final SuggestionsBuilder builder,
            final CommandContext<?> context
        ) {
            return builder.buildFuture();
        }
    }
}