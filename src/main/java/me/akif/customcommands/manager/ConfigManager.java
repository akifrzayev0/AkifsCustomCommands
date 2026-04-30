package me.akif.customcommands.manager;

import me.akif.customcommands.AkifsCustomCommands;
import me.akif.customcommands.model.CustomCommand;
import me.akif.customcommands.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads, caches and saves both config.yml (global settings) and
 * commands.yml (custom command definitions). On first run the
 * plugin auto-creates both files; if an older config.yml still has
 * a `commands:` section, it is migrated to commands.yml on load.
 */
public class ConfigManager {

    private final AkifsCustomCommands plugin;

    private final File configFile;
    private FileConfiguration config;

    private final File commandsFile;
    private FileConfiguration commandsConfig;

    private String prefix = "<gradient:#3FFF5C:#5CC8FF>&lAkifsCustomCommands</gradient> &8» &r";
    private String language = "en";
    private boolean usePrefixInConsole = true;
    private boolean logActions = true;

    private boolean discordEnabled = false;
    private String discordWebhookUrl = "";
    private String discordUsername = "AkifsCustomCommands";
    private String discordAvatarUrl = "";
    private String discordThumbnailUrl = "https://mc-heads.net/avatar/%player_uuid%/128";
    private String discordEmbedColor = "#3FFF5C";
    private boolean discordTimestamp = true;

    private final Map<String, CustomCommand> commands = new HashMap<>();

    public ConfigManager(AkifsCustomCommands plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.commandsFile = new File(plugin.getDataFolder(), "commands.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);

        if (!commandsFile.exists()) {
            plugin.saveResource("commands.yml", false);
        }
        this.commandsConfig = YamlConfiguration.loadConfiguration(commandsFile);

        migrateLegacyCommandsFromConfig();

        parseGlobals();
        parseCommands();
    }

    /**
     * If config.yml still contains a `commands:` section (old layout),
     * copy every entry into commands.yml and strip it from config.yml.
     * This keeps existing installs working without manual edits.
     */
    private void migrateLegacyCommandsFromConfig() {
        ConfigurationSection legacy = config.getConfigurationSection("commands");
        if (legacy == null) return;

        boolean migratedAny = false;
        ConfigurationSection target = commandsConfig.getConfigurationSection("commands");
        if (target == null) {
            target = commandsConfig.createSection("commands");
        }

        for (String key : legacy.getKeys(false)) {
            if (target.isConfigurationSection(key)) continue;
            ConfigurationSection from = legacy.getConfigurationSection(key);
            if (from == null) continue;

            ConfigurationSection to = target.createSection(key);
            for (String childKey : from.getKeys(true)) {
                Object value = from.get(childKey);
                if (value instanceof ConfigurationSection) continue;
                to.set(childKey, value);
            }
            migratedAny = true;
        }

        config.set("commands", null);

        try {
            config.save(configFile);
            if (migratedAny) commandsConfig.save(commandsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to migrate commands from config.yml", e);
            return;
        }

        if (migratedAny) {
            plugin.getLogger().info("Migrated custom commands from config.yml to commands.yml.");
        }
    }

    public void save() {
        if (config != null) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save config.yml", e);
            }
        }
        saveCommandsFile();
    }

