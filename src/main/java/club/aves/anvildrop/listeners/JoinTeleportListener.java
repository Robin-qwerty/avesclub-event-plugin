package club.aves.anvildrop.listeners;

import club.aves.anvildrop.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class JoinTeleportListener implements Listener {

    private final Plugin plugin;

    public JoinTeleportListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!cfg.joinTeleportEnabled) return;

        Player p = e.getPlayer();

        // Teleport 1 tick later to avoid conflicts with other join handlers/spawn logic.
        Bukkit.getScheduler().runTask(plugin, () -> {
            PluginConfig c = PluginConfig.load(plugin.getConfig());
            World lobby = Bukkit.getWorld(c.lobbyWorld);
            if (lobby == null) return;

            Location target = c.lobbySpawn != null ? c.lobbySpawn : lobby.getSpawnLocation();
            if (target != null) {
                p.teleport(target);
            }
        });
    }
}


