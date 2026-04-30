package me.akif.customcommands.manager;

import me.akif.customcommands.AkifsCustomCommands;
import me.akif.customcommands.command.DynamicCommand;
import me.akif.customcommands.model.CustomCommand;
import me.akif.customcommands.util.DiscordWebhook;
import me.akif.customcommands.util.MessageUtil;
import me.akif.customcommands.util.PlaceholderUtil;
import me.akif.customcommands.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Owns both the dynamic registration of custom commands into the
 * Bukkit CommandMap and the runtime checks that decide whether a
 * player is allowed to use them.
 */
public class CommandManager {

    private static final String FALLBACK_PREFIX = "akifscc";

    private final AkifsCustomCommands plugin;
    private final Set<String> registeredCommands = new HashSet<>();
    private CommandMap commandMap;

    public CommandManager(AkifsCustomCommands plugin) {
        this.plugin = plugin;
    }

    private CommandMap getCommandMap() {
        if (commandMap != null) return commandMap;
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            commandMap = (CommandMap) field.get(Bukkit.getServer());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not access the server CommandMap", e);
        }
        return commandMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands() {
        try {
            CommandMap map = getCommandMap();
            if (map instanceof SimpleCommandMap) {
                Field f = SimpleCommandMap.class.getDeclaredField("knownCommands");
                f.setAccessible(true);
                return (Map<String, Command>) f.get(map);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not access knownCommands (unsupported server version?)", e);
        }
        return null;
    }

    public void registerAll() {
        unregisterAll();
        for (CustomCommand cc : plugin.getConfigManager().getCommands().values()) {
            register(cc);
        }
    }

    public void register(CustomCommand cc) {
        if (cc == null) return;
        CommandMap map = getCommandMap();
        if (map == null) return;
        DynamicCommand dyn = new DynamicCommand(plugin, cc.getName());
        boolean ok = map.register(FALLBACK_PREFIX, dyn);
        registeredCommands.add(cc.getName());
        if (!ok && plugin.getConfigManager().isLogActions()) {
            plugin.pluginLogger().info("/" + cc.getName()
                    + " collided with another plugin - it is also reachable as /"
                    + FALLBACK_PREFIX + ":" + cc.getName());
        }
        syncCommandsToPlayers();
    }

    public void unregister(String name) {
        if (name == null) return;
        Map<String, Command> known = getKnownCommands();
        if (known == null) return;
        String lower = name.toLowerCase(Locale.ROOT);
        known.values().removeIf(cmd -> cmd instanceof DynamicCommand && cmd.getName().equalsIgnoreCase(lower));
        known.entrySet().removeIf(e -> {
            String key = e.getKey();
            return key.equalsIgnoreCase(lower) || key.equalsIgnoreCase(FALLBACK_PREFIX + ":" + lower);
        });
        registeredCommands.remove(lower);
        syncCommandsToPlayers();
    }

    public void unregisterAll() {
        Map<String, Command> known = getKnownCommands();
        if (known != null) {
            known.values().removeIf(cmd -> cmd instanceof DynamicCommand);
        }
        registeredCommands.clear();
    }

    private void syncCommandsToPlayers() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.updateCommands();
            } catch (Throwable ignored) {
                // updateCommands isn't available on every server fork - ignore.
            }
        }
    }

    public Set<String> getRegisteredCommandNames() {
        return registeredCommands;
    }

    /**
     * Validates every condition (enabled, expired, permission, limits,
     * cooldown) and dispatches the configured console commands when
     * the player is allowed.
     */
    public void handleCustomCommand(Player player, String commandName) {
        ConfigManager cfg = plugin.getConfigManager();
        DataManager data = plugin.getDataManager();

        CustomCommand cc = cfg.getCommand(commandName);
        if (cc == null) return;

        if (!cc.isEnabled()) {
            sendCommandMessage(player, cc, "disabled", null);
            return;
        }

        if (cc.isExpired()) {
            sendCommandMessage(player, cc, "expired", null);
            return;
        }

        String perm = cc.getPermission();
        if (perm != null && !perm.isEmpty() && !player.hasPermission(perm)) {
            String key = cc.getPermissionMessageKey();
            String raw = plugin.getLanguageManager().get(key);
            if (raw != null && !raw.isEmpty()) {
                Map<String, String> ph = PlaceholderUtil.playerPlaceholders(player);
                ph.put("%command%", cc.getName());
                MessageUtil.send(plugin, player, MessageUtil.applyPlaceholders(raw, ph));
            }
            return;
        }

        int totalLimit = cc.getUsageLimitTotal();
        int totalUsed = data.getTotalUsage(cc.getName());
        if (totalLimit > 0 && totalUsed >= totalLimit) {
            sendCommandMessage(player, cc, "limit-reached", null);
            return;
        }

        int playerLimit = cc.getUsageLimitPerPlayer();
        int playerUsed = data.getPlayerUsage(player.getUniqueId(), cc.getName());
        if (playerLimit > 0 && playerUsed >= playerLimit) {
            sendCommandMessage(player, cc, "already-used", null);
            return;
        }

        long cdLeft = data.getCooldownLeft(player.getUniqueId(), cc.getName(), cc.getCooldownSeconds());
        if (cdLeft > 0L) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%time_left%", TimeUtil.formatSeconds(cdLeft));
            sendCommandMessage(player, cc, "cooldown", ph);
            return;
        }

        executeConsoleCommands(player, cc);
        data.recordUsage(player.getUniqueId(), player.getName(), cc.getName());
        sendCommandMessage(player, cc, "success", null);
        broadcastUsage(player, cc);

        int totalUsedAfter = data.getTotalUsage(cc.getName());
        int playerUsedAfter = data.getPlayerUsage(player.getUniqueId(), cc.getName());
        DiscordWebhook.sendUsageEmbed(plugin, player, cc, totalUsedAfter, playerUsedAfter);

        if (cfg.isLogActions()) {
            plugin.pluginLogger().info(player.getName() + " used /" + cc.getName());
        }
    }

    /**
     * Sends every line of the command's <code>broadcast</code> list to all
     * online players. Placeholders and color codes are honored. If the list
     * is empty, nothing happens (so the admin keeps full control).
     */
    private void broadcastUsage(Player player, CustomCommand cc) {
        List<String> lines = cc.getBroadcastMessages();
        if (lines == null || lines.isEmpty()) return;

        Map<String, String> placeholders = PlaceholderUtil.playerPlaceholders(player);
        placeholders.put("%command%", cc.getName());
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            String rendered = MessageUtil.color(MessageUtil.applyPlaceholders(line, placeholders));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(rendered);
            }
            // Mirror to console so admins can see what was broadcast
            Bukkit.getConsoleSender().sendMessage(rendered);
        }
    }

    private void executeConsoleCommands(Player player, CustomCommand cc) {
        Map<String, String> placeholders = PlaceholderUtil.playerPlaceholders(player);
        List<String> consoleCommands = cc.getConsoleCommands();
        if (consoleCommands == null || consoleCommands.isEmpty()) return;

        for (String raw : consoleCommands) {
            if (raw == null || raw.isEmpty()) continue;
            String parsed = PlaceholderUtil.apply(raw, placeholders);
            if (plugin.getConfigManager().isLogActions()) {
                plugin.pluginLogger().info("Executed console command: " + parsed);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Failed to dispatch console command: " + parsed, t);
            }
        }
    }

    /**
     * Sends a command message to the player. The lookup order is:
     *   1. Per-command override in config.yml ("messages.<key>")
     *   2. Default from the active language file ("command.<key>")
     */
    private void sendCommandMessage(Player player, CustomCommand cc, String key, Map<String, String> extra) {
        Map<String, String> placeholders = PlaceholderUtil.playerPlaceholders(player);
        placeholders.put("%command%", cc.getName());
        if (extra != null) placeholders.putAll(extra);

        List<String> override = cc.getMessageOverride(key);
        List<String> lines;
        if (override != null && !override.isEmpty()) {
            lines = override;
        } else {
            lines = plugin.getLanguageManager().getList("command." + key);
        }
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            String replaced = MessageUtil.applyPlaceholders(line, placeholders);
            MessageUtil.send(plugin, player, replaced);
        }
    }
}
