package fr.xephi.authme.service.hook.papi;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.api.v3.AuthMeApi;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * AuthMe PlaceholderAPI expansion class.
 * @author Kobe 8
 */
public class AuthMeExpansion extends PlaceholderExpansion {
    private final Settings settings = AuthMe.settings;
    @Override
    public @NotNull String getIdentifier() {
        return "authme";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HaHaWTH";
    }

    @Override
    public @NotNull String getVersion() {
        return AuthMe.getPluginVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (!settings.getProperty(HooksSettings.PLACEHOLDER_API)) return null;
        AuthMeApi authMeApi = AuthMeApi.getInstance();
        if (authMeApi == null) return null;
        if (params.equalsIgnoreCase("version")) {
            return getVersion();
        }
        if (params.equalsIgnoreCase("is_registered")) {
            if (player != null) {
                Player onlinePlayer = player.getPlayer();
                if (onlinePlayer != null) {
                    return String.valueOf(authMeApi.isRegistered(onlinePlayer.getName()));
                }
            }
        }
        if (params.equalsIgnoreCase("is_authenticated")) {
            if (player != null) {
                Player onlinePlayer = player.getPlayer();
                if (onlinePlayer != null) {
                    return String.valueOf(authMeApi.isAuthenticated(onlinePlayer));
                }
            }
        }
        if (params.equalsIgnoreCase("last_login_time")) {
            if (player != null) {
                Player onlinePlayer = player.getPlayer();
                if (onlinePlayer != null) {
                    return authMeApi.getLastLoginTime(onlinePlayer.getName()).toString();
                }
            }
        }
        return null;
    }
}
