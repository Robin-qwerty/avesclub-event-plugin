package club.aves.anvildrop.spleef;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.model.ArenaCuboid;
import club.aves.anvildrop.ui.AnvilDropScoreboard;
import club.aves.anvildrop.util.Text;
import club.aves.anvildrop.util.TeleportBatcher;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SpleefEventManager implements Listener {

    private record BlockSnapshot(int x, int y, int z, BlockData data) {}

    private final Plugin plugin;
    private final AnvilDropScoreboard scoreboard;
    private final DeadPermissionService deadPerms;

    private volatile SpleefEventState state = SpleefEventState.IDLE;
    private final Set<UUID> eliminated = new HashSet<>();
    private BukkitTask countdownTask;
    private BukkitTask timerTask;
    private Integer stopAt = null;
    private boolean ending = false;
    private boolean breakEnabled = false;
    private long startMillis = 0L;
    private List<BlockSnapshot> arenaSnapshot = new ArrayList<>();

    private club.aves.anvildrop.reconnect.ReconnectManager reconnect;

    private BukkitTask openIdleTask;
    private int openIdleSeconds = 0;

    public SpleefEventManager(Plugin plugin, AnvilDropScoreboard scoreboard, DeadPermissionService deadPerms) {
        this.plugin = plugin;
        this.scoreboard = scoreboard;
        this.deadPerms = deadPerms;
    }

    public void setReconnectManager(club.aves.anvildrop.reconnect.ReconnectManager reconnect) {
        this.reconnect = reconnect;
    }

    public SpleefEventState getState() {
        return state;
    }

    public boolean isActive() {
        return state != SpleefEventState.IDLE || ending;
    }

    public boolean isAcceptingJoins() {
        return state == SpleefEventState.OPEN && countdownTask == null;
    }

    public boolean open() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        World spleef = Bukkit.getWorld(cfg.spleefWorld);
        if (lobby == null || spleef == null) return false;
        if (cfg.spleefWaitingSpawn == null || cfg.spleefSpectatorSpawn == null) return false;

        eliminated.clear();
        stopAt = null;
        ending = false;
        breakEnabled = false;
        state = SpleefEventState.OPEN;
        startMillis = 0L;
        cancelCountdown();
        stopTimer();
        stopOpenIdleWatchdog();
        scoreboard.setTimeSecondsForWorld(cfg.spleefWorld, 0);
        scoreboard.setStopAtForWorld(cfg.spleefWorld, null);

        snapshotArena(cfg);

        var dead = new java.util.ArrayList<Player>();
        var alive = new java.util.ArrayList<Player>();
        for (Player p : lobby.getPlayers()) {
            if (shouldBeSpectator(p)) dead.add(p);
            else alive.add(p);
        }
        TeleportBatcher.preloadChunks(cfg.spleefWaitingSpawn, 5);
        TeleportBatcher.preloadChunks(cfg.spleefSpectatorSpawn, 5);

        TeleportBatcher.teleportInBatches(plugin, alive, cfg.spleefWaitingSpawn, 4, 2, (p) -> {
            p.setGameMode(GameMode.SURVIVAL);
        }, null);

        TeleportBatcher.teleportInBatches(plugin, dead, cfg.spleefSpectatorSpawn, 4, 2, (p) -> {
            eliminated.add(p.getUniqueId());
            p.setGameMode(GameMode.SPECTATOR);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (p.getWorld() == null || !p.getWorld().getName().equalsIgnoreCase(cfg.spleefWorld)) return;
                p.setGameMode(GameMode.SPECTATOR);
            }, 2L);
        }, null);

        broadcastEventOpened(cfg);
        updateAlive();
        startOpenIdleWatchdog();
        return true;
    }

    public boolean start(Integer stopAt) {
        if (state != SpleefEventState.OPEN) return false;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World spleef = Bukkit.getWorld(cfg.spleefWorld);
        if (spleef == null) return false;
        if (cfg.spleefStartSpawn == null || cfg.spleefSpectatorSpawn == null) return false;

        this.stopAt = stopAt;
        scoreboard.setStopAtForWorld(cfg.spleefWorld, stopAt);
        breakEnabled = false;
        state = SpleefEventState.COUNTDOWN;
        stopOpenIdleWatchdog();

        TeleportBatcher.runInBatches(plugin, List.copyOf(spleef.getPlayers()), 5, 1, (p) -> {
            if (shouldBeSpectator(p)) {
                eliminated.add(p.getUniqueId());
                p.teleport(cfg.spleefSpectatorSpawn);
                p.setGameMode(GameMode.SPECTATOR);
                return;
            }
            p.teleport(cfg.spleefStartSpawn);
            p.setGameMode(GameMode.SURVIVAL);
            giveShovel(p);
        }, null);

        int seconds = Math.max(0, plugin.getConfig().getInt("spleef.countdown.seconds", 5));
        if (seconds <= 0) {
            beginRunNow();
            return true;
        }

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = seconds;

            @Override
            public void run() {
                PluginConfig c = PluginConfig.load(plugin.getConfig());
                World w = Bukkit.getWorld(c.spleefWorld);
                if (w == null) {
                    cancelCountdown();
                    state = SpleefEventState.IDLE;
                    return;
                }

                if (left <= 0) {
                    cancelCountdown();
                    beginRunNow();
                    String t = plugin.getConfig().getString("spleef.countdown.goTitle", "&aGO!");
                    String st = plugin.getConfig().getString("spleef.countdown.goSubtitle", "&fBreak the floor!");
                    int goSecs = Math.max(1, plugin.getConfig().getInt("spleef.countdown.goTitleSeconds", 2));
                    for (Player p : w.getPlayers()) {
                        p.sendTitle(Text.color(t), Text.color(st), 5, goSecs * 20, 5);
                    }
                    return;
                }

                String fmt = plugin.getConfig().getString("spleef.countdown.chatFormat", "&eSpleef starts in &6{seconds}&e...");
                String msg = Text.color(Text.replacePlaceholders(fmt, java.util.Map.of("seconds", String.valueOf(left))));
                for (Player p : w.getPlayers()) {
                    p.sendMessage(msg);
                }
                left--;
            }
        }, 0L, 20L);

        return true;
    }

    public void stop() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World spleef = Bukkit.getWorld(cfg.spleefWorld);
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        Location lobbySpawn = cfg.lobbySpawn != null ? cfg.lobbySpawn : (lobby != null ? lobby.getSpawnLocation() : null);

        if (ending) return;
        ending = true;
        cancelCountdown();
        stopTimer();
        stopOpenIdleWatchdog();
        breakEnabled = false;
        stopAt = null;
        state = SpleefEventState.IDLE;
        scoreboard.setStopAtForWorld(cfg.spleefWorld, null);
        scoreboard.setTimeSecondsForWorld(cfg.spleefWorld, 0);
        broadcastEventEnded(cfg);
        updateAlive();

        if (spleef != null) {
            // Extra stop-grace messaging (configurable)
            String endedFmt = plugin.getConfig().getString("stopGrace.endedChat", "&c{event} has ended!");
            String nextFmt = plugin.getConfig().getString("stopGrace.nextRoundChat", "&aYou're going to the next round!");
            String eventName = plugin.getConfig().getString("eventBroadcast.names.spleef", "Spleef");
            String endedMsg = Text.color(cfg.msgPrefix + Text.replacePlaceholders(endedFmt, java.util.Map.of("event", eventName)));
            String nextMsg = Text.color(cfg.msgPrefix + Text.replacePlaceholders(nextFmt, java.util.Map.of("event", eventName)));
            java.util.Set<java.util.UUID> trackedDead = (deadPerms != null) ? deadPerms.getTrackedDeadUuids() : java.util.Set.of();
            for (Player p : spleef.getPlayers()) {
                p.sendMessage(endedMsg);
                boolean isDead = deadPerms != null && (deadPerms.isDead(p) || trackedDead.contains(p.getUniqueId()));
                if (!p.hasPermission("event.admin") && !isDead) {
                    p.sendMessage(nextMsg);
                }
            }
            for (Player p : spleef.getPlayers()) {
                p.setInvulnerable(true);
            }
        }

        int delay = Math.max(0, plugin.getConfig().getInt("stopGrace.seconds", 3));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (spleef != null && lobbySpawn != null) {
                TeleportBatcher.preloadChunks(lobbySpawn, 5);
                TeleportBatcher.teleportInBatches(plugin, spleef.getPlayers(), lobbySpawn, 4, 2, (p) -> {
                    p.setInvulnerable(false);
                    clearInventory(p);
                    p.setGameMode(GameMode.SURVIVAL);
                }, () -> {
                    restoreArena(cfg);
                    eliminated.clear();
                    ending = false;
                    updateAlive();
                });
            } else if (spleef != null) {
                for (Player p : spleef.getPlayers()) {
                    p.setInvulnerable(false);
                }
                restoreArena(cfg);
                eliminated.clear();
                ending = false;
                updateAlive();
            }
        }, delay * 20L);
    }

    private void startOpenIdleWatchdog() {
        stopOpenIdleWatchdog();
        openIdleSeconds = 0;
        openIdleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != SpleefEventState.OPEN) {
                stopOpenIdleWatchdog();
                return;
            }
            PluginConfig cfg = PluginConfig.load(plugin.getConfig());
            World w = Bukkit.getWorld(cfg.spleefWorld);
            boolean serverEmpty = Bukkit.getOnlinePlayers().isEmpty();
            boolean worldEmpty = (w == null || w.getPlayers().isEmpty());
            if (serverEmpty || worldEmpty) {
                openIdleSeconds++;
            } else {
                openIdleSeconds = 0;
            }
            int limit = Math.max(5, plugin.getConfig().getInt("autoStop.openEmptySeconds", 30));
            if (openIdleSeconds >= limit) {
                stop();
            }
        }, 20L, 20L);
    }

    private void stopOpenIdleWatchdog() {
        if (openIdleTask != null) {
            openIdleTask.cancel();
            openIdleTask = null;
        }
        openIdleSeconds = 0;
    }

    public boolean isEliminated(UUID uuid) {
        return uuid != null && eliminated.contains(uuid);
    }

    public void refreshAliveCount() {
        updateAlive();
    }

    public void revive(Player p) {
        if (p == null) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        eliminated.remove(p.getUniqueId());
        // Requirement: revive in spleef should send them to the START area and give shovel if missing.
        if (cfg.spleefStartSpawn != null) {
            p.teleport(cfg.spleefStartSpawn);
        }
        if (!hasSpleefShovel(p)) {
            giveShovel(p);
        }
        p.setGameMode(GameMode.SURVIVAL);
        updateAlive();
    }

    private void beginRunNow() {
        state = SpleefEventState.RUNNING;
        breakEnabled = true;
        startMillis = System.currentTimeMillis();
        stopTimer();
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != SpleefEventState.RUNNING) return;
            PluginConfig c = PluginConfig.load(plugin.getConfig());
            int secs = (int) ((System.currentTimeMillis() - startMillis) / 1000L);
            scoreboard.setTimeSecondsForWorld(c.spleefWorld, secs);
        }, 0L, 20L);
        updateAlive();
    }

    private void updateAlive() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        scoreboard.setAliveCountForWorld(cfg.spleefWorld, getAliveCount());
        if (stopAt != null) checkStopAt();
    }

    public int getAliveCount() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World w = Bukkit.getWorld(cfg.spleefWorld);
        if (w == null) return 0;
        int alive = w.getPlayers().size();
        for (Player p : w.getPlayers()) {
            if (p.hasPermission("event.admin")) {
                alive--;
                continue;
            }
            if (deadPerms != null && deadPerms.isDead(p)) {
                alive--;
                continue;
            }
            if (eliminated.contains(p.getUniqueId())) alive--;
        }
        if (reconnect != null) {
            alive += reconnect.getPendingAliveForWorld(cfg.spleefWorld);
        }
        return Math.max(0, alive);
    }

    private void checkStopAt() {
        if (stopAt == null || ending) return;
        int alive = getAliveCount();
        if (alive > stopAt) return;
        endDueToStopAt();
    }

    private void endDueToStopAt() {
        ending = true;
        cancelCountdown();
        stopTimer();
        breakEnabled = false;
        state = SpleefEventState.IDLE;

        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        broadcastEventEnded(cfg);
        World w = Bukkit.getWorld(cfg.spleefWorld);
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
                    clearInventory(p);
                    p.teleport(lobbySpawn);
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }
            restoreArena(cfg);
            eliminated.clear();
            stopAt = null;
            ending = false;
            scoreboard.setStopAtForWorld(cfg.spleefWorld, null);
            scoreboard.setTimeSecondsForWorld(cfg.spleefWorld, 0);
            updateAlive();
        }, 100L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void giveShovel(Player p) {
        ItemStack shovel = new ItemStack(Material.DIAMOND_SHOVEL, 1);
        ItemMeta meta = shovel.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
            shovel.setItemMeta(meta);
        }
        p.getInventory().addItem(shovel);
        p.updateInventory();
    }

    private void snapshotArena(PluginConfig cfg) {
        World w = Bukkit.getWorld(cfg.spleefWorld);
        ArenaCuboid arena = cfg.spleefArena;
        if (w == null || arena == null || !arena.isInWorld(w)) return;
        List<BlockSnapshot> snap = new ArrayList<>();
        for (int x = arena.minX(); x <= arena.maxX(); x++) {
            for (int y = arena.minY(); y <= arena.maxY(); y++) {
                for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
                    BlockData data = w.getBlockAt(x, y, z).getBlockData();
                    snap.add(new BlockSnapshot(x, y, z, data));
                }
            }
        }
        arenaSnapshot = snap;
    }

    private void restoreArena(PluginConfig cfg) {
        World w = Bukkit.getWorld(cfg.spleefWorld);
        if (w == null || arenaSnapshot == null || arenaSnapshot.isEmpty()) return;
        for (BlockSnapshot s : arenaSnapshot) {
            w.getBlockAt(s.x, s.y, s.z).setBlockData(s.data, false);
        }
    }

    private boolean shouldBeSpectator(Player p) {
        if (p == null) return false;
        if (p.hasPermission("event.admin")) return false;
        return deadPerms != null && deadPerms.isDead(p);
    }

    private boolean hasSpleefShovel(Player p) {
        if (p == null) return false;
        var inv = p.getInventory();
        if (inv.getItemInMainHand() != null && inv.getItemInMainHand().getType() == Material.DIAMOND_SHOVEL) return true;
        if (inv.getItemInOffHand() != null && inv.getItemInOffHand().getType() == Material.DIAMOND_SHOVEL) return true;
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() == Material.DIAMOND_SHOVEL) return true;
        }
        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase(cfg.spleefWorld)) return;
        if (!isActive()) return;

        if (state != SpleefEventState.RUNNING || !breakEnabled) {
            e.setCancelled(true);
            return;
        }

        Material breakable = Material.matchMaterial(plugin.getConfig().getString("spleef.breakableBlock", "SNOW_BLOCK"));
        if (breakable == null) breakable = Material.SNOW_BLOCK;
        if (e.getBlock().getType() != breakable) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!e.getBlock().getWorld().getName().equalsIgnoreCase(cfg.spleefWorld)) return;
        if (!isActive()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getEntity();
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.spleefWorld)) return;
        if (!isActive()) return;
        if (p.hasPermission("event.admin")) return;
        eliminated.add(p.getUniqueId());
        if (deadPerms != null) deadPerms.markDead(p);
        updateAlive();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getPlayer();
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.spleefWorld)) return;
        if (!eliminated.contains(p.getUniqueId()) && (deadPerms == null || !deadPerms.isDead(p))) return;
        if (cfg.spleefSpectatorSpawn != null) {
            e.setRespawnLocation(cfg.spleefSpectatorSpawn);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.setGameMode(GameMode.SPECTATOR);
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getPlayer();
        if (!isActive()) return;
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.spleefWorld)) return;
        if (shouldBeSpectator(p) && cfg.spleefSpectatorSpawn != null) {
            eliminated.add(p.getUniqueId());
            p.teleport(cfg.spleefSpectatorSpawn);
            // Teleport can race with client/world init; set spectator 1 tick later for reliability.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                if (p.getWorld() == null || !p.getWorld().getName().equalsIgnoreCase(cfg.spleefWorld)) return;
                p.setGameMode(GameMode.SPECTATOR);
            });
        }
        updateAlive();
    }

    private void broadcastEventOpened(PluginConfig cfg) {
        String fmt = plugin.getConfig().getString("eventBroadcast.opened", "&a{event} is opened");
        String name = plugin.getConfig().getString("eventBroadcast.names.spleef", "Spleef");
        String msg = Text.replacePlaceholders(fmt, java.util.Map.of("event", name));
        Bukkit.broadcastMessage(Text.color(cfg.msgPrefix + msg));
    }

    private void broadcastEventEnded(PluginConfig cfg) {
        String fmt = plugin.getConfig().getString("eventBroadcast.ended", "&c{event} has ended");
        String name = plugin.getConfig().getString("eventBroadcast.names.spleef", "Spleef");
        String msg = Text.replacePlaceholders(fmt, java.util.Map.of("event", name));
        Bukkit.broadcastMessage(Text.color(cfg.msgPrefix + msg));
    }

    private static void clearInventory(Player p) {
        if (p == null) return;
        var inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[0]);
        inv.setItemInOffHand(null);
        p.updateInventory();
    }
}


