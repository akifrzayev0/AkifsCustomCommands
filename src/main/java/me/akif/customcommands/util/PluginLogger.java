package me.akif.customcommands.util;

import me.akif.customcommands.AkifsCustomCommands;
import org.bukkit.Bukkit;

/**
 * Wraps console output so the plugin honors the configurable
 * <code>prefix</code> from config.yml. When
 * <code>use-prefix-in-console</code> is enabled, log lines look like:
 * <pre>
 *   &amp;a&amp;lAkifsCustomCommands &amp;» Some action happened
 * </pre>
 * Otherwise, the bundled Java logger is used and Bukkit prepends
 * the regular <code>[AkifsCustomCommands]</code> tag.
 */
public final class PluginLogger {

    private final AkifsCustomCommands plugin;

    public PluginLogger(AkifsCustomCommands plugin) {
        this.plugin = plugin;
    }

    public void info(String message) {
        if (message == null) return;
        if (plugin.getConfigManager() != null && plugin.getConfigManager().isUsePrefixInConsole()) {
            Bukkit.getConsoleSender().sendMessage(MessageUtil.formatWithPrefix(plugin, message));
        } else {
            plugin.getLogger().info(stripColors(message));
        }
    }

    public void warn(String message) {
        if (message == null) return;
        if (plugin.getConfigManager() != null && plugin.getConfigManager().isUsePrefixInConsole()) {
            Bukkit.getConsoleSender().sendMessage(MessageUtil.formatWithPrefix(plugin, "&e" + message));
        } else {
            plugin.getLogger().warning(stripColors(message));
        }
    }

    public void error(String message) {
        if (message == null) return;
        if (plugin.getConfigManager() != null && plugin.getConfigManager().isUsePrefixInConsole()) {
            Bukkit.getConsoleSender().sendMessage(MessageUtil.formatWithPrefix(plugin, "&c" + message));
        } else {
            plugin.getLogger().severe(stripColors(message));
        }
    }

    private String stripColors(String input) {
        if (input == null) return "";
        return input
                .replaceAll("</?gradient(:#[A-Fa-f0-9]{6})*>", "")
                .replaceAll("&#[A-Fa-f0-9]{6}", "")
                .replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}
