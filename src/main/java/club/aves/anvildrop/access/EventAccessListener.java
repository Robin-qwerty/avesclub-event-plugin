package club.aves.anvildrop.access;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.event.AnvilDropEventManager;
import club.aves.anvildrop.ffa.FFAEventManager;
import club.aves.anvildrop.parkour.ParkourEventManager;
import club.aves.anvildrop.reconnect.ReconnectManager;
import club.aves.anvildrop.spleef.SpleefEventManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;

public final class EventAccessListener implements Listener {

    private final Plugin plugin;
    private final AnvilDropEventManager anvil;
    private final ParkourEventManager parkour;
    private final FFAEventManager ffa;
    private final SpleefEventManager spleef;
    private final ReconnectManager reconnect;

    public EventAccessListener(Plugin plugin,
                              AnvilDropEventManager anvil,
                              ParkourEventManager parkour,
                              FFAEventManager ffa,
                              SpleefEventManager spleef,
                              ReconnectManager reconnect) {
        this.plugin = plugin;
        this.anvil = anvil;
        this.parkour = parkour;
        this.ffa = ffa;
        this.spleef = spleef;
        this.reconnect = reconnect;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // let reconnect manager handle restores
        if (reconnect != null && reconnect.shouldSkipLobbyTeleport(p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            enforceNoPlayersInInactiveEventWorld(p);
            autoJoinIfLobbyAndEventOpen(p);
            // If no event is active and they're in the lobby, clear any leftover event items (kits, etc.)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p == null || !p.isOnline() || p.getWorld() == null) return;
                if (p.hasPermission("event.admin")) return;
                PluginConfig cfg = PluginConfig.load(plugin.getConfig());
                boolean anyActive = (anvil != null && anvil.isActive())
                        || (parkour != null && parkour.isActive())
                        || (ffa != null && ffa.isActive())
                        || (spleef != null && spleef.isActive());
                if (anyActive) return;
                if (!p.getWorld().getName().equalsIgnoreCase(cfg.lobbyWorld)) return;
                clearAllItems(p);
            }, 2L);
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            enforceNoPlayersInInactiveEventWorld(p);
            autoJoinIfLobbyAndEventOpen(p);
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        // After respawn, make sure they're not stuck in an inactive event world.
        Bukkit.getScheduler().runTask(plugin, () -> enforceNoPlayersInInactiveEventWorld(p));
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (e.getPlayer().hasPermission("event.admin")) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        String toWorld = e.getTo().getWorld().getName();

        if (toWorld.equalsIgnoreCase(cfg.eventWorld) && (anvil == null || !anvil.isActive())) {
            e.setTo(lobbySpawn(cfg));
            return;
        }
        if (toWorld.equalsIgnoreCase(cfg.parkourWorld) && (parkour == null || !parkour.isActive())) {
            e.setTo(lobbySpawn(cfg));
            return;
        }
        if (toWorld.equalsIgnoreCase(cfg.ffaWorld) && (ffa == null || !ffa.isActive())) {
            e.setTo(lobbySpawn(cfg));
            return;
        }
        if (toWorld.equalsIgnoreCase(cfg.spleefWorld) && (spleef == null || !spleef.isActive())) {
            e.setTo(lobbySpawn(cfg));
        }
    }

    private void enforceNoPlayersInInactiveEventWorld(Player p) {
        if (p == null || !p.isOnline() || p.getWorld() == null) return;
        if (p.hasPermission("event.admin")) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        String w = p.getWorld().getName();

        if (w.equalsIgnoreCase(cfg.eventWorld) && (anvil == null || !anvil.isActive())) {
            p.teleport(lobbySpawn(cfg));
            return;
        }
        if (w.equalsIgnoreCase(cfg.parkourWorld) && (parkour == null || !parkour.isActive())) {
            p.teleport(lobbySpawn(cfg));
            return;
        }
        if (w.equalsIgnoreCase(cfg.ffaWorld) && (ffa == null || !ffa.isActive())) {
            p.teleport(lobbySpawn(cfg));
            return;
        }
        if (w.equalsIgnoreCase(cfg.spleefWorld) && (spleef == null || !spleef.isActive())) {
            p.teleport(lobbySpawn(cfg));
        }
    }

    private void autoJoinIfLobbyAndEventOpen(Player p) {
        if (p == null || !p.isOnline() || p.getWorld() == null) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());

        if (!p.getWorld().getName().equalsIgnoreCase(cfg.lobbyWorld)) return;

        // If an event is open and NOT started yet, teleport lobby players into it.
        // If the event has started (countdown started / running), players stay in lobby.
        if (anvil != null && anvil.isAcceptingJoins()) {
            if (cfg.eventSpawn != null) p.teleport(cfg.eventSpawn);
            return;
        }
        if (parkour != null && parkour.isAcceptingJoins()) {
            if (cfg.parkourSpawn != null) p.teleport(cfg.parkourSpawn);
            return;
        }
        if (ffa != null && ffa.isAcceptingJoins()) {
            if (cfg.ffaOpenSpawn != null) p.teleport(cfg.ffaOpenSpawn);
            return;
        }
        if (spleef != null && spleef.isAcceptingJoins()) {
            if (cfg.spleefWaitingSpawn != null) p.teleport(cfg.spleefWaitingSpawn);
        }
    }

    private Location lobbySpawn(PluginConfig cfg) {
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        if (cfg.lobbySpawn != null) return cfg.lobbySpawn;
        if (lobby != null) return lobby.getSpawnLocation();
        // absolute fallback
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }

    private static void clearAllItems(Player p) {
        if (p == null) return;
        var inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[0]);
        inv.setItemInOffHand(null);
        p.updateInventory();
    }
}


