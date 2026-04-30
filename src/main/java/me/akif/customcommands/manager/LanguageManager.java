package me.akif.customcommands.manager;

import me.akif.customcommands.AkifsCustomCommands;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Loads the active language file from the plugin's languages/ folder.
 * <p>
 * Bundled defaults (en.yml, az.yml) are extracted on first start so
 * server owners can edit them. Adding a brand new translation is as
 * simple as dropping <code>fr.yml</code> into the folder and changing
 * the <code>language</code> key in config.yml.
 */
public class LanguageManager {

    private static final String DEFAULT_LANGUAGE = "en";

    private final AkifsCustomCommands plugin;
    private final File languagesFolder;

    private FileConfiguration language;
    private FileConfiguration fallback;
    private String activeLanguage = DEFAULT_LANGUAGE;

    public LanguageManager(AkifsCustomCommands plugin) {
        this.plugin = plugin;
        this.languagesFolder = new File(plugin.getDataFolder(), "languages");
    }

    public void load(String requestedLanguage) {
        if (!languagesFolder.exists() && !languagesFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create languages folder.");
        }

        // Always make sure the bundled defaults are present on disk.
        saveBundledLanguage("en.yml");
        saveBundledLanguage("az.yml");
        saveBundledLanguage("tr.yml");

        this.fallback = loadOrDefault(DEFAULT_LANGUAGE);

        String lang = requestedLanguage == null || requestedLanguage.isEmpty()
                ? DEFAULT_LANGUAGE
                : requestedLanguage.toLowerCase();

        File target = new File(languagesFolder, lang + ".yml");
        if (!target.exists()) {
            plugin.getLogger().warning("Language file '" + lang + ".yml' was not found, falling back to en.yml.");
            this.activeLanguage = DEFAULT_LANGUAGE;
            this.language = fallback;
            return;
        }

        this.activeLanguage = lang;
        this.language = YamlConfiguration.loadConfiguration(target);
    }

    private FileConfiguration loadOrDefault(String name) {
        File file = new File(languagesFolder, name + ".yml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        // Read embedded resource if it exists, otherwise return an empty config.
        try (InputStream in = plugin.getResource("languages/" + name + ".yml")) {
            if (in != null) {
                return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read embedded language: " + name, e);
        }
        return new YamlConfiguration();
    }

    private void saveBundledLanguage(String fileName) {
        File target = new File(languagesFolder, fileName);
        if (target.exists()) return;
        try (InputStream in = plugin.getResource("languages/" + fileName)) {
            if (in == null) return;
            Files.copy(in, target.toPath());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save bundled language: " + fileName, e);
        }
    }

    public String getActiveLanguage() {
        return activeLanguage;
    }

    /**
     * Returns the message at the given key. Falls back to en.yml,
     * and finally to the key itself if nothing was found.
     */
    public String get(String key) {
        if (language != null && language.isString(key)) {
            return language.getString(key, key);
        }
        if (fallback != null && fallback.isString(key)) {
            return fallback.getString(key, key);
        }
        return key;
    }

    /**
     * Same as {@link #get(String)} but for list-style messages.
     */
    public List<String> getList(String key) {
        if (language != null && language.isList(key)) {
            return language.getStringList(key);
        }
        if (fallback != null && fallback.isList(key)) {
            return fallback.getStringList(key);
        }
        // Some keys are stored as a single string but may be requested as a list.
        if (language != null && language.isString(key)) {
            return Collections.singletonList(language.getString(key, ""));
        }
        if (fallback != null && fallback.isString(key)) {
            return Collections.singletonList(fallback.getString(key, ""));
        }
        return Collections.emptyList();
    }
}
