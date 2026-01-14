package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public final class VoiceMuteCommand implements CommandExecutor {

    private final Plugin plugin;

    public VoiceMuteCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!sender.hasPermission("event.admin")) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNoPerm));
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            sender.sendMessage(Text.color(cfg.msgPrefix + "&cLuckPerms is not installed."));
            return true;
        }

        String group = plugin.getConfig().getString("voiceMute.group", "default");
        String perm = plugin.getConfig().getString("voiceMute.permission", "voicechat.speak");
        boolean enabled = plugin.getConfig().getBoolean("voiceMute.enabled", false);

        boolean nowEnabled = !enabled;
        plugin.getConfig().set("voiceMute.enabled", nowEnabled);
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin jp) jp.saveConfig();

        // Requirement: toggle voicechat.speak for group default (or configured group).
        if (nowEnabled) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp group " + group + " permission set " + perm + " false");
            Bukkit.broadcastMessage(Text.color(cfg.msgPrefix + cfg.msgVoiceMutedOn));
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp group " + group + " permission set " + perm + " true");
            Bukkit.broadcastMessage(Text.color(cfg.msgPrefix + cfg.msgVoiceMutedOff));
        }

        return true;
    }
}


