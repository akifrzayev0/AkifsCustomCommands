package me.akif.customcommands.manager;

import me.akif.customcommands.AkifsCustomCommands;
import me.akif.customcommands.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists per-player and per-command usage data in data.yml.
 * The file survives server restarts so cooldowns, total counters
 * and per-player limits are kept consistent.
 */
public class DataManager {

    private final AkifsCustomCommands plugin;
    private final File dataFile;
    private FileConfiguration data;

    public DataManager(AkifsCustomCommands plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create data.yml", e);
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        if (data == null) return;
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save data.yml", e);
        }
    }

    private String pp(UUID uuid, String command, String field) {
        return "players." + uuid + ".commands." + command.toLowerCase(Locale.ROOT) + "." + field;
    }

    private String cp(String command, String field) {
        return "commands." + command.toLowerCase(Locale.ROOT) + "." + field;
    }

    public int getPlayerUsage(UUID uuid, String command) {
        return data.getInt(pp(uuid, command, "used"), 0);
    }

    public long getPlayerLastUsed(UUID uuid, String command) {
        return data.getLong(pp(uuid, command, "last-used"), 0L);
    }

    public int getTotalUsage(String command) {
        return data.getInt(cp(command, "total-used"), 0);
    }

    public long getCommandCreatedAt(String command) {
        return data.getLong(cp(command, "created-at"), 0L);
    }

    public void setCommandCreatedAt(String command, long createdAt) {
        data.set(cp(command, "created-at"), createdAt);
        save();
    }

    public void recordUsage(UUID uuid, String playerName, String command) {
        String name = command.toLowerCase(Locale.ROOT);
        int used = getPlayerUsage(uuid, name);
        data.set("players." + uuid + ".name", playerName);
        data.set(pp(uuid, name, "used"), used + 1);
        data.set(pp(uuid, name, "last-used"), TimeUtil.nowSeconds());
        data.set(cp(name, "total-used"), getTotalUsage(name) + 1);
        if (getCommandCreatedAt(name) <= 0L) {
            data.set(cp(name, "created-at"), TimeUtil.nowSeconds());
        }
        save();
    }

    public void resetCommand(String command) {
        String name = command.toLowerCase(Locale.ROOT);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                data.set("players." + uuid + ".commands." + name, null);
            }
        }
        data.set(cp(name, "total-used"), 0);
        save();
    }

    public boolean resetPlayer(UUID uuid, String command) {
        if (!data.contains("players." + uuid + ".commands." + command.toLowerCase(Locale.ROOT))) {
            return false;
        }
        int currentTotal = getTotalUsage(command);
        int playerUsed = getPlayerUsage(uuid, command);
        data.set("players." + uuid + ".commands." + command.toLowerCase(Locale.ROOT), null);
        data.set(cp(command, "total-used"), Math.max(0, currentTotal - playerUsed));
        save();
        return true;
    }

    public void deleteCommand(String command) {
        String name = command.toLowerCase(Locale.ROOT);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                data.set("players." + uuid + ".commands." + name, null);
            }
        }
        data.set("commands." + name, null);
        save();
    }

    public long getCooldownLeft(UUID uuid, String command, long cooldownSeconds) {
        if (cooldownSeconds <= 0L) return 0L;
        long lastUsed = getPlayerLastUsed(uuid, command);
        if (lastUsed <= 0L) return 0L;
        long elapsed = TimeUtil.nowSeconds() - lastUsed;
        long left = cooldownSeconds - elapsed;
        return Math.max(0L, left);
    }

    public Map<UUID, String> getKnownPlayerNames() {
        Map<UUID, String> result = new HashMap<>();
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return result;
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String name = players.getString(key + ".name", null);
                if (name != null) {
                    result.put(uuid, name);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }
}
