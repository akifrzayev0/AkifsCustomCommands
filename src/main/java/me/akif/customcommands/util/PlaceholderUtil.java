package me.akif.customcommands.util;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the small placeholder set used by console commands and
 * messages. Kept intentionally lightweight so the plugin doesn't
 * require PlaceholderAPI as a dependency.
 */
public final class PlaceholderUtil {

    private PlaceholderUtil() {
    }

    public static Map<String, String> playerPlaceholders(Player player) {
        Map<String, String> map = new HashMap<>();
        if (player == null) return map;
        map.put("%player_name%", player.getName());
        map.put("%player_uuid%", player.getUniqueId().toString());
        map.put("%player_displayname%", player.getDisplayName());
        return map;
    }

    public static String apply(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty()) return "";
        if (placeholders == null || placeholders.isEmpty()) return input;
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public static String apply(String input, Player player) {
        return apply(input, playerPlaceholders(player));
    }
}
