package fr.xephi.authme.util;

import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Utility class for various operations used in the codebase.
 */
public final class Utils {

    /** Number of milliseconds in a minute. */
    public static final long MILLIS_PER_MINUTE = 60_000L;

    private static ConsoleLogger logger = ConsoleLoggerFactory.get(Utils.class);

    // Utility class
    private Utils() {
    }

    /**
     * Compile Pattern sneaky without throwing Exception.
     *
     * @param pattern pattern string to compile
     *
     * @return the given regex compiled into Pattern object.
     */
    public static Pattern safePatternCompile(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (Exception e) {
            logger.warning("Failed to compile pattern '" + pattern + "' - defaulting to allowing everything");
            return Pattern.compile(".*?");
        }
    }

    /**
     * Returns whether the class exists in the current class loader.
     *
     * @param className the class name to check
     *
     * @return true if the class is loaded, false otherwise
     */
    public static boolean isClassLoaded(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Sends a message to the given sender (null safe), and logs the message to the console.
     * This method is aware that the command sender might be the console sender and avoids
     * displaying the message twice in this case.
     *
     * @param sender the sender to inform
     * @param message the message to log and send
     */
    public static void logAndSendMessage(CommandSender sender, String message) {
        logger.info(message);
        // Make sure sender is not console user, which will see the message from ConsoleLogger already
        if (sender != null && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(message);
        }
    }

    /**
     * Sends a warning to the given sender (null safe), and logs the warning to the console.
     * This method is aware that the command sender might be the console sender and avoids
     * displaying the message twice in this case.
     *
     * @param sender the sender to inform
     * @param message the warning to log and send
     */
    public static void logAndSendWarning(CommandSender sender, String message) {
        logger.warning(message);
        // Make sure sender is not console user, which will see the message from ConsoleLogger already
        if (sender != null && !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(ChatColor.RED + message);
        }
    }

    /**
     * Null-safe way to check whether a collection is empty or not.
     *
     * @param coll The collection to verify
     * @return True if the collection is null or empty, false otherwise
     */
    public static boolean isCollectionEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    /**
     * Returns whether the given email is empty or equal to the standard "undefined" email address.
     *
     * @param email the email to check
     *
     * @return true if the email is empty
     */
    public static boolean isEmailEmpty(String email) {
        return StringUtils.isBlank(email) || "your@email.com".equalsIgnoreCase(email);
    }

    // ===================== 修改后的版本解析（方案一，无final） =====================
    // 保存解析出的版本号数组（仅内部可能使用）
    private static String[] serverVersion;
    // 对外暴露的主版本号和次版本号，若解析失败则默认设为 1.8
    public static int MAJOR_VERSION;
    public static int MINOR_VERSION;
    // 第一版本号（通常为 1），若不需要可设为私有
    private static int FIRST_VERSION;

    static {
        String bukkitVersion = Bukkit.getServer().getBukkitVersion();
        String versionPart;
        try {
            int dashIdx = bukkitVersion.indexOf("-");
            versionPart = (dashIdx >= 0) ? bukkitVersion.substring(0, dashIdx) : bukkitVersion;
            String[] parts = versionPart.split("\\.");
            int first = Integer.parseInt(parts[0]);
            int major = Integer.parseInt(parts[1]);
            int minor = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 0;
            serverVersion = parts;
            FIRST_VERSION = first;
            MAJOR_VERSION = major;
            MINOR_VERSION = minor;
        } catch (Exception e) {
            // 解析失败时使用默认值 1.8，并记录警告
            ConsoleLogger warnLogger = ConsoleLoggerFactory.get(Utils.class);
            warnLogger.warning("Failed to parse server version from Bukkit version: " + bukkitVersion
                               + ". Using default 1.8. This may affect some features.");
            serverVersion = new String[]{"1", "8", "0"};
            FIRST_VERSION = 1;
            MAJOR_VERSION = 8;
            MINOR_VERSION = 0;
        }
    }
    // ====================================================================
}