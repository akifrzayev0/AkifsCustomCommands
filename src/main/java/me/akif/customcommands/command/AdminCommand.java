package me.akif.customcommands.command;

import me.akif.customcommands.AkifsCustomCommands;
import me.akif.customcommands.manager.ConfigManager;
import me.akif.customcommands.manager.DataManager;
import me.akif.customcommands.manager.LanguageManager;
import me.akif.customcommands.model.CustomCommand;
import me.akif.customcommands.util.DiscordWebhook;
import me.akif.customcommands.util.MessageUtil;
import me.akif.customcommands.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the /cac admin command and its tab completion.
 * Every user-facing message is loaded from the active language file
 * via {@link LanguageManager}.
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    public static final String ADMIN_PERMISSION = "akifscustomcommands.admin";

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "help", "reload", "list", "info", "create", "delete",
            "enable", "disable", "reset", "resetplayer",
            "addcmd", "removecmd",
            "addbcast", "removebcast", "clearbcast",
            "setlimit", "setplayerlimit", "setcooldown", "setduration", "setperm",
            "testwebhook"
    );

    private final AkifsCustomCommands plugin;

    public AdminCommand(AkifsCustomCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION) && !sender.isOp()) {
            sendKey(sender, "admin.no-permission", null);
            return true;
        }

        if (args.length == 0) {
            sendKey(sender, "admin.help-hint", null);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
            case "?":
                MessageUtil.sendRaw(sender, plugin.getLanguageManager().getList("help"));
                break;
            case "reload":         handleReload(sender); break;
            case "list":           handleList(sender); break;
            case "info":           handleInfo(sender, args); break;
            case "create":         handleCreate(sender, args); break;
            case "delete":         handleDelete(sender, args); break;
            case "enable":         handleToggle(sender, args, true); break;
            case "disable":        handleToggle(sender, args, false); break;
            case "reset":          handleReset(sender, args); break;
            case "resetplayer":    handleResetPlayer(sender, args); break;
            case "addcmd":         handleAddCmd(sender, args); break;
            case "removecmd":      handleRemoveCmd(sender, args); break;
            case "addbcast":       handleAddBcast(sender, args); break;
            case "removebcast":    handleRemoveBcast(sender, args); break;
            case "clearbcast":     handleClearBcast(sender, args); break;
            case "setlimit":       handleSetLong(sender, args, "limit"); break;
            case "setplayerlimit": handleSetLong(sender, args, "playerlimit"); break;
            case "setcooldown":    handleSetLong(sender, args, "cooldown"); break;
            case "setduration":    handleSetLong(sender, args, "duration"); break;
            case "setperm":        handleSetPerm(sender, args); break;
            case "testwebhook":    handleTestWebhook(sender, args); break;
            default:               sendKey(sender, "admin.unknown-subcommand", null); break;
        }
        return true;
    }

    // -------------------- handlers --------------------

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().load();
        plugin.getLanguageManager().load(plugin.getConfigManager().getLanguage());
        plugin.getDataManager().load();
        plugin.getCommandManager().registerAll();
        sendKey(sender, "admin.reload-success", null);
        Map<String, String> ph = new HashMap<>();
        ph.put("%language%", plugin.getLanguageManager().getActiveLanguage());
        sendKey(sender, "admin.language-loaded", ph);
    }

    private void handleList(CommandSender sender) {
        Map<String, CustomCommand> commands = plugin.getConfigManager().getCommands();
        LanguageManager lang = plugin.getLanguageManager();
        if (commands.isEmpty()) {
            sendKey(sender, "list.empty", null);
            return;
        }
        Map<String, String> headerPh = new HashMap<>();
        headerPh.put("%count%", String.valueOf(commands.size()));
        sendKey(sender, "list.header", headerPh);
        for (CustomCommand cc : commands.values()) {
            String status = cc.isEnabled()
                    ? lang.get("info.status-enabled")
                    : lang.get("info.status-disabled");
            if (cc.isExpired()) status = lang.get("info.status-expired");
            Map<String, String> ph = new HashMap<>();
            ph.put("%command%", cc.getName());
            ph.put("%status%", status);
            sendKey(sender, "list.entry", ph);
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac info <command>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        DataManager data = plugin.getDataManager();
        LanguageManager lang = plugin.getLanguageManager();

        long createdAt = cc.getCreatedAt();
        long expiresAt = cc.getActiveTimeSeconds() > 0 ? createdAt + cc.getActiveTimeSeconds() : 0L;
        long now = TimeUtil.nowSeconds();

        String durationText;
        if (cc.getActiveTimeSeconds() <= 0) {
            durationText = lang.get("info.duration-forever");
        } else if (now > expiresAt) {
            durationText = lang.get("info.duration-expired");
        } else {
            durationText = lang.get("info.duration-remaining")
                    .replace("%time%", TimeUtil.formatSeconds(expiresAt - now));
        }

        String unlimited = lang.get("info.unlimited");
        String none = lang.get("info.cooldown-none");

        MessageUtil.sendRaw(sender, Collections.singletonList(lang.get("info.header")));
        sendKey(sender, "info.command-line", placeholders("%command%", cc.getName()));
        Map<String, String> statusPh = new HashMap<>();
        statusPh.put("%status%", cc.isEnabled() ? lang.get("info.status-enabled") : lang.get("info.status-disabled"));
        sendKey(sender, "info.status-line", statusPh);

        Map<String, String> permPh = new HashMap<>();
        permPh.put("%permission%", cc.getPermission().isEmpty()
                ? lang.get("info.permission-none")
                : cc.getPermission());
        sendKey(sender, "info.permission-line", permPh);

        Map<String, String> totalPh = new HashMap<>();
        totalPh.put("%used%", String.valueOf(data.getTotalUsage(cc.getName())));
        totalPh.put("%limit%", cc.getUsageLimitTotal() <= 0 ? unlimited : String.valueOf(cc.getUsageLimitTotal()));
        sendKey(sender, "info.total-usage-line", totalPh);

        Map<String, String> playerPh = new HashMap<>();
        playerPh.put("%limit%", cc.getUsageLimitPerPlayer() <= 0 ? unlimited : String.valueOf(cc.getUsageLimitPerPlayer()));
        sendKey(sender, "info.player-limit-line", playerPh);

        Map<String, String> cdPh = new HashMap<>();
        cdPh.put("%cooldown%", cc.getCooldownSeconds() <= 0 ? none : TimeUtil.formatSeconds(cc.getCooldownSeconds()));
        sendKey(sender, "info.cooldown-line", cdPh);

        Map<String, String> durPh = new HashMap<>();
        durPh.put("%duration%", durationText);
        sendKey(sender, "info.duration-line", durPh);

        MessageUtil.sendRaw(sender, Collections.singletonList(lang.get("info.console-commands-header")));
        if (cc.getConsoleCommands().isEmpty()) {
            MessageUtil.sendRaw(sender, Collections.singletonList(lang.get("info.console-commands-empty")));
        } else {
            int i = 0;
            String template = lang.get("info.console-commands-entry");
            for (String line : cc.getConsoleCommands()) {
                String rendered = template
                        .replace("%index%", String.valueOf(i))
                        .replace("%command%", line);
                MessageUtil.sendRaw(sender, Collections.singletonList(rendered));
                i++;
            }
        }

        MessageUtil.sendRaw(sender, Collections.singletonList(lang.get("info.broadcast-header")));
        if (cc.getBroadcastMessages().isEmpty()) {
            MessageUtil.sendRaw(sender, Collections.singletonList(lang.get("info.broadcast-empty")));
        } else {
            int i = 0;
            String template = lang.get("info.broadcast-entry");
            for (String line : cc.getBroadcastMessages()) {
                String rendered = template
                        .replace("%index%", String.valueOf(i))
                        .replace("%message%", line);
                MessageUtil.sendRaw(sender, Collections.singletonList(rendered));
                i++;
            }
        }

        MessageUtil.sendRaw(sender, Collections.singletonList(lang.get("info.footer")));
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac create <command>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!isValidCommandName(name)) {
            sendKey(sender, "admin.invalid-name", null);
            return;
        }
        ConfigManager cfg = plugin.getConfigManager();
        if (cfg.hasCommand(name)) {
            sendKey(sender, "admin.command-already-exists", placeholders("%command%", name));
            return;
        }
        CustomCommand cc = cfg.registerNewCommand(name);
        plugin.getCommandManager().register(cc);
        sendKey(sender, "admin.command-created", placeholders("%command%", name));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac delete <command>");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.hasCommand(name)) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", name));
            return;
        }
        cfg.deleteCommand(name);
        plugin.getDataManager().deleteCommand(name);
        plugin.getCommandManager().unregister(name);
        sendKey(sender, "admin.command-deleted", placeholders("%command%", name));
    }

    private void handleToggle(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac " + (enable ? "enable" : "disable") + " <command>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        cc.setEnabled(enable);
        plugin.getConfigManager().writeCommand(cc);
        sendKey(sender, enable ? "admin.command-enabled" : "admin.command-disabled",
                placeholders("%command%", cc.getName()));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac reset <command>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        plugin.getDataManager().resetCommand(cc.getName());
        sendKey(sender, "admin.command-reset", placeholders("%command%", cc.getName()));
    }

    private void handleResetPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac resetplayer <player> <command>");
            return;
        }
        String playerName = args[1];
        CustomCommand cc = plugin.getConfigManager().getCommand(args[2]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[2]));
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offline.getUniqueId();
        plugin.getDataManager().resetPlayer(uuid, cc.getName());
        Map<String, String> ph = new HashMap<>();
        ph.put("%player%", playerName);
        ph.put("%command%", cc.getName());
        sendKey(sender, "admin.player-reset", ph);
    }

    private void handleAddCmd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac addcmd <command> <console command>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        cc.addConsoleCommand(sb.toString());
        plugin.getConfigManager().writeCommand(cc);
        sendKey(sender, "admin.console-cmd-added", placeholders("%command%", cc.getName()));
    }

    private void handleRemoveCmd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac removecmd <command> <index>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        int idx;
        try {
            idx = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sendKey(sender, "admin.invalid-number", null);
            return;
        }
        boolean ok = cc.removeConsoleCommand(idx);
        if (!ok) {
            sendKey(sender, "admin.invalid-index", null);
            return;
        }
        plugin.getConfigManager().writeCommand(cc);
        sendKey(sender, "admin.console-cmd-removed", null);
    }

    private void handleAddBcast(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac addbcast <command> <message>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        cc.addBroadcastMessage(sb.toString());
        plugin.getConfigManager().writeCommand(cc);
        sendKey(sender, "admin.broadcast-added", placeholders("%command%", cc.getName()));
    }

    private void handleRemoveBcast(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac removebcast <command> <index>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        int idx;
        try {
            idx = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sendKey(sender, "admin.invalid-number", null);
            return;
        }
        if (!cc.removeBroadcastMessage(idx)) {
            sendKey(sender, "admin.invalid-index", null);
            return;
        }
        plugin.getConfigManager().writeCommand(cc);
        sendKey(sender, "admin.broadcast-removed", null);
    }

    private void handleClearBcast(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac clearbcast <command>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        cc.clearBroadcastMessages();
        plugin.getConfigManager().writeCommand(cc);
        sendKey(sender, "admin.broadcast-cleared", placeholders("%command%", cc.getName()));
    }

    private void handleSetLong(CommandSender sender, String[] args, String type) {
        if (args.length < 3) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac set" + type + " <command> <value>");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }

        boolean durationLike = type.equals("cooldown") || type.equals("duration");
        long value;
        try {
            value = durationLike
                    ? TimeUtil.parseDurationSeconds(args[2])
                    : Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sendKey(sender, "admin.invalid-number", null);
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("%command%", cc.getName());
        ph.put("%amount%", String.valueOf(value));
        ph.put("%seconds%", String.valueOf(value));
        ph.put("%duration%", durationLike ? TimeUtil.formatSeconds(value) : String.valueOf(value));

        switch (type) {
            case "limit":
                cc.setUsageLimitTotal((int) value);
                sendKey(sender, "admin.limit-set", ph);
                break;
            case "playerlimit":
                cc.setUsageLimitPerPlayer((int) value);
                sendKey(sender, "admin.player-limit-set", ph);
                break;
            case "cooldown":
                cc.setCooldownSeconds(value);
                sendKey(sender, "admin.cooldown-set", ph);
                break;
            case "duration":
                cc.setActiveTimeSeconds(value);
                sendKey(sender, "admin.duration-set", ph);
                break;
            default:
                return;
        }
        plugin.getConfigManager().writeCommand(cc);
    }

    private void handleTestWebhook(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac testwebhook <command> [player]");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isDiscordEnabled()) {
            sendKey(sender, "admin.webhook-disabled", null);
            return;
        }
        if (cfg.getDiscordWebhookUrl().isEmpty()) {
            sendKey(sender, "admin.webhook-not-configured", null);
            return;
        }

        OfflinePlayer target;
        if (args.length >= 3) {
            target = Bukkit.getOfflinePlayer(args[2]);
        } else if (sender instanceof Player) {
            target = (OfflinePlayer) sender;
        } else {
            target = null;
        }

        DataManager data = plugin.getDataManager();
        int totalUsed = data.getTotalUsage(cc.getName());
        int playerUsed = target == null ? 0 : data.getPlayerUsage(target.getUniqueId(), cc.getName());

        DiscordWebhook.sendUsageEmbed(plugin, target, cc, totalUsed, playerUsed, error -> {
            if (error == null) {
                sendKey(sender, "admin.webhook-test-sent", placeholders("%command%", cc.getName()));
            } else {
                Map<String, String> ph = new HashMap<>();
                ph.put("%error%", error);
                sendKey(sender, "admin.webhook-failed", ph);
            }
        });
    }

    private void handleSetPerm(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(plugin, sender, "&cUsage: &e/cac setperm <command> [permission]");
            return;
        }
        CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
        if (cc == null) {
            sendKey(sender, "admin.command-not-found", placeholders("%command%", args[1]));
            return;
        }
        Map<String, String> ph = new HashMap<>();
        ph.put("%command%", cc.getName());
        if (args.length < 3) {
            cc.setPermission("");
            plugin.getConfigManager().writeCommand(cc);
            sendKey(sender, "admin.permission-cleared", ph);
            return;
        }
        cc.setPermission(args[2]);
        plugin.getConfigManager().writeCommand(cc);
        ph.put("%permission%", args[2]);
        sendKey(sender, "admin.permission-set", ph);
    }

    // -------------------- helpers --------------------

    private boolean isValidCommandName(String name) {
        return name != null && name.matches("[a-z0-9_]+");
    }

    private Map<String, String> placeholders(String key, String value) {
        Map<String, String> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    private void sendKey(CommandSender sender, String key, Map<String, String> placeholders) {
        MessageUtil.sendKey(plugin, sender, key, placeholders);
    }

    // -------------------- tab completion --------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION) && !sender.isOp()) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filter(SUB_COMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            switch (sub) {
                case "info":
                case "delete":
                case "enable":
                case "disable":
                case "reset":
                case "addcmd":
                case "removecmd":
                case "addbcast":
                case "removebcast":
                case "clearbcast":
                case "setlimit":
                case "setplayerlimit":
                case "setcooldown":
                case "setduration":
                case "setperm":
                case "testwebhook":
                    return filter(new ArrayList<>(plugin.getConfigManager().getCommands().keySet()), args[1]);
                case "resetplayer":
                    return filter(onlinePlayerNames(), args[1]);
                default:
                    return Collections.emptyList();
            }
        }

        if (args.length == 3) {
            if (sub.equals("resetplayer")) {
                return filter(new ArrayList<>(plugin.getConfigManager().getCommands().keySet()), args[2]);
            }
            if (sub.equals("setlimit") || sub.equals("setplayerlimit")) {
                return filter(Arrays.asList("-1", "1", "5", "10", "50", "100"), args[2]);
            }
            if (sub.equals("setcooldown") || sub.equals("setduration")) {
                return filter(Arrays.asList(
                        "0", "30s", "1m", "5m", "30m", "1h", "12h", "1d", "7d", "30d"
                ), args[2]);
            }
            if (sub.equals("removecmd")) {
                CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
                if (cc != null) {
                    List<String> idx = new ArrayList<>();
                    for (int i = 0; i < cc.getConsoleCommands().size(); i++) idx.add(String.valueOf(i));
                    return filter(idx, args[2]);
                }
            }
            if (sub.equals("removebcast")) {
                CustomCommand cc = plugin.getConfigManager().getCommand(args[1]);
                if (cc != null) {
                    List<String> idx = new ArrayList<>();
                    for (int i = 0; i < cc.getBroadcastMessages().size(); i++) idx.add(String.valueOf(i));
                    return filter(idx, args[2]);
                }
            }
            if (sub.equals("testwebhook")) {
                return filter(onlinePlayerNames(), args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    private List<String> filter(List<String> source, String prefix) {
        if (source == null) return Collections.emptyList();
        if (prefix == null || prefix.isEmpty()) return new ArrayList<>(source);
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : source) {
            if (s != null && s.toLowerCase(Locale.ROOT).startsWith(low)) out.add(s);
        }
        Collections.sort(out);
        return out;
    }
}
