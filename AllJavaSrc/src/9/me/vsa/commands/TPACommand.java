package main.java.me.vsa.commands;

import main.java.me.vsa.VoidServerAdmin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TPACommand extends Command implements TabExecutor {

    private final VoidServerAdmin plugin;

    public TPACommand(VoidServerAdmin plugin, String name, String description, String usageMessage, List<String> aliases) {
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
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只能由玩家执行");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            player.sendMessage("§c用法: /tpa-player <access|deny|to|setting> ...");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "access": handleAccess(player); break;
            case "deny": handleDeny(player); break;
            case "to":
                if (args.length < 2) { player.sendMessage("§c用法: /tpa-player to <玩家名>"); return true; }
                handleTo(player, args[1]);
                break;
            case "setting": handleSetting(player, args); break;
            default: player.sendMessage("§c未知子命令");
        }
        return true;
    }

    private void handleAccess(Player player) {
        VoidServerAdmin.TpaRequest req = plugin.getPendingRequests().get(player.getUniqueId());
        if (req == null) {
            player.sendMessage("§c你没有收到任何互传请求");
            return;
        }
        Player requester = Bukkit.getPlayer(req.requester);
        if (requester == null || !requester.isOnline()) {
            player.sendMessage("§c请求发送者已离线");
            cancelRequest(req);
            return;
        }
        Set<UUID> black = plugin.getBlacklist().getOrDefault(player.getUniqueId(), new HashSet<>());
        if (black.contains(requester.getUniqueId())) {
            player.sendMessage("§c你已拉黑对方，无法接受请求");
            return;
        }
        requester.teleport(player.getLocation());
        PotionEffectType resistance = PotionEffectType.getByName("RESISTANCE");
        if (resistance != null) {
            requester.addPotionEffect(new PotionEffect(resistance, 60, 4));
        }
        player.sendMessage("§a你已接受 §6" + requester.getName() + " §a的传送请求");
        requester.sendMessage("§a玩家 §6" + player.getName() + " §a已接受你的传送请求");
        req.timeoutTask.cancel();
        plugin.getPendingRequests().remove(player.getUniqueId());
    }

    private void handleDeny(Player player) {
        VoidServerAdmin.TpaRequest req = plugin.getPendingRequests().remove(player.getUniqueId());
        if (req == null) {
            player.sendMessage("§c你没有收到任何互传请求");
            return;
        }
        req.timeoutTask.cancel();
        Player requester = Bukkit.getPlayer(req.requester);
        if (requester != null && requester.isOnline()) {
            requester.sendMessage("§c玩家 §6" + player.getName() + " §c拒绝了你的传送请求");
        }
        player.sendMessage("§c你已拒绝传送请求");
    }

    private void handleTo(Player player, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§c玩家 " + targetName + " 不在线");
            return;
        }
        if (target.equals(player)) {
            player.sendMessage("§c不能向自己发送请求");
            return;
        }
        VoidServerAdmin.TpaRequest existing = plugin.getPendingRequests().get(target.getUniqueId());
        if (existing != null) {
            existing.timeoutTask.cancel();
            plugin.getPendingRequests().remove(target.getUniqueId());
        }
        Set<UUID> black = plugin.getBlacklist().getOrDefault(target.getUniqueId(), new HashSet<>());
        if (black.contains(player.getUniqueId())) {
            player.sendMessage("§c你已被对方拉黑");
            return;
        }
        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                VoidServerAdmin.TpaRequest req = plugin.getPendingRequests().get(target.getUniqueId());
                if (req != null && req.requester.equals(player.getUniqueId())) {
                    plugin.getPendingRequests().remove(target.getUniqueId());
                    player.sendMessage("§c互传请求已超时");
                    if (target.isOnline()) {
                        target.sendMessage("§c来自 §6" + player.getName() + " §c的传送请求已超时");
                    }
                }
            }
        }.runTaskLater(plugin, 600L);

        plugin.getPendingRequests().put(target.getUniqueId(),
                new VoidServerAdmin.TpaRequest(player.getUniqueId(), target.getUniqueId(), timeoutTask));
        player.sendMessage("<§cTPA System§f>§a你已向 §6" + target.getName() + " §a发送互传请求 (30秒后过期)");
        target.sendMessage("<§cTPA System§f> §a玩家 §6" + player.getName() + " §a想传送到你这里 §e(/tpa-player access 允许, /tpa-player deny 拒绝)");
    }

    private void handleSetting(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /tpa-player setting <add|list|remove> [玩家名]");
            return;
        }
        String sub = args[1].toLowerCase();
        Set<UUID> black = plugin.getBlacklist().computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());

        switch (sub) {
            case "list":
                if (black.isEmpty()) player.sendMessage("§a黑名单为空");
                else {
                    StringBuilder sb = new StringBuilder("§a黑名单: ");
                    for (UUID uuid : black) {
                        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                        sb.append("§6").append(off.getName()).append("§a, ");
                    }
                    player.sendMessage(sb.substring(0, sb.length() - 2));
                }
                break;
            case "add":
                if (args.length < 3) { player.sendMessage("§c用法: /tpa-player setting add <玩家名>"); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { player.sendMessage("§c玩家不在线"); return; }
                if (black.add(target.getUniqueId())) player.sendMessage("§a已添加 §6" + target.getName() + " §a到黑名单");
                else player.sendMessage("§c该玩家已在黑名单");
                break;
            case "remove":
                if (args.length < 3) { player.sendMessage("§c用法: /tpa-player setting remove <玩家名>"); return; }
                OfflinePlayer off = Bukkit.getOfflinePlayer(args[2]);
                if (off.getUniqueId() == null) { player.sendMessage("§c玩家不存在"); return; }
                if (black.remove(off.getUniqueId())) player.sendMessage("§a已从黑名单移除 §6" + off.getName());
                else player.sendMessage("§c该玩家不在黑名单");
                break;
            default: player.sendMessage("§c未知 setting 子命令");
        }
    }

    private void cancelRequest(VoidServerAdmin.TpaRequest req) {
        if (req.timeoutTask != null) req.timeoutTask.cancel();
        plugin.getPendingRequests().remove(req.target);
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) return completions;
        Player player = (Player) sender;

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("access".startsWith(input)) completions.add("access");
            if ("deny".startsWith(input)) completions.add("deny");
            if ("to".startsWith(input)) completions.add("to");
            if ("setting".startsWith(input)) completions.add("setting");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("to")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player) && p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("setting")) {
                String input = args[1].toLowerCase();
                if ("add".startsWith(input)) completions.add("add");
                if ("list".startsWith(input)) completions.add("list");
                if ("remove".startsWith(input)) completions.add("remove");
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("setting") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player) && p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }
        return completions;
    }
}