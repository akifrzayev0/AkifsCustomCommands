package me.akif.customcommands;

import me.akif.customcommands.command.AdminCommand;
import me.akif.customcommands.manager.CommandManager;
import me.akif.customcommands.manager.ConfigManager;
import me.akif.customcommands.manager.DataManager;
import me.akif.customcommands.manager.LanguageManager;
import me.akif.customcommands.util.PluginLogger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point. Spins up the four managers, registers the
 * /cac admin command and pushes every custom command from config.yml
 * into Bukkit's command map.
 */
public class AkifsCustomCommands extends JavaPlugin {

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private DataManager dataManager;
    private CommandManager commandManager;
    private PluginLogger pluginLogger;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.dataManager = new DataManager(this);
        this.commandManager = new CommandManager(this);
        this.pluginLogger = new PluginLogger(this);

        configManager.load();
        languageManager.load(configManager.getLanguage());
        dataManager.load();
        commandManager.registerAll();

        PluginCommand cac = getCommand("cac");
        if (cac != null) {
            AdminCommand admin = new AdminCommand(this);
            cac.setExecutor(admin);
            cac.setTabCompleter(admin);
        } else {
            getLogger().severe("/cac command was not declared in plugin.yml!");
        }

        pluginLogger.info("Enabled. Loaded "
                + configManager.getCommands().size() + " custom command(s), language="
                + languageManager.getActiveLanguage());
    }

    @Override
    public void onDisable() {
        if (commandManager != null) commandManager.unregisterAll();
        if (dataManager != null) dataManager.save();
        if (pluginLogger != null) pluginLogger.info("Disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public PluginLogger pluginLogger() {
        return pluginLogger;
    }
}
