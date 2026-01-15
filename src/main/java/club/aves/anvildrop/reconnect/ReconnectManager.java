package club.aves.anvildrop.reconnect;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.event.AnvilDropEventManager;
import club.aves.anvildrop.ffa.FFAEventManager;
import club.aves.anvildrop.parkour.ParkourEventManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ReconnectManager implements Listener {

    private enum EventKind { ANVIL, PARKOUR, FFA }

    private record Pending(EventKind kind, Location location, long expiresAtMillis, boolean countAsAlive, BukkitTask task) {}

    private final Plugin plugin;
    private final DeadPermissionService deadPerms;
    private final AnvilDropEventManager anvil;
    private final ParkourEventManager parkour;
    private final FFAEventManager ffa;

    private final Map<UUID, Pending> pending = new HashMap<>();

    public ReconnectManager(Plugin plugin,
                            DeadPermissionService deadPerms,
                            AnvilDropEventManager anvil,
                            ParkourEventManager parkour,
                            FFAEventManager ffa) {
        this.plugin = plugin;
        this.deadPerms = deadPerms;
        this.anvil = anvil;
        this.parkour = parkour;
        this.ffa = ffa;
    }

    public boolean shouldSkipLobbyTeleport(Player p) {
        if (p == null) return false;
        Pending pen = pending.get(p.getUniqueId());
        if (pen == null) return false;
        long now = System.currentTimeMillis();
        if (now > pen.expiresAtMillis) return false;
        return isEventStillActive(pen.kind);
    }

    public int getPendingAliveForWorld(String worldName) {
        if (worldName == null) return 0;
        EventKind kind = switch (worldName.toLowerCase()) {
            case "anvildrop" -> EventKind.ANVIL; // fallback; we also match exact below
            default -> null;
        };
        // Determine kind by matching config worlds
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (worldName.equalsIgnoreCase(cfg.eventWorld)) kind = EventKind.ANVIL;
        else if (worldName.equalsIgnoreCase(cfg.parkourWorld)) kind = EventKind.PARKOUR;
        else if (worldName.equalsIgnoreCase(cfg.ffaWorld)) kind = EventKind.FFA;
        if (kind == null) return 0;

        long now = System.currentTimeMillis();
        int count = 0;
        for (Pending p : pending.values()) {
            if (p.kind != kind) continue;
            if (!p.countAsAlive) continue;
            if (now > p.expiresAtMillis) continue;
            count++;
        }
        return count;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (p == null || p.getWorld() == null) return;

        EventKind kind = null;
        String w = p.getWorld().getName();
        if (w.equalsIgnoreCase(cfg.eventWorld) && anvil != null && anvil.isActive()) kind = EventKind.ANVIL;
        else if (w.equalsIgnoreCase(cfg.parkourWorld) && parkour != null && parkour.isActive()) kind = EventKind.PARKOUR;
        else if (w.equalsIgnoreCase(cfg.ffaWorld) && ffa != null && ffa.isActive()) kind = EventKind.FFA;

        if (kind == null) return;

        // Only treat them as "alive during grace" if they were alive when leaving.
        boolean countAsAlive = true;
        UUID id = p.getUniqueId();
        if (p.hasPermission("event.admin")) countAsAlive = false;
        if (deadPerms.isDead(p)) countAsAlive = false;
        if (kind == EventKind.ANVIL && anvil != null && anvil.isEliminated(id)) countAsAlive = false;
        if (kind == EventKind.PARKOUR && parkour != null && (parkour.isEliminated(id) || parkour.isFinished(id))) countAsAlive = false;
        if (kind == EventKind.FFA && ffa != null && ffa.isEliminated(id)) countAsAlive = false;

        int grace = Math.max(1, plugin.getConfig().getInt("reconnect.graceSeconds", 30));
        long expiresAt = System.currentTimeMillis() + (grace * 1000L);
        Location loc = p.getLocation().clone();

        // Cancel previous pending (if any)
        Pending prev = pending.remove(p.getUniqueId());
        if (prev != null && prev.task != null) prev.task.cancel();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending pen = pending.remove(p.getUniqueId());
            if (pen == null) return;
            // If they didn't rejoin in time -> dead (even if the event ended in between)
            deadPerms.markDeadUuid(p.getUniqueId());
            // Trigger alive refresh
            triggerAliveRefresh(pen.kind);
        }, grace * 20L);

        pending.put(p.getUniqueId(), new Pending(kind, loc, expiresAt, countAsAlive, task));
        triggerAliveRefresh(kind);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        Pending pen = pending.remove(p.getUniqueId());
        if (pen == null) return;
        if (pen.task != null) pen.task.cancel();

        long now = System.currentTimeMillis();
        boolean within = now <= pen.expiresAtMillis;

        if (!within) {
            deadPerms.markDead(p);
            triggerAliveRefresh(pen.kind);
            return;
        }

        // If the event ended while they were gone -> dead
        if (!isEventStillActive(pen.kind)) {
            deadPerms.markDead(p);
            triggerAliveRefresh(pen.kind);
            return;
        }

        // Restore location (1 tick later so join-spawn handlers don't override)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.teleport(pen.location);
            triggerAliveRefresh(pen.kind);
        });
    }

    private boolean isEventStillActive(EventKind kind) {
        return switch (kind) {
            case ANVIL -> anvil != null && anvil.isActive();
            case PARKOUR -> parkour != null && parkour.isActive();
            case FFA -> ffa != null && ffa.isActive();
        };
    }

    private void triggerAliveRefresh(EventKind kind) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (kind) {
                case ANVIL -> {
                    // Anvil already ticks every second, but refresh anyway
                    // (no public method needed)
                }
                case PARKOUR -> {
                    if (parkour != null) parkour.refreshAliveCount();
                }
                case FFA -> {
                    if (ffa != null) ffa.refreshAliveCount();
                }
            }
        });
    }
}


