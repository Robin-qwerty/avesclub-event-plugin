package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.mods.ModRegistry;
import club.aves.anvildrop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public final class DeadCommand implements CommandExecutor {

    private final Plugin plugin;
    private final DeadPermissionService deadPerms;
    private final ModRegistry mods;

    public DeadCommand(Plugin plugin, DeadPermissionService deadPerms, ModRegistry mods) {
        this.plugin = plugin;
        this.deadPerms = deadPerms;
        this.mods = mods;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());

        if (!sender.hasPermission("event.dead")) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNoPerm));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgDeadUsage));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveNotOnline));
            return true;
        }

        if (!mods.isMod(target.getUniqueId())) {
            deadPerms.markDead(target);
        }

        // Kill them (this will also trigger death handlers; marking dead twice is harmless)
        try {
            target.setHealth(0.0);
        } catch (Throwable t) {
            target.damage(10000.0);
        }

        sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgDeadDone, Map.of("player", target.getName()))));
        return true;
    }
}