    private void saveCommandsFile() {
        if (commandsConfig == null) return;
        try {
            commandsConfig.save(commandsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save commands.yml", e);
        }
    }

    private void parseGlobals() {
        prefix = config.getString("prefix", "<gradient:#3FFF5C:#5CC8FF>&lAkifsCustomCommands</gradient> &8» &r");
        language = config.getString("language", "en");
        usePrefixInConsole = config.getBoolean("use-prefix-in-console", true);
        logActions = config.getBoolean("log-actions", true);

        discordEnabled = config.getBoolean("discord.enabled", false);
        discordWebhookUrl = config.getString("discord.webhook-url", "");
        discordUsername = config.getString("discord.username", "AkifsCustomCommands");
        discordAvatarUrl = config.getString("discord.avatar-url", "");
        discordThumbnailUrl = config.getString("discord.thumbnail-url",
                "https://mc-heads.net/avatar/%player_uuid%/128");
        discordEmbedColor = config.getString("discord.embed-color", "#3FFF5C");
        discordTimestamp = config.getBoolean("discord.timestamp", true);
    }

    private void parseCommands() {
        commands.clear();
        ConfigurationSection commandsSection = commandsConfig.getConfigurationSection("commands");
        if (commandsSection == null) return;

        boolean dirty = false;
        for (String key : commandsSection.getKeys(false)) {
            ConfigurationSection cmdSection = commandsSection.getConfigurationSection(key);
            if (cmdSection == null) continue;

            String name = key.toLowerCase(Locale.ROOT);
            CustomCommand cc = new CustomCommand(name);
            cc.setEnabled(cmdSection.getBoolean("enabled", true));
            cc.setPermission(cmdSection.getString("permission", ""));
            cc.setPermissionMessageKey(cmdSection.getString("permission-message-key", "command.no-permission"));
            cc.setUsageLimitTotal(cmdSection.getInt("usage-limit-total", -1));
            cc.setUsageLimitPerPlayer(cmdSection.getInt("usage-limit-per-player", -1));
            cc.setCooldownSeconds(cmdSection.getLong("cooldown-seconds", 0L));
            cc.setActiveTimeSeconds(cmdSection.getLong("active-time-seconds", 0L));

            long createdAt = cmdSection.getLong("created-at", 0L);
            if (createdAt <= 0L) {
                createdAt = TimeUtil.nowSeconds();
                cmdSection.set("created-at", createdAt);
                dirty = true;
            }
            cc.setCreatedAt(createdAt);
            cc.setConsoleCommands(new ArrayList<>(cmdSection.getStringList("console-commands")));
            cc.setBroadcastMessages(new ArrayList<>(cmdSection.getStringList("broadcast")));

            ConfigurationSection messages = cmdSection.getConfigurationSection("messages");
            if (messages != null) {
                for (String msgKey : messages.getKeys(false)) {
                    cc.setMessageOverride(msgKey, new ArrayList<>(messages.getStringList(msgKey)));
                }
            }

            commands.put(name, cc);
        }
        if (dirty) saveCommandsFile();
    }

    public void writeCommand(CustomCommand cc) {
        String path = "commands." + cc.getName();
        commandsConfig.set(path + ".enabled", cc.isEnabled());
        commandsConfig.set(path + ".permission", cc.getPermission());
        commandsConfig.set(path + ".permission-message-key", cc.getPermissionMessageKey());
        commandsConfig.set(path + ".usage-limit-total", cc.getUsageLimitTotal());
        commandsConfig.set(path + ".usage-limit-per-player", cc.getUsageLimitPerPlayer());
        commandsConfig.set(path + ".cooldown-seconds", cc.getCooldownSeconds());
        commandsConfig.set(path + ".active-time-seconds", cc.getActiveTimeSeconds());
        commandsConfig.set(path + ".created-at", cc.getCreatedAt());
        commandsConfig.set(path + ".console-commands", cc.getConsoleCommands());
        commandsConfig.set(path + ".broadcast", cc.getBroadcastMessages());

        // Wipe and re-write overrides so removed entries actually disappear.
        commandsConfig.set(path + ".messages", null);
        for (Map.Entry<String, java.util.List<String>> entry : cc.getMessageOverrides().entrySet()) {
            commandsConfig.set(path + ".messages." + entry.getKey(), entry.getValue());
        }
        saveCommandsFile();
    }

    public void deleteCommand(String name) {
        commandsConfig.set("commands." + name.toLowerCase(Locale.ROOT), null);
        commands.remove(name.toLowerCase(Locale.ROOT));
        saveCommandsFile();
    }

    public CustomCommand registerNewCommand(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        CustomCommand cc = new CustomCommand(key);
        commands.put(key, cc);
        writeCommand(cc);
        return cc;
    }

    public CustomCommand getCommand(String name) {
        if (name == null) return null;
        return commands.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean hasCommand(String name) {
        if (name == null) return false;
        return commands.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, CustomCommand> getCommands() {
        return commands;
    }

    public String getPrefix() {
        return prefix == null ? "" : prefix;
    }

    public String getLanguage() {
        return language == null ? "en" : language;
    }

    public boolean isUsePrefixInConsole() {
        return usePrefixInConsole;
    }

    public boolean isLogActions() {
        return logActions;
    }

    public FileConfiguration getRawConfig() {
        return config;
    }

    public FileConfiguration getRawCommandsConfig() {
        return commandsConfig;
    }

    public boolean isDiscordEnabled() {
        return discordEnabled;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl == null ? "" : discordWebhookUrl;
    }

    public String getDiscordUsername() {
        return discordUsername == null ? "" : discordUsername;
    }

    public String getDiscordAvatarUrl() {
        return discordAvatarUrl == null ? "" : discordAvatarUrl;
    }

    public String getDiscordThumbnailUrl() {
        return discordThumbnailUrl == null ? "" : discordThumbnailUrl;
    }

    public String getDiscordEmbedColor() {
        return discordEmbedColor == null ? "#3FFF5C" : discordEmbedColor;
    }

    public boolean isDiscordTimestamp() {
        return discordTimestamp;
    }
}
