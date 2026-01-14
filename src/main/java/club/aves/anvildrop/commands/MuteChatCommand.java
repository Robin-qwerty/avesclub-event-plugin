package club.aves.anvildrop.commands;

import club.aves.anvildrop.chat.ChatMuteManager;
import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public final class MuteChatCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ChatMuteManager mute;

    public MuteChatCommand(Plugin plugin, ChatMuteManager mute) {
        this.plugin = plugin;
        this.mute = mute;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!sender.hasPermission("event.admin")) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNoPerm));
            return true;
        }

        boolean nowMuted = mute.toggle();
        if (nowMuted) {
            mute.broadcast(Text.color(cfg.msgPrefix + cfg.msgChatMutedOn));
        } else {
            mute.broadcast(Text.color(cfg.msgPrefix + cfg.msgChatMutedOff));
        }
        return true;
    }
}


