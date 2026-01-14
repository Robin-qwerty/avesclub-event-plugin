package club.aves.anvildrop.event;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.mods.ModRegistry;
import club.aves.anvildrop.model.ArenaCuboid;
import club.aves.anvildrop.ui.AnvilDropScoreboard;
import club.aves.anvildrop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class AnvilDropEventManager implements Listener {

    private final Plugin plugin;
    private final AnvilDropScoreboard scoreboard;
    private final ModRegistry mods;
    private final DeadPermissionService deadPerms;
    private final Random random = new Random();

    private Runnable onEventEnd = null;

    private volatile EventState state = EventState.IDLE;

    private BukkitTask countdownTask;
    private BukkitTask dropTask;
    private BukkitTask aliveTask;

    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    // Players who became dead DURING the currently-running event.
    // Requirement: they should NOT be forced into spectator; only players entering already-dead should.
    private final Set<UUID> deadDuringThisEvent = new HashSet<>();

    private double currentPercent = 0.0;

    public AnvilDropEventManager(Plugin plugin, AnvilDropScoreboard scoreboard, ModRegistry mods, DeadPermissionService deadPerms) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
        this.mods = mods;
        this.deadPerms = deadPerms;
        this.scoreboard.startUpdater();
    }

    public EventState getState() {
        return state;
    }

    public void setOnEventEnd(Runnable onEventEnd) {
        this.onEventEnd = onEventEnd;
    }

    public boolean isActive() {
        return state != EventState.IDLE;
    }

    public boolean isDeadDuringThisEvent(UUID uuid) {
        return uuid != null && deadDuringThisEvent.contains(uuid);
    }

    public boolean shouldForceSpectator(Player p) {
        if (p == null) return false;
        // Only players entering already-dead should be forced spectator.
        boolean isDead = deadPerms.isDead(p) || deadPerms.getTrackedDeadUuids().contains(p.getUniqueId());
        return isActive() && isDead && !deadDuringThisEvent.contains(p.getUniqueId());
    }

    public boolean revivePlayer(Player player) {
        if (player == null) return false;
        eliminated.remove(player.getUniqueId());
        deadDuringThisEvent.remove(player.getUniqueId());
        updateAliveScoreboard();
        return true;
    }

    public void shutdown() {
        stopAllTasks();
        participants.clear();
        eliminated.clear();
        state = EventState.IDLE;
    }

    public void reload() {
        plugin.reloadConfig();
        scoreboard.startUpdater();
    }

    public boolean openEvent(Player initiator) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());

        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        World event = Bukkit.getWorld(cfg.eventWorld);
        if (lobby == null || event == null) return false;
        if (cfg.eventSpawn == null) return false;

        stopAllTasks();
        participants.clear();
        eliminated.clear();
        deadDuringThisEvent.clear();
        // Set OPEN before teleporting so "already dead -> spectator" logic applies immediately.
        state = EventState.OPEN;

        for (Player p : lobby.getPlayers()) {
            participants.add(p.getUniqueId());
            p.teleport(cfg.eventSpawn);
            // Only players who ENTER the event already-dead should be forced spectator.
            // Teleport can complete before the client/world is fully ready; set spectator shortly after teleport.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                PluginConfig c = PluginConfig.load(plugin.getConfig());
                if (!p.isOnline()) return;
                if (!p.getWorld().getName().equalsIgnoreCase(c.eventWorld)) return;
                if (shouldForceSpectator(p)) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
            }, 2L);
            int fadeIn = 10;
            int stay = cfg.openSeconds * 20;
            int fadeOut = 10;
            p.sendTitle(Text.color(cfg.openTitle), Text.color(cfg.openSubtitle), fadeIn, stay, fadeOut);
        }
        startAliveUpdater();
        return true;
    }

    public boolean startEvent() {
        if (state != EventState.OPEN && state != EventState.PAUSED) return false;

        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World event = Bukkit.getWorld(cfg.eventWorld);
        if (event == null) return false;

        stopAllTasks();
        state = EventState.COUNTDOWN;
        startAliveUpdater();

        // Refresh participants to include anyone currently in event world
        for (Player p : event.getPlayers()) {
            participants.add(p.getUniqueId());
        }

        int seconds = cfg.countdownSeconds;
        if (seconds <= 0) {
            beginRunning();
            return true;
        }

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = seconds;

            @Override
            public void run() {
                PluginConfig c = PluginConfig.load(plugin.getConfig());
                World w = Bukkit.getWorld(c.eventWorld);
                if (w == null) {
                    stopAllTasks();
                    state = EventState.IDLE;
                    return;
                }

                if (left <= 0) {
                    for (Player p : w.getPlayers()) {
                        p.sendMessage(Text.color(c.msgPrefix + c.countdownStartMessage));
                    }
                    beginRunning();
                    return;
                }

                String msg = Text.color(Text.color(c.msgPrefix) + Text.replacePlaceholders(c.countdownChatFormat, Map.of("seconds", String.valueOf(left))));
                for (Player p : w.getPlayers()) {
                    p.sendMessage(msg);
                }
                left--;
            }
        }, 0L, 20L);

        return true;
    }

    public boolean pause() {
        if (state != EventState.RUNNING) return false;
        if (dropTask != null) {
            dropTask.cancel();
            dropTask = null;
        }
        state = EventState.PAUSED;
        return true;
    }

    public boolean resume() {
        if (state != EventState.PAUSED) return false;
        beginRunning();
        return true;
    }

    public void stopEvent() {
        // Teleport players back before clearing state, so we can still read config.
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World event = Bukkit.getWorld(cfg.eventWorld);
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        Location lobbySpawn = cfg.lobbySpawn != null ? cfg.lobbySpawn : (lobby != null ? lobby.getSpawnLocation() : null);
        if (event != null && lobbySpawn != null) {
            for (Player p : event.getPlayers()) {
                p.teleport(lobbySpawn);
                // Requirement: when returning to lobby, ensure SURVIVAL
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        stopAllTasks();
        participants.clear();
        eliminated.clear();
        deadDuringThisEvent.clear();
        state = EventState.IDLE;
        updateAliveScoreboard();
        if (onEventEnd != null) {
            try { onEventEnd.run(); } catch (Throwable ignored) {}
        }
    }

    public boolean dropNow(double percent) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World w = Bukkit.getWorld(cfg.eventWorld);
        if (w == null) return false;
        ArenaCuboid arena = cfg.arena;
        if (arena == null) return false;
        if (!arena.isInWorld(w)) return false;
        Material mat = cfg.anvilMaterial != null ? cfg.anvilMaterial : Material.ANVIL;
        if (state == EventState.RUNNING || state == EventState.PAUSED) {
            currentPercent = Math.max(0.0, Math.min(100.0, percent));
        }
        doWave(w, arena, percent, mat, cfg.onlyReplaceAir);
        updateAliveScoreboard();
        return true;
    }

    private void beginRunning() {
        stopAllTasks();
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World event = Bukkit.getWorld(cfg.eventWorld);
        if (event == null) {
            state = EventState.IDLE;
            return;
        }

        state = EventState.RUNNING;
        currentPercent = cfg.initialPercent;
        startAliveUpdater();

        dropTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            PluginConfig c = PluginConfig.load(plugin.getConfig());
            World w = Bukkit.getWorld(c.eventWorld);
            if (w == null) return;

            Material mat = c.anvilMaterial != null ? c.anvilMaterial : Material.ANVIL;
            doWave(w, c.arena, currentPercent, mat, c.onlyReplaceAir);
            currentPercent = Math.min(c.maxPercent, currentPercent + c.increaseEachWave);
            updateAliveScoreboard();
        }, 0L, cfg.intervalSeconds * 20L);
    }

    private void doWave(World world, ArenaCuboid arena, double percent, Material anvilMat, boolean onlyReplaceAir) {
        if (arena == null || world == null) return;
        if (!arena.isInWorld(world)) return;

        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        double p = Math.max(0.0, Math.min(100.0, percent)) / 100.0;
        if (p <= 0.0) return;

        int minX = arena.minX(), minY = arena.minY(), minZ = arena.minZ();
        int maxX = arena.maxX(), maxY = arena.maxY(), maxZ = arena.maxZ();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (random.nextDouble() > p) continue;
                    var block = world.getBlockAt(x, y, z);
                    if (onlyReplaceAir && !block.getType().isAir()) continue;
                    // applyPhysics=true is what makes gravity blocks (anvils) actually fall like WorldEdit //replace does
                    block.setType(anvilMat, cfg.applyPhysics);
                }
            }
        }
    }

    private void stopAllTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (dropTask != null) {
            dropTask.cancel();
            dropTask = null;
        }
        if (aliveTask != null) {
            aliveTask.cancel();
            aliveTask = null;
        }
    }

    public int getAliveCount() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World event = Bukkit.getWorld(cfg.eventWorld);
        if (event == null) return 0;

        // Requirement: "all players in the world minus the players that died after the anvildrop started"
        int alive = event.getPlayers().size();
        for (Player p : event.getPlayers()) {
            if (mods.isMod(p.getUniqueId())) {
                alive--;
                continue;
            }
            if (eliminated.contains(p.getUniqueId()) || deadPerms.isDead(p)) alive--;
        }
        return Math.max(0, alive);
    }

    private void updateAliveScoreboard() {
        scoreboard.setAliveCount(getAliveCount());
    }

    private void startAliveUpdater() {
        if (aliveTask != null) return;
        aliveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAliveScoreboard, 1L, 20L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        // Only count deaths after anvils have started dropping
        if (state != EventState.RUNNING && state != EventState.PAUSED) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getEntity();
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.eventWorld)) return;
        if (mods.isMod(p.getUniqueId())) return;
        eliminated.add(p.getUniqueId());
        deadDuringThisEvent.add(p.getUniqueId());
        deadPerms.markDead(p);
        updateAliveScoreboard();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // If they rejoin during the same event, they stay eliminated.
        participants.remove(e.getPlayer().getUniqueId());
        updateAliveScoreboard();
    }
}


