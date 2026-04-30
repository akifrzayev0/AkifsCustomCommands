package me.akif.customcommands.util;

import me.akif.customcommands.AkifsCustomCommands;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central place for sending colored, prefixed messages.
 *
 * <p>Three color systems are supported and processed in order:
 * <ol>
 *   <li><b>Gradients</b> — {@code <gradient:#RRGGBB:#RRGGBB>text</gradient>}
 *       interpolates a color across each character. Format codes
 *       inside the block ({@code &l}, {@code &n}, {@code &o}, {@code &m},
 *       {@code &k}) are preserved per character.</li>
 *   <li><b>Hex codes</b> — {@code &#RRGGBB} sets a single color (1.16+).</li>
 *   <li><b>Legacy codes</b> — {@code &a}, {@code &c}, {@code &l}, ...</li>
 * </ol>
 */
public final class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile(
            "<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>",
            Pattern.DOTALL);

    private MessageUtil() {
    }

    public static String color(String input) {
        if (input == null) return "";
        String afterGradient = applyGradients(input);
        return applyHexAndLegacy(afterGradient);
    }

    private static String applyHexAndLegacy(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder buffer = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            buffer.append(input, last, matcher.start());
            String hex = matcher.group(1);
            try {
                buffer.append(net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
            } catch (Throwable t) {
                buffer.append(matcher.group());
            }
            last = matcher.end();
        }
        buffer.append(input, last, input.length());
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Replaces every {@code <gradient:#A:#B>text</gradient>} block with
     * a sequence of per-character {@code &#RRGGBB} codes. The hex codes
     * are then translated by {@link #applyHexAndLegacy(String)}.
     */
    private static String applyGradients(String input) {
        if (input.indexOf("<gradient:") < 0) return input;
        Matcher matcher = GRADIENT_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(input, last, matcher.start());
            int[] start = parseRgb(matcher.group(1));
            int[] end = parseRgb(matcher.group(2));
            out.append(buildGradient(start, end, matcher.group(3)));
            last = matcher.end();
        }
        out.append(input, last, input.length());
        return out.toString();
    }

    private static int[] parseRgb(String hex) {
        return new int[] {
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private static String buildGradient(int[] start, int[] end, String text) {
        if (text.isEmpty()) return "";

        // First pass: count "meaningful" characters so the interpolation
        // does not skip ahead because of format codes like &l.
        int meaningful = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isAmpFormat(text, i)) {
                i++;
            } else {
                meaningful++;
            }
        }

        StringBuilder out = new StringBuilder();
        StringBuilder pendingFormats = new StringBuilder();
        int idx = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isAmpFormat(text, i)) {
                pendingFormats.append('&').append(text.charAt(i + 1));
                i++;
                continue;
            }
            char ch = text.charAt(i);
            float t = meaningful <= 1 ? 0f : (float) idx / (float) (meaningful - 1);
            int r = lerp(start[0], end[0], t);
            int g = lerp(start[1], end[1], t);
            int b = lerp(start[2], end[2], t);
            out.append(String.format("&#%02X%02X%02X", r, g, b));
            if (pendingFormats.length() > 0) out.append(pendingFormats);
            out.append(ch);
            idx++;
        }
        return out.toString();
    }

    private static boolean isAmpFormat(String s, int i) {
        if (i + 1 >= s.length()) return false;
        if (s.charAt(i) != '&') return false;
        char c = Character.toLowerCase(s.charAt(i + 1));
        return c == 'l' || c == 'n' || c == 'o' || c == 'm' || c == 'k' || c == 'r';
    }

    private static int lerp(int a, int b, float t) {
        int v = Math.round(a + (b - a) * t);
        return Math.max(0, Math.min(255, v));
    }

    public static String formatWithPrefix(AkifsCustomCommands plugin, String input) {
        String prefix = plugin.getConfigManager().getPrefix();
        return color(prefix + (input == null ? "" : input));
    }

    public static void send(AkifsCustomCommands plugin, CommandSender target, String message) {
        if (message == null || message.isEmpty()) return;
        target.sendMessage(formatWithPrefix(plugin, message));
    }

    public static void sendList(AkifsCustomCommands plugin, CommandSender target, List<String> messages) {
        if (messages == null || messages.isEmpty()) return;
        for (String msg : messages) {
            target.sendMessage(formatWithPrefix(plugin, msg));
        }
    }

    /**
     * Sends a list of messages without the prefix - useful for help
     * or info screens that already have their own header.
     */
    public static void sendRaw(CommandSender target, List<String> messages) {
        if (messages == null) return;
        for (String msg : messages) {
            target.sendMessage(color(msg));
        }
    }

    public static void sendKey(AkifsCustomCommands plugin, CommandSender target,
                               String key, Map<String, String> placeholders) {
        String raw = plugin.getLanguageManager().get(key);
        if (raw == null || raw.isEmpty()) return;
        String replaced = applyPlaceholders(raw, placeholders);
        send(plugin, target, replaced);
    }

    public static void sendKeyList(AkifsCustomCommands plugin, CommandSender target,
                                   String key, Map<String, String> placeholders) {
        List<String> raw = plugin.getLanguageManager().getList(key);
        if (raw == null || raw.isEmpty()) return;
        for (String line : raw) {
            send(plugin, target, applyPlaceholders(line, placeholders));
        }
    }

    /**
     * Removes every supported color marker (gradient blocks, hex codes
     * and legacy &amp;-codes) from the input string. Useful for sending
     * plain-text copies of in-game messages to Discord, log files, etc.
     */
    public static String stripColors(String input) {
        if (input == null) return "";
        return input
                .replaceAll("</?gradient(:#[A-Fa-f0-9]{6})*>", "")
                .replaceAll("&#[A-Fa-f0-9]{6}", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    public static String applyPlaceholders(String message, Map<String, String> placeholders) {
        if (message == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return message;
        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
