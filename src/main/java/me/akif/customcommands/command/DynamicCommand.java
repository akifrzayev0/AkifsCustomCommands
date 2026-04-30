package me.akif.customcommands.command;

import me.akif.customcommands.AkifsCustomCommands;
import me.akif.customcommands.manager.CommandManager;
import me.akif.customcommands.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;

/**
 * One instance of this class is registered into Bukkit's CommandMap
 * for every custom command defined in config.yml. When a player runs
 * the command we hand control over to {@link CommandManager}.
 */
public class DynamicCommand extends Command {

    private final AkifsCustomCommands plugin;

    public DynamicCommand(AkifsCustomCommands plugin, String name) {
        super(name);
        this.plugin = plugin;
        this.setDescription("Custom command managed by AkifsCustomCommands.");
        this.setUsage("/" + name);
        this.setAliases(Collections.emptyList());
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player)) {
            String msg = plugin.getLanguageManager().get("admin.player-only");
            sender.sendMessage(MessageUtil.formatWithPrefix(plugin, msg));
            return true;
        }
        CommandManager manager = plugin.getCommandManager();
        if (manager == null) return true;
        manager.handleCustomCommand((Player) sender, getName());
        return true;
    }
}
