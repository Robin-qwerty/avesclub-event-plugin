package club.aves.anvildrop.chat;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.util.Text;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public final class ChatMuteListener implements Listener {

    private final Plugin plugin;
    private final ChatMuteManager mute;

    public ChatMuteListener(Plugin plugin, ChatMuteManager mute) {
        this.plugin = plugin;
        this.mute = mute;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!mute.isMuted()) return;
        if (e.getPlayer().hasPermission("event.admin")) return;

        e.setCancelled(true);
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        e.getPlayer().sendMessage(Text.color(cfg.msgPrefix + cfg.msgChatMutedBlocked));
    }
}


