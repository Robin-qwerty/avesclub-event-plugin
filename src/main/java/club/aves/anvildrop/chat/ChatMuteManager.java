package club.aves.anvildrop.chat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ChatMuteManager {

    private final Plugin plugin;
    private volatile boolean muted = false;

    public ChatMuteManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean toggle() {
        muted = !muted;
        return muted;
    }

    public void broadcast(String message) {
        if (message == null || message.isBlank()) return;
        Bukkit.broadcastMessage(message);
    }
}


