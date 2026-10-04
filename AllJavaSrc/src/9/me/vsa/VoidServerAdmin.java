package main.java.me.vsa;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import main.java.me.vsa.commands.AdminCommand;
import main.java.me.vsa.commands.TPACommand;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

public class VoidServerAdmin extends JavaPlugin implements Listener {

    // ---------- 自定义边界相关 ----------
    private static final double HARD_LIMIT = 33_552_000D;
    private static final String CONFIG_WORLDS = "worlds";

    // ---------- 反作弊配置路径 ----------
    private static final String CONFIG_ANTICHEAT_ANTI4D4V = "anticheat.anti4d4v";
    private static final String CONFIG_ANTICHEAT_BANBBQ = "anticheat.banBBQ";

    // ---------- 日志查看器相关 ----------
    private final Set<Player> logViewers = ConcurrentHashMap.newKeySet();
    private Handler logHandler;

    // ---------- 调试模式和静默崩溃配置 ----------
    private static final String CONFIG_DEBUG_MODE = "debug-mode";
    private static final String CONFIG_SILENT_CRASH_MODE = "silent-crash-mode";

    // ---------- 区块索引溢出修复配置 ----------
    private static final String CONFIG_FIX_ENTITY_CHUNK_OVERFLOW = "fixes.entity-chunk-overflow";

    // ---------- 末影龙Y轴速度修复配置 ----------
    private static final String CONFIG_FIX_DRAGON_Y_SPEED = "fixes.ender-dragon-y-speed";

    // ---------- TPA 相关 ----------
    private final Map<UUID, TpaRequest> pendingRequests = new HashMap<>();
    private final Map<UUID, Set<UUID>> blacklist = new HashMap<>();

    // ---------- 外部命令执行相关 ----------
    private static final String CONFIG_EXECUTE_COMMANDS = "ExecuteCommands_WithPlugins";
    private BukkitTask commandExecutorTask;

    // ---------- 其他工具字段 ----------
    private boolean isPaperServer = false;
    private int majorVersion = -1;

    @Override
    public void onEnable() {
        try {
            Class.forName("com.destroystokyo.paper.utils.PaperPluginLogger");
            isPaperServer = true;
        } catch (ClassNotFoundException ignored) {}

        majorVersion = getMajorServerVersion();

        getConfig().addDefault(CONFIG_ANTICHEAT_ANTI4D4V, false);
        getConfig().addDefault(CONFIG_ANTICHEAT_BANBBQ, false);
        getConfig().addDefault(CONFIG_DEBUG_MODE, false);
        getConfig().addDefault(CONFIG_SILENT_CRASH_MODE, false);
        getConfig().addDefault(CONFIG_FIX_ENTITY_CHUNK_OVERFLOW, true);
        getConfig().addDefault(CONFIG_FIX_DRAGON_Y_SPEED, true);
        getConfig().addDefault(CONFIG_EXECUTE_COMMANDS, new ArrayList<String>());
        getConfig().options().copyDefaults(true);
        saveConfig();

        // 注册命令（使用提取的独立命令类）
        AdminCommand adminCmd = new AdminCommand(this, "admin", "IllegalStackTrans 扩展命令", "/admin ...", Arrays.asList("admin"));
        getServer().getCommandMap().register("illegalstack", adminCmd);

        TPACommand tpaCmd = new TPACommand(this, "tpa-player", "TPA 传送系统",
                "/tpa-player <access|deny|to|setting> ...", Arrays.asList("tpa-player"));
        getServer().getCommandMap().register("illegalstack", tpaCmd);

        // 注册监听器
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new RestartBlocker(), this);

        if (getConfig().getBoolean(CONFIG_FIX_ENTITY_CHUNK_OVERFLOW, true) && majorVersion >= 17) {
            getServer().getPluginManager().registerEvents(new ChunkOverflowFixListener(), this);
            getLogger().info("已启用矿车区块溢出修复 (适用于 1.17+)");
        }

        if (getConfig().getBoolean(CONFIG_FIX_DRAGON_Y_SPEED, true)) {
            Bukkit.getScheduler().runTaskTimer(this, new DragonYFixTask(), 0L, 1L);
            getLogger().info("已启用末影龙Y轴速度修复");
        }

