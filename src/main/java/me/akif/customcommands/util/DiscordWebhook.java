package me.akif.customcommands.util;

import me.akif.customcommands.AkifsCustomCommands;
import me.akif.customcommands.manager.ConfigManager;
import me.akif.customcommands.manager.LanguageManager;
import me.akif.customcommands.model.CustomCommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Async Discord webhook dispatcher. Whenever a player successfully
 * runs a custom command, the plugin builds a localized embed that
 * shows who used what command, when they used it, the public
 * broadcast text (color-stripped) and the executed console
 * commands. The HTTP POST happens on a background thread so the
 * main server thread is never blocked.
 *
 * <p>JSON is hand-built with proper escaping to avoid pulling in
 * an extra dependency.</p>
 */
public final class DiscordWebhook {

    private static final String USER_AGENT = "AkifsCustomCommands/1.0";

    private DiscordWebhook() {
    }

    /**
     * Sends a "command used" log embed for the given player and
     * custom command. Errors are logged but never propagated to
     * the caller.
     *
     * @param plugin      plugin instance
     * @param playerName  the in-game name of the player
     * @param playerUuid  the player's UUID
     * @param displayName the player's display name (may equal playerName)
     * @param cc          the custom command that was used
     * @param totalUsed   how many times the command has been used in total
     *                    (after this current use is counted)
     * @param playerUsed  how many times this player has used the command
     *                    (after this current use is counted)
     * @param onResult    optional callback invoked on the main thread with
     *                    {@code null} on success or an error message string
     */
    public static void sendUsageEmbed(AkifsCustomCommands plugin,
                                      String playerName,
                                      UUID playerUuid,
                                      String displayName,
                                      CustomCommand cc,
                                      int totalUsed,
                                      int playerUsed,
                                      Consumer<String> onResult) {
        if (plugin == null || cc == null) return;
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isDiscordEnabled()) {
            if (onResult != null) onResult.accept("disabled");
            return;
        }
        String webhookUrl = cfg.getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            if (onResult != null) onResult.accept("not-configured");
            return;
        }

        LanguageManager lang = plugin.getLanguageManager();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player_name%", safe(playerName));
        placeholders.put("%player_uuid%", playerUuid == null ? "" : playerUuid.toString());
        placeholders.put("%player_displayname%", safe(displayName));
        placeholders.put("%command%", cc.getName());

        String timeFormat = lang.get("discord.embed.time-format");
        if (timeFormat == null || timeFormat.isEmpty() || timeFormat.equals("discord.embed.time-format")) {
            timeFormat = "yyyy-MM-dd HH:mm:ss z";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(timeFormat);
        sdf.setTimeZone(TimeZone.getDefault());
        placeholders.put("%time%", sdf.format(new Date()));

        String title = applyPlaceholders(lang.get("discord.embed.title"), placeholders);
        String description = applyPlaceholders(lang.get("discord.embed.description"), placeholders);
        String fieldPlayer = lang.get("discord.embed.field-player");
        String fieldCommand = lang.get("discord.embed.field-command");
        String fieldTime = lang.get("discord.embed.field-time");
        String fieldBroadcast = lang.get("discord.embed.field-broadcast");
        String fieldCommands = lang.get("discord.embed.field-commands");
        String fieldRemaining = lang.get("discord.embed.field-remaining");
        String footer = applyPlaceholders(lang.get("discord.embed.footer"), placeholders);

        String username = applyPlaceholders(cfg.getDiscordUsername(), placeholders);
        String avatarUrl = applyPlaceholders(cfg.getDiscordAvatarUrl(), placeholders);
        String thumbnailUrl = applyPlaceholders(cfg.getDiscordThumbnailUrl(), placeholders);

        int color = parseHexColor(cfg.getDiscordEmbedColor());

        String broadcastText = stripBroadcastLines(cc.getBroadcastMessages(), placeholders);
        String consoleCmdsText = formatConsoleCommands(cc.getConsoleCommands(), placeholders);
        String remainingText = buildRemainingText(lang, placeholders, cc, totalUsed, playerUsed);

        StringBuilder embed = new StringBuilder();
        embed.append('{');
        appendString(embed, "title", title);
        embed.append(',');
        appendString(embed, "description", description);
        embed.append(",\"color\":").append(color);

        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            embed.append(",\"thumbnail\":{");
            appendString(embed, "url", thumbnailUrl);
            embed.append('}');
        }

        StringBuilder fields = new StringBuilder();
        boolean any = false;
        any |= appendField(fields, fieldPlayer, safe(playerName), true, any);
        any |= appendField(fields, fieldCommand, "/" + cc.getName(), true, any);
        any |= appendField(fields, fieldTime, placeholders.get("%time%"), true, any);
        any |= appendField(fields, fieldRemaining, remainingText, false, any);
        any |= appendField(fields, fieldBroadcast, broadcastText, false, any);
        any |= appendField(fields, fieldCommands, consoleCmdsText, false, any);
        if (any) {
            embed.append(",\"fields\":[").append(fields).append(']');
        }

        if (footer != null && !footer.isEmpty()) {
            embed.append(",\"footer\":{");
            appendString(embed, "text", footer);
            embed.append('}');
        }

        if (cfg.isDiscordTimestamp()) {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            embed.append(",\"timestamp\":\"").append(iso.format(new Date())).append('"');
        }
        embed.append('}');

        StringBuilder payload = new StringBuilder("{");
        boolean prependComma = false;
        if (username != null && !username.isEmpty()) {
            appendString(payload, "username", username);
            prependComma = true;
        }
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (prependComma) payload.append(',');
            appendString(payload, "avatar_url", avatarUrl);
            prependComma = true;
        }
        if (prependComma) payload.append(',');
        payload.append("\"embeds\":[").append(embed).append("]}");

        final String body = payload.toString();
        final String url = webhookUrl;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String error = post(url, body);
            if (error != null) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to deliver Discord webhook for /" + cc.getName() + ": " + error);
            }
            if (onResult != null) {
                final String e = error;
                Bukkit.getScheduler().runTask(plugin, () -> onResult.accept(e));
            }
        });
    }

    /**
     * Convenience overload for use from CommandManager - it has a Player
     * available, but we extract just the data we need so the async task
     * never touches Bukkit objects from another thread.
     */
    public static void sendUsageEmbed(AkifsCustomCommands plugin,
                                      org.bukkit.entity.Player player,
                                      CustomCommand cc,
                                      int totalUsed,
                                      int playerUsed) {
        if (player == null) return;
        sendUsageEmbed(plugin, player.getName(), player.getUniqueId(),
                player.getDisplayName(), cc, totalUsed, playerUsed, null);
    }

    /**
     * Convenience overload for offline test commands.
     */
    public static void sendUsageEmbed(AkifsCustomCommands plugin,
                                      OfflinePlayer offline,
                                      CustomCommand cc,
                                      int totalUsed,
                                      int playerUsed,
                                      Consumer<String> onResult) {
        if (offline == null) {
            sendUsageEmbed(plugin, "Tester", new UUID(0L, 0L), "Tester",
                    cc, totalUsed, playerUsed, onResult);
            return;
        }
        String name = offline.getName() == null ? "Tester" : offline.getName();
        sendUsageEmbed(plugin, name, offline.getUniqueId(), name,
                cc, totalUsed, playerUsed, onResult);
    }

    /**
     * Builds the value of the "Remaining uses" field by joining the two
     * translatable lines that apply. If neither limit is set (>0) the
     * field is hidden entirely (returns ""). Placeholders inside each
     * line are replaced before the lines are joined.
     */
    private static String buildRemainingText(LanguageManager lang,
                                             Map<String, String> playerPlaceholders,
                                             CustomCommand cc,
                                             int totalUsed,
                                             int playerUsed) {
        StringBuilder out = new StringBuilder();

        int totalLimit = cc.getUsageLimitTotal();
        if (totalLimit > 0) {
            int totalRemaining = Math.max(0, totalLimit - totalUsed);
            String line = lang.get("discord.embed.remaining-total");
            if (line != null && !line.isEmpty() && !line.equals("discord.embed.remaining-total")) {
                Map<String, String> ph = new HashMap<>(playerPlaceholders);
                ph.put("%limit%", String.valueOf(totalLimit));
                ph.put("%used%", String.valueOf(totalUsed));
                ph.put("%remaining%", String.valueOf(totalRemaining));
                out.append(applyPlaceholders(line, ph));
            }
        }

        int playerLimit = cc.getUsageLimitPerPlayer();
        if (playerLimit > 0) {
            int playerRemaining = Math.max(0, playerLimit - playerUsed);
            String line = lang.get("discord.embed.remaining-player");
            if (line != null && !line.isEmpty() && !line.equals("discord.embed.remaining-player")) {
                Map<String, String> ph = new HashMap<>(playerPlaceholders);
                ph.put("%limit%", String.valueOf(playerLimit));
                ph.put("%used%", String.valueOf(playerUsed));
                ph.put("%remaining%", String.valueOf(playerRemaining));
                if (out.length() > 0) out.append('\n');
                out.append(applyPlaceholders(line, ph));
            }
        }

        return out.toString();
    }

    /**
     * Joins every broadcast line, applies placeholders and removes all
     * gradient / hex / legacy color markers so the result is a plain
     * text suitable for a Discord embed. Empty / blank lines are kept
     * to preserve the visual rhythm of the original broadcast.
     */
    private static String stripBroadcastLines(List<String> lines, Map<String, String> placeholders) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String raw : lines) {
            if (raw == null) continue;
            String stripped = MessageUtil.stripColors(applyPlaceholders(raw, placeholders)).trim();
            if (sb.length() > 0) sb.append('\n');
            sb.append(stripped);
        }
        // Discord caps embed field values at 1024 chars.
        return truncate(sb.toString(), 1000);
    }

    private static String formatConsoleCommands(List<String> cmds, Map<String, String> placeholders) {
        if (cmds == null || cmds.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("```").append('\n');
        for (String raw : cmds) {
            if (raw == null || raw.isEmpty()) continue;
            sb.append("/").append(applyPlaceholders(raw, placeholders)).append('\n');
        }
        sb.append("```");
        return truncate(sb.toString(), 1000);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    // ------------------- HTTP -------------------

    private static String post(String urlString, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setDoOutput(true);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return null;
            }
            return "HTTP " + code;
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ------------------- JSON helpers -------------------

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"")
                .append(escape(value == null ? "" : value)).append('"');
    }

    private static boolean appendField(StringBuilder sb, String name, String value,
                                       boolean inline, boolean alreadyHasFields) {
        if (name == null || name.isEmpty()) return false;
        if (value == null || value.isEmpty()) return false;
        if (alreadyHasFields) sb.append(',');
        sb.append('{');
        appendString(sb, "name", name);
        sb.append(',');
        appendString(sb, "value", value);
        sb.append(",\"inline\":").append(inline);
        sb.append('}');
        return true;
    }

    private static String escape(String input) {
        if (input == null) return "";
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static String applyPlaceholders(String input, Map<String, String> map) {
        if (input == null) return "";
        String result = input;
        for (Map.Entry<String, String> e : map.entrySet()) {
            result = result.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    private static int parseHexColor(String hex) {
        if (hex == null) return 0x3FFF5C;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.startsWith("&#")) s = s.substring(2);
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            return 0x3FFF5C;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
