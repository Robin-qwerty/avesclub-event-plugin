package club.aves.anvildrop.parkour;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.model.ArenaCuboid;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.ui.AnvilDropScoreboard;
import club.aves.anvildrop.ui.EventSettingsUI;
import club.aves.anvildrop.util.Text;
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
import org.bukkit.event.player.PlayerMoveEvent;
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
    private final DeadPermissionService deadPerms;

    private volatile ParkourEventState state = ParkourEventState.IDLE;
    private final Set<UUID> eliminated = new HashSet<>();
    private final Set<UUID> finished = new HashSet<>();
    private BukkitTask timerTask;
    private BukkitTask countdownTask;
    private Integer stopAt = null;
    private boolean ending = false;
    private long startMillis = 0L;

    private club.aves.anvildrop.reconnect.ReconnectManager reconnect;

    public ParkourEventManager(Plugin plugin, AnvilDropScoreboard scoreboard, EventSettingsUI settingsUI, DeadPermissionService deadPerms) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
        this.settingsUI = settingsUI;
        this.deadPerms = deadPerms;
    }

    public void setReconnectManager(club.aves.anvildrop.reconnect.ReconnectManager reconnect) {
        this.reconnect = reconnect;
    }

    public ParkourEventState getState() {
        return state;
    }

    public boolean isAcceptingJoins() {
        return state == ParkourEventState.OPEN && countdownTask == null;
    }

    public boolean isActive() {
        return state != ParkourEventState.IDLE || ending;
    }

    public boolean open() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World parkour = Bukkit.getWorld(cfg.parkourWorld);
        if (parkour == null) return false;
        if (cfg.parkourSpawn == null) return false;

        eliminated.clear();
        finished.clear();
        stopAt = null;
        ending = false;
        state = ParkourEventState.OPEN;
        startMillis = 0L;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
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
        scoreboard.setFinishedCountForWorld(cfg.parkourWorld, finished.size());
        broadcastEventOpened(cfg);
        return true;
    }

    public boolean start() {
        return start(null);
    }

    public boolean start(Integer stopAt) {
        if (state != ParkourEventState.OPEN) return false;
        if (countdownTask != null) return false;
        this.stopAt = stopAt;
        scoreboard.setStopAtForWorld(PluginConfig.load(plugin.getConfig()).parkourWorld, stopAt);
        int seconds = Math.max(0, plugin.getConfig().getInt("parkour.countdown.seconds", 5));

        if (seconds <= 0) {
            beginRunNow();
            return true;
        }

        // Countdown in chat in parkour world
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = seconds;

            @Override
            public void run() {
                PluginConfig c = PluginConfig.load(plugin.getConfig());
                World w = Bukkit.getWorld(c.parkourWorld);
                if (w == null) {
                    stopCountdown();
                    return;
                }

                if (left <= 0) {
                    stopCountdown();
                    beginRunNow();
                    // GO title
                    String t = plugin.getConfig().getString("parkour.countdown.goTitle", "&aGO!");
                    String st = plugin.getConfig().getString("parkour.countdown.goSubtitle", "&fGood luck!");
                    int goSecs = Math.max(1, plugin.getConfig().getInt("parkour.countdown.goTitleSeconds", 2));
                    for (Player p : w.getPlayers()) {
                        p.sendTitle(Text.color(t), Text.color(st), 5, goSecs * 20, 5);
                    }
                    return;
                }

                String fmt = plugin.getConfig().getString("parkour.countdown.chatFormat", "&eParkour starts in &6{seconds}&e...");
                String msg = Text.color(Text.replacePlaceholders(fmt, java.util.Map.of("seconds", String.valueOf(left))));
                for (Player p : w.getPlayers()) {
                    p.sendMessage(msg);
                }
                left--;
            }
        }, 0L, 20L);

        return true;
    }

    private void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void beginRunNow() {
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
        finished.clear();
        state = ParkourEventState.IDLE;
        stopAt = null;
        ending = false;
        startMillis = 0L;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        scoreboard.setTimeSecondsForWorld(cfg.parkourWorld, 0);
        scoreboard.setFinishedCountForWorld(cfg.parkourWorld, 0);
        scoreboard.setStopAtForWorld(cfg.parkourWorld, null);
        broadcastEventEnded(cfg);
        updateAlive();
    }

    private void updateAlive() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        scoreboard.setAliveCountForWorld(cfg.parkourWorld, getAliveCount());
        scoreboard.setFinishedCountForWorld(cfg.parkourWorld, finished.size());
        if (stopAt != null) checkStopAt();
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
            if (finished.contains(p.getUniqueId())) {
                alive--;
                continue;
            }
            if (eliminated.contains(p.getUniqueId())) alive--;
        }
        if (reconnect != null) {
            alive += reconnect.getPendingAliveForWorld(cfg.parkourWorld);
        }
        return Math.max(0, alive);
    }

    public boolean isEliminated(UUID uuid) {
        return uuid != null && eliminated.contains(uuid);
    }

    public boolean isFinished(UUID uuid) {
        return uuid != null && finished.contains(uuid);
    }

    public void refreshAliveCount() {
        updateAlive();
    }

    public boolean isEliminated(Player p) {
        return p != null && eliminated.contains(p.getUniqueId());
    }

    public void reviveToStart(Player p) {
        if (p == null) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        eliminated.remove(p.getUniqueId());
        finished.remove(p.getUniqueId());
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
        if (p.hasPermission("event.admin")) return;

        eliminated.add(p.getUniqueId());
        if (deadPerms != null) deadPerms.markDead(p);
        // Ensure their compass doesn't drop and they don't keep it
        e.getDrops().removeIf(settingsUI::isAnySettingsCompass);
        settingsUI.removeCompass(p);
        updateAlive();
    }

    private void checkStopAt() {
        if (stopAt == null || ending) return;
        int alive = getAliveCount();
        if (alive > stopAt) return;
        endDueToStopAt();
    }

    private void endDueToStopAt() {
        ending = true;
        stopCountdown();
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        state = ParkourEventState.IDLE;

        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        broadcastEventEnded(cfg);
        World w = Bukkit.getWorld(cfg.parkourWorld);
        if (w != null) {
            for (Player p : w.getPlayers()) {
                p.sendMessage(Text.color(cfg.msgPrefix + cfg.msgEventEnded));
                if (deadPerms == null || !deadPerms.isDead(p)) {
                    p.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNextRoundChat));
                    p.sendTitle(Text.color(cfg.msgNextRoundTitle), Text.color(cfg.msgNextRoundSubtitle), 5, cfg.msgNextRoundTitleSeconds * 20, 5);
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World lobby = Bukkit.getWorld(cfg.lobbyWorld);
            Location lobbySpawn = cfg.lobbySpawn != null ? cfg.lobbySpawn : (lobby != null ? lobby.getSpawnLocation() : null);
            if (w != null && lobbySpawn != null) {
                for (Player p : w.getPlayers()) {
                    p.teleport(lobbySpawn);
                    p.setGameMode(GameMode.SURVIVAL);
                    settingsUI.removeCompass(p);
                }
            }
            eliminated.clear();
            finished.clear();
            stopAt = null;
            ending = false;
            scoreboard.setTimeSecondsForWorld(cfg.parkourWorld, 0);
            scoreboard.setFinishedCountForWorld(cfg.parkourWorld, 0);
            scoreboard.setStopAtForWorld(cfg.parkourWorld, null);
            updateAlive();
        }, 100L);
    }

    private void broadcastEventOpened(PluginConfig cfg) {
        String fmt = plugin.getConfig().getString("eventBroadcast.opened", "&a{event} is opened");
        String name = plugin.getConfig().getString("eventBroadcast.names.parkour", "Parkour");
        String msg = Text.replacePlaceholders(fmt, java.util.Map.of("event", name));
        Bukkit.broadcastMessage(Text.color(cfg.msgPrefix + msg));
    }

    private void broadcastEventEnded(PluginConfig cfg) {
        String fmt = plugin.getConfig().getString("eventBroadcast.ended", "&c{event} has ended");
        String name = plugin.getConfig().getString("eventBroadcast.names.parkour", "Parkour");
        String msg = Text.replacePlaceholders(fmt, java.util.Map.of("event", name));
        Bukkit.broadcastMessage(Text.color(cfg.msgPrefix + msg));
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
    public void onMove(PlayerMoveEvent e) {
        if (state != ParkourEventState.RUNNING) return;
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getPlayer();
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.parkourWorld)) return;
        if (p.hasPermission("event.admin")) return;
        if (eliminated.contains(p.getUniqueId())) return;
        if (finished.contains(p.getUniqueId())) return;

        // Finish if player enters the configured end region
        ArenaCuboid end = cfg.parkourEndRegion;
        if (end != null && end.contains(p.getLocation())) {
            finished.add(p.getUniqueId());
            p.setGameMode(GameMode.SPECTATOR);
            settingsUI.removeCompass(p);
            // Per-player finish title/subtitle
            String title = plugin.getConfig().getString("parkour.finish.title", "&aYou finished the parkour successfully!");
            String subtitle = plugin.getConfig().getString("parkour.finish.subtitle", "&fYou're going to the next round");
            int secs = Math.max(1, plugin.getConfig().getInt("parkour.finish.titleSeconds", 3));
            p.sendTitle(Text.color(title), Text.color(subtitle), 5, secs * 20, 5);

            // Broadcast finish message to everyone
            String fmt = plugin.getConfig().getString("parkourMessages.playerFinished", "&a{player} &7finished the parkour! &7(&f{pkfinished}&7 finished)");
            String msg = Text.color(Text.replacePlaceholders(fmt, java.util.Map.of(
                    "player", p.getName(),
                    "pkfinished", String.valueOf(finished.size())
            )));
            Bukkit.broadcastMessage(Text.color(cfg.msgPrefix) + msg);

            updateAlive();
        }
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