        startCommandExecutorTask();
        getLogger().info("VoidServerAdmin 精简版已加载，/admin 和 /tpa-player 可用");
    }

    @Override
    public void onDisable() {
        if (commandExecutorTask != null) {
            commandExecutorTask.cancel();
            commandExecutorTask = null;
        }
        if (logHandler != null) {
            Bukkit.getLogger().removeHandler(logHandler);
            logHandler = null;
        }
        Bukkit.getScheduler().cancelTasks(this);
        saveConfig();
    }

    // ==================== 工具方法（供命令类使用） ====================

    public int getMajorServerVersion() {
        if (majorVersion > 0) return majorVersion;
        try {
            String ver = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String[] parts = ver.split("_");
            if (parts.length >= 2) {
                majorVersion = Integer.parseInt(parts[1]);
                return majorVersion;
            }
        } catch (Exception ignored) {}
        String bukkitVer = Bukkit.getServer().getBukkitVersion();
        if (bukkitVer.contains("1.20")) majorVersion = 20;
        else if (bukkitVer.contains("1.21")) majorVersion = 21;
        else majorVersion = 0;
        return majorVersion;
    }

    public boolean isPaperServer() {
        return isPaperServer;
    }

    // ==================== 边界相关（公开给命令） ====================

    public void setCustomWorldBorder(String worldName, double diameter) {
        getConfig().set(CONFIG_WORLDS + "." + worldName + ".diameter", diameter);
        saveConfig();
        getLogger().info("世界 " + worldName + " 自定义边界已设置，直径: " + diameter);
    }

    public void disableCustomWorldBorder(String worldName) {
        getConfig().set(CONFIG_WORLDS + "." + worldName, null);
        saveConfig();
        getLogger().info("世界 " + worldName + " 自定义边界已禁用。");
    }

    public boolean isWorldBorderEnabled(World world) {
        return getConfig().contains(CONFIG_WORLDS + "." + world.getName() + ".diameter");
    }

    public double getWorldBorderDiameter(World world) {
        return getConfig().getDouble(CONFIG_WORLDS + "." + world.getName() + ".diameter", -1);
    }

    // ==================== 调试 / 静默崩溃（公开） ====================

    public boolean isDebugMode() {
        return getConfig().getBoolean(CONFIG_DEBUG_MODE, false);
    }

    public void setDebugMode(boolean enabled) {
        getConfig().set(CONFIG_DEBUG_MODE, enabled);
        saveConfig();
    }

    public boolean isSilentCrashMode() {
        return getConfig().getBoolean(CONFIG_SILENT_CRASH_MODE, false);
    }

    public void setSilentCrashMode(boolean enabled) {
        getConfig().set(CONFIG_SILENT_CRASH_MODE, enabled);
        saveConfig();
    }

    // ==================== 日志查看器（公开） ====================

    public Set<Player> getLogViewers() {
        return logViewers;
    }

    public void ensureLogHandler() {
        if (logHandler != null) return;
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = record.getMessage();
                logViewers.removeIf(viewer -> !viewer.isOnline());
                for (Player viewer : logViewers) {
                    viewer.sendMessage(message);
                }
                if (logViewers.isEmpty()) {
                    removeLogHandler();
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logHandler.setLevel(Level.ALL);
        Bukkit.getLogger().addHandler(logHandler);
    }

    public void removeLogHandler() {
        if (logHandler != null) {
            Bukkit.getLogger().removeHandler(logHandler);
            logHandler = null;
        }
    }

    // ==================== TPA 相关（公开） ====================

    public Map<UUID, TpaRequest> getPendingRequests() {
        return pendingRequests;
    }

    public Map<UUID, Set<UUID>> getBlacklist() {
        return blacklist;
    }

    // ==================== 外部命令执行 ====================

    private void startCommandExecutorTask() {
        commandExecutorTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<String> commands = getConfig().getStringList(CONFIG_EXECUTE_COMMANDS);
            if (commands == null || commands.isEmpty()) return;
            getConfig().set(CONFIG_EXECUTE_COMMANDS, new ArrayList<String>());
            saveConfig();

            Bukkit.getScheduler().runTaskTimer(VoidServerAdmin.this, new BukkitRunnable() {
                private int index = 0;
                @Override
                public void run() {
                    if (index >= commands.size()) {
                        this.cancel();
                        return;
                    }
                    String cmd = commands.get(index);
                    if (cmd != null && !cmd.trim().isEmpty()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.trim());
                    }
                    index++;
                }
            }, 0L, 1L);
        }, 0L, 20L);
    }

    // ==================== 数据包部署（修复牧羊人小屋） ====================

    public void setShepherdHouseFix(boolean enable) {
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        File datapacksFolder = new File(worldFolder, "datapacks");
        File fixDatapackFolder = new File(datapacksFolder, "fix-snowy-shepherd-house");

        if (enable) {
            if (!fixDatapackFolder.exists()) {
                fixDatapackFolder.mkdirs();
                createPackMeta(fixDatapackFolder);
                File structureFolder = new File(fixDatapackFolder, "data/buidling_entrance/structures");
                structureFolder.mkdirs();
                File nbtFile = new File(structureFolder, "snowy_shepherds_house_1.nbt");
                try (InputStream in = getResource("snowy_shepherds_house_1.nbt")) {
                    if (in == null) {
                        getLogger().warning("修复文件 snowy_shepherds_house_1.nbt 未找到！");
                        return;
                    }
                    java.nio.file.Files.copy(in, nbtFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    getLogger().info("雪地牧羊人小屋修复数据包已部署。");
                } catch (IOException e) {
                    getLogger().log(Level.SEVERE, "无法复制修复文件", e);
                }
            } else {
                getLogger().info("修复数据包已存在。");
            }
        } else {
            if (fixDatapackFolder.exists()) {
                deleteDirectory(fixDatapackFolder);
                getLogger().info("修复数据包已移除。");
            }
        }
    }

    private void createPackMeta(File packFolder) {
        File mcmeta = new File(packFolder, "pack.mcmeta");
        String content = "{\n" +
                "  \"pack\": {\n" +
                "    \"pack_format\": 48,\n" +
                "    \"description\": \"Fix for snowy shepherd house\"\n" +
                "  }\n" +
                "}";
        try (FileWriter writer = new FileWriter(mcmeta)) {
            writer.write(content);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "无法创建 pack.mcmeta", e);
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteDirectory(file);
                else file.delete();
            }
        }
        dir.delete();
    }

    // ==================== 反作弊监听器（保留在主类） ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!getConfig().getBoolean(CONFIG_ANTICHEAT_ANTI4D4V, false)) return;
        Player player = event.getPlayer();
        String message = event.getMessage();
        if (message.contains("4d4v.top")) {
            Bukkit.getScheduler().runTask(this, () -> player.kickPlayer("§c你的账号似乎为 4D4V 方面的宣传机器人"));
            event.setCancelled(true);
            getLogger().info("已踢出宣传 4d4v.top 的玩家: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean(CONFIG_ANTICHEAT_BANBBQ, false)) return;
        Player player = event.getPlayer();
        if (player.getName().equalsIgnoreCase("smooth_BBQ")) {
            String ip = player.getAddress().getAddress().getHostAddress();
            Bukkit.getBanList(BanList.Type.IP).addBan(ip, "Banned by antiBBQ", null, "Console");
            Bukkit.getScheduler().runTask(this, () -> player.kickPlayer("§c你的 IP 已被封禁"));
            getLogger().info("已封禁 smooth_BBQ 的 IP: " + ip);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (TpaRequest req : pendingRequests.values()) {
            if (req.requester.equals(player.getUniqueId())) {
                req.timeoutTask.cancel();
                Player target = Bukkit.getPlayer(req.target);
                if (target != null && target.isOnline()) {
                    target.sendMessage("§c发送者 §6" + player.getName() + " §c已离线，传送请求取消。");
                }
            }
        }
        TpaRequest req = pendingRequests.remove(player.getUniqueId());
        if (req != null) {
            req.timeoutTask.cancel();
            Player requester = Bukkit.getPlayer(req.requester);
            if (requester != null && requester.isOnline()) {
                requester.sendMessage("§c目标玩家 §6" + player.getName() + " §c已离线，传送请求取消。");
            }
        }

        if (logViewers.remove(player)) {
            if (logViewers.isEmpty()) removeLogHandler();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;
        World world = to.getWorld();
        if (world == null) return;

        double x = to.getX(), z = to.getZ();
        double absX = Math.abs(x), absZ = Math.abs(z);
        if (isWorldBorderEnabled(world)) {
            double radius = getWorldBorderDiameter(world) / 2.0;
            if (absX > radius || absZ > radius) {
                Location corrected = to.clone();
                if (absX > radius) corrected.setX(x > 0 ? radius : -radius);
                if (absZ > radius) corrected.setZ(z > 0 ? radius : -radius);
                player.teleport(corrected);
                player.sendMessage("§c你已到达世界边界！");
                return;
            }
        }

        if (absX >= HARD_LIMIT || absZ >= HARD_LIMIT) {
            int chunkX = to.getBlockX() >> 4;
            int chunkZ = to.getBlockZ() >> 4;
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            if (!chunk.isLoaded()) chunk.load(true);
            if (isPaperServer()) {
                world.getChunkAtAsync(chunkX, chunkZ, (c) -> c.setForceLoaded(true));
            }
            final Location target = to.clone();
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline() && player.getWorld().equals(target.getWorld())) {
                    player.teleport(target);
                }
            });
        }

        if (event.getFrom().getBlockX() == to.getBlockX() &&
            event.getFrom().getBlockY() == to.getBlockY() &&
            event.getFrom().getBlockZ() == to.getBlockZ()) return;

        for (TpaRequest req : pendingRequests.values()) {
            if (req.requester.equals(player.getUniqueId())) {
                req.timeoutTask.cancel();
                pendingRequests.remove(req.target);
                Player target = Bukkit.getPlayer(req.target);
                if (target != null && target.isOnline()) {
                    target.sendMessage("§c发送者 §6" + player.getName() + " §c移动了，传送请求已取消。");
                }
                player.sendMessage("§c你移动了，传送请求已取消。");
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;
        World world = to.getWorld();
        if (world == null) return;

        double absX = Math.abs(to.getX()), absZ = Math.abs(to.getZ());
        if (absX >= HARD_LIMIT || absZ >= HARD_LIMIT) {
            int chunkX = to.getBlockX() >> 4;
            int chunkZ = to.getBlockZ() >> 4;
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            if (!chunk.isLoaded()) chunk.load(true);
            if (isPaperServer()) {
                world.getChunkAtAsync(chunkX, chunkZ, (c) -> c.setForceLoaded(true));
            }
        }
    }

    // ==================== 内部类：RestartBlocker ====================

    private class RestartBlocker implements Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
            String msg = event.getMessage().toLowerCase().trim();
            if (msg.equals("/restart") || msg.startsWith("/restart ")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c/restart 命令已被禁用，请使用面板或启动脚本管理服务器。");
            }
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onServerCommand(ServerCommandEvent event) {
            String cmd = event.getCommand().toLowerCase().trim();
            if (cmd.equals("restart") || cmd.startsWith("restart ")) {
                event.setCancelled(true);
                event.getSender().sendMessage("§c/restart 命令已被禁用，请使用面板或启动脚本管理服务器。");
            }
        }
    }

    // ==================== 内部类：ChunkOverflowFixListener ====================

    private class ChunkOverflowFixListener implements Listener {
        private boolean isDangerChunk(int chunkX) {
            return ((chunkX + 1) & 0x3FFFFF) == 0x200000;
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onEntitySpawn(EntitySpawnEvent event) {
            if (!getConfig().getBoolean(CONFIG_FIX_ENTITY_CHUNK_OVERFLOW, true)) return;
            if (getMajorServerVersion() < 17) return;
            Entity entity = event.getEntity();
            if (!(entity instanceof org.bukkit.entity.Minecart)) return;
            int chunkX = entity.getLocation().getBlockX() >> 4;
            if (isDangerChunk(chunkX)) {
                event.setCancelled(true);
                getLogger().warning("阻止了矿车在危险区块生成: " + entity.getLocation());
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onVehicleMove(VehicleMoveEvent event) {
            if (!getConfig().getBoolean(CONFIG_FIX_ENTITY_CHUNK_OVERFLOW, true)) return;
            if (getMajorServerVersion() < 17) return;
            Vehicle vehicle = event.getVehicle();
            if (!(vehicle instanceof org.bukkit.entity.Minecart)) return;
            Location to = event.getTo();
            int chunkX = to.getBlockX() >> 4;
            if (isDangerChunk(chunkX)) {
                vehicle.teleport(event.getFrom());
                vehicle.getWorld().playSound(vehicle.getLocation(), Sound.ENTITY_MINECART_INSIDE, 0.5f, 1.0f);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onEntityTeleport(EntityTeleportEvent event) {
            if (!getConfig().getBoolean(CONFIG_FIX_ENTITY_CHUNK_OVERFLOW, true)) return;
            if (getMajorServerVersion() < 17) return;
            Entity entity = event.getEntity();
            if (!(entity instanceof org.bukkit.entity.Minecart)) return;
            Location to = event.getTo();
            if (to == null) return;
            int chunkX = to.getBlockX() >> 4;
            if (isDangerChunk(chunkX)) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== 内部类：DragonYFixTask ====================

    private class DragonYFixTask implements Runnable {
        private Method getPhaseManager, getCurrentPhase, getFlyTargetLocation, getFlySpeed;
        private Method getDeltaMovement, setDeltaMovement, vecX, vecY, vecZ;
        private Class<?> vec3Class;
        private java.lang.reflect.Constructor<?> vec3Constructor;

        public DragonYFixTask() {
            try {
                Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
                getDeltaMovement = entityClass.getMethod("getDeltaMovement");
                setDeltaMovement = entityClass.getMethod("setDeltaMovement", Class.forName("net.minecraft.world.phys.Vec3"));

                Class<?> enderDragonClass = Class.forName("net.minecraft.world.entity.boss.enderdragon.EnderDragon");
                getPhaseManager = enderDragonClass.getMethod("getPhaseManager");

                Class<?> phaseManagerClass = Class.forName("net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseManager");
                getCurrentPhase = phaseManagerClass.getMethod("getCurrentPhase");

                Class<?> phaseInstanceClass = Class.forName("net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance");
                getFlyTargetLocation = phaseInstanceClass.getMethod("getFlyTargetLocation");
                getFlySpeed = phaseInstanceClass.getMethod("getFlySpeed");

                vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
                vecX = vec3Class.getMethod("x");
                vecY = vec3Class.getMethod("y");
                vecZ = vec3Class.getMethod("z");
                vec3Constructor = vec3Class.getConstructor(double.class, double.class, double.class);
            } catch (Exception e) {
                getLogger().warning("无法初始化末影龙修复反射：" + e.getMessage());
            }
        }

        @Override
        public void run() {
            if (!getConfig().getBoolean(CONFIG_FIX_DRAGON_Y_SPEED, true)) return;
            if (getPhaseManager == null) return;
            for (World world : Bukkit.getWorlds()) {
                for (EnderDragon dragon : world.getEntitiesByClass(EnderDragon.class)) {
                    applyFix(dragon);
                }
            }
        }

        private void applyFix(EnderDragon dragon) {
            try {
                Object nmsEntity = ((org.bukkit.craftbukkit.entity.CraftEntity) dragon).getHandle();
                Object phaseManager = getPhaseManager.invoke(nmsEntity);
                if (phaseManager == null) return;
                Object currentPhase = getCurrentPhase.invoke(phaseManager);
                if (currentPhase == null) return;
                Object targetLocation = getFlyTargetLocation.invoke(currentPhase);
                if (targetLocation == null) return;

                double tx = (double) vecX.invoke(targetLocation);
                double ty = (double) vecY.invoke(targetLocation);
                double tz = (double) vecZ.invoke(targetLocation);

                double x = ((Number) nmsEntity.getClass().getMethod("getX").invoke(nmsEntity)).doubleValue();
                double y = ((Number) nmsEntity.getClass().getMethod("getY").invoke(nmsEntity)).doubleValue();
                double z = ((Number) nmsEntity.getClass().getMethod("getZ").invoke(nmsEntity)).doubleValue();

                double xdd = tx - x;
                double ydd = ty - y;
                double zdd = tz - z;
                float max = (float) getFlySpeed.invoke(currentPhase);
                double horizontalDist = Math.sqrt(zdd * zdd + xdd * xdd);
                if (horizontalDist > 0.0D) {
                    ydd = Math.max(-max, Math.min(ydd / horizontalDist, max));
                }

                Object delta = getDeltaMovement.invoke(nmsEntity);
                double dx = (double) vecX.invoke(delta);
                double dy = (double) vecY.invoke(delta);
                double dz = (double) vecZ.invoke(delta);
                dy += ydd * 0.1D;

                Object newDelta = vec3Constructor.newInstance(dx, dy, dz);
                setDeltaMovement.invoke(nmsEntity, newDelta);
            } catch (Exception e) {
                // 静默失败
            }
        }
    }

    // ==================== 内部类：LevelDatEditor（公开供命令使用） ====================

    public static class LevelDatEditor {
        private final File levelFile;
        private CompoundTag compound;
        private final World world;

        public LevelDatEditor(World world) throws IOException {
            this.world = world;
            File worldFolder = world.getWorldFolder();
            this.levelFile = new File(worldFolder, "level.dat");
            if (!levelFile.exists()) throw new IOException("level.dat 不存在！");
            try (FileInputStream fis = new FileInputStream(levelFile)) {
                CompoundTag root = NbtIo.readCompressed(fis, NbtAccounter.unlimitedHeap());
                this.compound = root.getCompound("Data").orElseThrow(() -> new IOException("缺少 Data 标签"));
            }
        }

        public void setDouble(String key, double value) { compound.putDouble(key, value); }
        public void setInt(String key, int value) { compound.putInt(key, value); }
        public void setLong(String key, long value) { compound.putLong(key, value); }
        public void setBoolean(String key, boolean value) { compound.putBoolean(key, value); }
        public void setByte(String key, byte value) { compound.putByte(key, value); }
        public void setFloat(String key, float value) { compound.putFloat(key, value); }
        public void setString(String key, String value) { compound.putString(key, value); }

        public void setUUID(String key, UUID uuid) {
            setLong(key + "Most", uuid.getMostSignificantBits());
            setLong(key + "Least", uuid.getLeastSignificantBits());
        }

        public List<String> getServerBrands() {
            Optional<ListTag> optionalList = compound.getList("ServerBrands");
            if (!optionalList.isPresent()) return new ArrayList<>();
            ListTag listTag = optionalList.get();
            List<String> result = new ArrayList<>();
            for (int i = 0; i < listTag.size(); i++) {
                listTag.getString(i).ifPresent(result::add);
            }
            return result;
        }

        public void addServerBrand(String brand) {
            List<String> brands = getServerBrands();
            brands.add(brand);
            setServerBrands(brands);
        }

        public void removeServerBrand(String brand) {
            List<String> brands = getServerBrands();
            brands.remove(brand);
            setServerBrands(brands);
        }

        private void setServerBrands(List<String> brands) {
            ListTag listTag = new ListTag();
            for (String s : brands) {
                listTag.add(StringTag.valueOf(s));
            }
            compound.put("ServerBrands", listTag);
        }

        public void save() throws IOException {
            CompoundTag root = new CompoundTag();
            root.put("Data", compound);
            try (FileOutputStream fos = new FileOutputStream(levelFile)) {
                NbtIo.writeCompressed(root, fos);
            }
        }
    }

    // ==================== 内部类：TpaRequest（公开供命令使用） ====================

    public static class TpaRequest {
        public UUID requester;
        public UUID target;
        public BukkitTask timeoutTask;
        public long timestamp;

        public TpaRequest(UUID requester, UUID target, BukkitTask timeoutTask) {
            this.requester = requester;
            this.target = target;
            this.timeoutTask = timeoutTask;
            this.timestamp = System.currentTimeMillis();
        }
    }
}