package club.aves.anvildrop.parkour;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.model.ArenaCuboid;
import club.aves.anvildrop.ui.AnvilDropScoreboard;
import club.aves.anvildrop.ui.EventSettingsUI;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ParkourEventManager implements Listener {

    private final Plugin plugin;
    private final AnvilDropScoreboard scoreboard;
    private final EventSettingsUI settingsUI;

    private volatile ParkourEventState state = ParkourEventState.IDLE;
    private final Set<UUID> eliminated = new HashSet<>();
    private BukkitTask timerTask;
    private long startMillis = 0L;

    public ParkourEventManager(Plugin plugin, AnvilDropScoreboard scoreboard, EventSettingsUI settingsUI) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
        this.settingsUI = settingsUI;
    }

    public ParkourEventState getState() {
        return state;
    }

    public boolean isActive() {
        return state != ParkourEventState.IDLE;
    }

    public boolean open() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World parkour = Bukkit.getWorld(cfg.parkourWorld);
        if (parkour == null) return false;
        if (cfg.parkourSpawn == null) return false;

        eliminated.clear();
        state = ParkourEventState.OPEN;
        startMillis = 0L;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        // Place wall on open
        placeWall(true);

        // Requirement: teleport everybody
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.teleport(cfg.parkourSpawn);
            p.setGameMode(GameMode.SURVIVAL);
            Bukkit.getScheduler().runTask(plugin, () -> settingsUI.ensureCompass(p));
        }

        scoreboard.setAliveCountForWorld(cfg.parkourWorld, getAliveCount());
        scoreboard.setTimeSecondsForWorld(cfg.parkourWorld, 0);
        return true;
    }

    public boolean start() {
        if (state != ParkourEventState.OPEN) return false;
        state = ParkourEventState.RUNNING;
        placeWall(false);
        startMillis = System.currentTimeMillis();
        if (timerTask != null) timerTask.cancel();
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            PluginConfig c = PluginConfig.load(plugin.getConfig());
            if (state != ParkourEventState.RUNNING) return;
            int secs = (int) ((System.currentTimeMillis() - startMillis) / 1000L);
            scoreboard.setTimeSecondsForWorld(c.parkourWorld, secs);
        }, 0L, 20L);
        updateAlive();
        return true;
    }

    public void stop() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World parkour = Bukkit.getWorld(cfg.parkourWorld);
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        Location lobbySpawn = cfg.lobbySpawn != null ? cfg.lobbySpawn : (lobby != null ? lobby.getSpawnLocation() : null);

        // Place wall back
        placeWall(true);

        if (parkour != null && lobbySpawn != null) {
            for (Player p : parkour.getPlayers()) {
                p.teleport(lobbySpawn);
                p.setGameMode(GameMode.SURVIVAL);
                settingsUI.removeCompass(p);
            }
        }

        eliminated.clear();
        state = ParkourEventState.IDLE;
        startMillis = 0L;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        scoreboard.setTimeSecondsForWorld(cfg.parkourWorld, 0);
        updateAlive();
    }

    private void updateAlive() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        scoreboard.setAliveCountForWorld(cfg.parkourWorld, getAliveCount());
    }

    public int getAliveCount() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World parkour = Bukkit.getWorld(cfg.parkourWorld);
        if (parkour == null) return 0;

        int alive = parkour.getPlayers().size();
        for (Player p : parkour.getPlayers()) {
            if (p.hasPermission("event.admin")) {
                alive--;
                continue;
            }
            if (eliminated.contains(p.getUniqueId())) alive--;
        }
        return Math.max(0, alive);
    }

    public boolean isEliminated(Player p) {
        return p != null && eliminated.contains(p.getUniqueId());
    }

    public void reviveToStart(Player p) {
        if (p == null) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        eliminated.remove(p.getUniqueId());
        if (cfg.parkourSpawn != null) {
            p.teleport(cfg.parkourSpawn);
        }
        p.setGameMode(GameMode.SURVIVAL);
        Bukkit.getScheduler().runTask(plugin, () -> settingsUI.ensureCompass(p));
        updateAlive();
    }

    private void placeWall(boolean place) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World w = Bukkit.getWorld(cfg.parkourWorld);
        if (w == null) return;
        ArenaCuboid wall = cfg.parkourWall;
        if (wall == null || !wall.isInWorld(w)) return;

        Material mat = place ? Material.BARRIER : Material.AIR;
        for (int x = wall.minX(); x <= wall.maxX(); x++) {
            for (int y = wall.minY(); y <= wall.maxY(); y++) {
                for (int z = wall.minZ(); z <= wall.maxZ(); z++) {
                    w.getBlockAt(x, y, z).setType(mat, false);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getEntity();
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.parkourWorld)) return;
        if (state != ParkourEventState.RUNNING) return;

        eliminated.add(p.getUniqueId());
        // Ensure their compass doesn't drop and they don't keep it
        e.getDrops().removeIf(settingsUI::isAnySettingsCompass);
        settingsUI.removeCompass(p);
        updateAlive();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getPlayer();
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.parkourWorld)) return;
        if (!eliminated.contains(p.getUniqueId())) return;
        if (cfg.parkourSpectatorSpawn != null) {
            e.setRespawnLocation(cfg.parkourSpectatorSpawn);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.setGameMode(GameMode.SPECTATOR);
            settingsUI.removeCompass(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        eliminated.remove(e.getPlayer().getUniqueId());
        updateAlive();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getPlayer();
        if (!isActive()) return;
        if (p.getWorld().getName().equalsIgnoreCase(cfg.parkourWorld)) {
            // entering parkour world
            if (!eliminated.contains(p.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () -> settingsUI.ensureCompass(p));
            } else {
                settingsUI.removeCompass(p);
            }
            updateAlive();
        } else if (e.getFrom().getName().equalsIgnoreCase(cfg.parkourWorld)) {
            // leaving parkour world
            settingsUI.removeCompass(p);
            updateAlive();
        }
    }
}


