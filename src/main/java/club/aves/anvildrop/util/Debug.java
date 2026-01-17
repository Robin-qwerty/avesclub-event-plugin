package club.aves.anvildrop.util;

import org.bukkit.plugin.Plugin;

public final class Debug {
    private Debug() {}

    public static void log(Plugin plugin, String message) {
        if (plugin == null || message == null) return;
        if (!plugin.getConfig().getBoolean("debug.enabled", false)) return;
        plugin.getLogger().info(message);
    }
}


