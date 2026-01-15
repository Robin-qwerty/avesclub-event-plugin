package club.aves.anvildrop.ffa;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.model.ArenaCuboid;
import club.aves.anvildrop.ui.AnvilDropScoreboard;
import club.aves.anvildrop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class FFAEventManager implements Listener {

    private final JavaPlugin plugin;
    private final FFAKitManager kits;
    private final DeadPermissionService deadPerms;
    private final AnvilDropScoreboard scoreboard;
    private final Random random = new Random();

    private volatile FFAEventState state = FFAEventState.IDLE;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();

    private BukkitTask countdownTask;
    private BukkitTask timerTask;
    private boolean pvpAllowed = false;
    private Integer stopAt = null;
    private boolean ending = false;
    private long startMillis = 0L;

    private club.aves.anvildrop.reconnect.ReconnectManager reconnect;

    public FFAEventManager(JavaPlugin plugin, FFAKitManager kits, DeadPermissionService deadPerms, AnvilDropScoreboard scoreboard) {
        this.plugin = plugin;
        this.kits = kits;
        this.deadPerms = deadPerms;
        this.scoreboard = scoreboard;
    }

    public void setReconnectManager(club.aves.anvildrop.reconnect.ReconnectManager reconnect) {
        this.reconnect = reconnect;
    }

    public boolean isActive() {
        return state != FFAEventState.IDLE || ending;
    }

    public FFAEventState getState() {
        return state;
    }

    public boolean isAcceptingJoins() {
        return state == FFAEventState.OPEN && countdownTask == null;
    }

    public boolean open() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        World ffa = Bukkit.getWorld(cfg.ffaWorld);
        if (lobby == null || ffa == null) return false;
        if (cfg.ffaOpenSpawn == null) return false;

        participants.clear();
        eliminated.clear();
        cancelCountdown();
        stopTimer();
        stopAt = null;
        ending = false;
        state = FFAEventState.OPEN;
        setPvp(false);
        clearGroundItems(ffa);
        scoreboard.setTimeSecondsForWorld(cfg.ffaWorld, 0);

        // Teleport lobby players into FFA open spawn
        for (Player p : lobby.getPlayers()) {
            participants.add(p.getUniqueId());
            p.teleport(cfg.ffaOpenSpawn);
            p.setGameMode(GameMode.SURVIVAL);
        }
        broadcastEventOpened(cfg);
        updateAlive();
        return true;
    }

    public boolean start(String kitName, Integer stopAt) {
        if (state != FFAEventState.OPEN) return false;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World ffa = Bukkit.getWorld(cfg.ffaWorld);
        if (ffa == null) return false;

        FFAKit kit = kits.load(kitName);
        if (kit == null) return false;

        if (countdownTask != null) return false;
        eliminated.clear();
        pvpAllowed = false;
        setPvp(false);
        this.stopAt = stopAt;
        this.ending = false;
        scoreboard.setStopAtForWorld(cfg.ffaWorld, stopAt);

        // Teleport + kit immediately, then countdown, then enable pvp
        for (UUID id : Set.copyOf(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(cfg.ffaWorld)) continue;
            // Requirement: if marked dead, do NOT give kit and put them in spectator at ffaSpectator spawn.
            if (deadPerms != null && deadPerms.isDead(p) && !p.hasPermission("event.admin")) {
                eliminated.add(p.getUniqueId());
                clearInventory(p);
                if (cfg.ffaSpectatorSpawn != null) {
                    p.teleport(cfg.ffaSpectatorSpawn);
                }
                p.setGameMode(GameMode.SPECTATOR);
                continue;
            }

            p.setGameMode(GameMode.SURVIVAL);
            Location spawn = randomInRegion(cfg.ffaStartRegion, ffa);
            if (spawn != null) p.teleport(spawn);
            giveKit(p, kit);
        }

        int seconds = Math.max(0, plugin.getConfig().getInt("ffa.countdown.seconds", 15));
        if (seconds <= 0) {
            state = FFAEventState.RUNNING;
            setPvp(true);
            showGoTitle(ffa);
            startTimer();
            updateAlive();
            return true;
        }

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = seconds;

            @Override
            public void run() {
                PluginConfig c = PluginConfig.load(plugin.getConfig());
                World w = Bukkit.getWorld(c.ffaWorld);
                if (w == null) {
                    cancelCountdown();
                    state = FFAEventState.IDLE;
                    return;
                }

                if (left <= 0) {
                    cancelCountdown();
                    state = FFAEventState.RUNNING;
                    setPvp(true);
                    showGoTitle(w);
                    startTimer();
                    updateAlive();
                    return;
                }

                String fmt = plugin.getConfig().getString("ffa.countdown.chatFormat", "&eFFA starts in &6{seconds}&e...");
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
        World ffa = Bukkit.getWorld(cfg.ffaWorld);
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        Location lobbySpawn = cfg.lobbySpawn != null ? cfg.lobbySpawn : (lobby != null ? lobby.getSpawnLocation() : null);
        setPvp(false);
        cancelCountdown();
        stopTimer();
        stopAt = null;
        ending = false;
        if (ffa != null) clearGroundItems(ffa);
        if (ffa != null && lobbySpawn != null) {
            for (Player p : ffa.getPlayers()) {
                clearInventory(p);
                p.teleport(lobbySpawn);
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
        participants.clear();
        eliminated.clear();
        state = FFAEventState.IDLE;
        broadcastEventEnded(cfg);
        scoreboard.setStopAtForWorld(cfg.ffaWorld, null);
        scoreboard.setTimeSecondsForWorld(cfg.ffaWorld, 0);
        updateAlive();
    }

    private void giveKit(Player p, FFAKit kit) {
        var inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(kit.armor().toArray(new org.bukkit.inventory.ItemStack[0]));
        inv.setContents(kit.contents().toArray(new org.bukkit.inventory.ItemStack[0]));
        inv.setItemInOffHand(kit.offhand());
        p.updateInventory();
    }

    private void updateAlive() {
        if (scoreboard == null) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        scoreboard.setAliveCountForWorld(cfg.ffaWorld, getAliveCount());
        if (stopAt != null) checkStopAt();
    }

    public int getAliveCount() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World w = Bukkit.getWorld(cfg.ffaWorld);
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
        // Players who disconnected during the event are considered "alive" during the reconnect grace window.
        if (reconnect != null) {
            alive += reconnect.getPendingAliveForWorld(cfg.ffaWorld);
        }
        return Math.max(0, alive);
    }

    public boolean isEliminated(UUID uuid) {
        return uuid != null && eliminated.contains(uuid);
    }

    public void refreshAliveCount() {
        updateAlive();
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void startTimer() {
        stopTimer();
        startMillis = System.currentTimeMillis();
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != FFAEventState.RUNNING) return;
            PluginConfig c = PluginConfig.load(plugin.getConfig());
            int secs = (int) ((System.currentTimeMillis() - startMillis) / 1000L);
            scoreboard.setTimeSecondsForWorld(c.ffaWorld, secs);
        }, 0L, 20L);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void showGoTitle(World w) {
        String t = plugin.getConfig().getString("ffa.countdown.goTitle", "&aGO!");
        String st = plugin.getConfig().getString("ffa.countdown.goSubtitle", "&fFight!");
        int goSecs = Math.max(1, plugin.getConfig().getInt("ffa.countdown.goTitleSeconds", 2));
        for (Player p : w.getPlayers()) {
            p.sendTitle(Text.color(t), Text.color(st), 5, goSecs * 20, 5);
        }
    }

    private void setPvp(boolean allow) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        String region = cfg.ffaWorldGuardRegion;
        if (region == null || region.isBlank()) return;
        if (pvpAllowed == allow) return;
        pvpAllowed = allow;

        String flagValue = allow ? "allow" : "deny";
        String worldName = cfg.ffaWorldGuardWorld;
        boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "rg flag -w " + worldName + " " + region + " pvp " + flagValue);
        if (!ok) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "region flag -w " + worldName + " " + region + " pvp " + flagValue);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getEntity();
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.ffaWorld)) return;
        if (!isActive()) return;
        if (p.hasPermission("event.admin")) return;

        eliminated.add(p.getUniqueId());
        if (deadPerms != null) deadPerms.markDead(p);
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
        state = FFAEventState.IDLE;
        setPvp(false);
        cancelCountdown();
        stopTimer();

        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World w = Bukkit.getWorld(cfg.ffaWorld);
        if (w != null) clearGroundItems(w);
        broadcastEventEnded(cfg);
        if (w != null) {
            // Broadcast end
            for (Player p : w.getPlayers()) {
                p.sendMessage(Text.color(cfg.msgPrefix + cfg.msgEventEnded));
                if (deadPerms == null || !deadPerms.isDead(p)) {
                    p.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNextRoundChat));
                    p.sendTitle(Text.color(cfg.msgNextRoundTitle), Text.color(cfg.msgNextRoundSubtitle), 5, cfg.msgNextRoundTitleSeconds * 20, 5);
                }
            }
        }

        // After 5s, teleport everyone to lobby
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World lobby = Bukkit.getWorld(cfg.lobbyWorld);
            Location lobbySpawn = cfg.lobbySpawn != null ? cfg.lobbySpawn : (lobby != null ? lobby.getSpawnLocation() : null);
            if (w != null && lobbySpawn != null) {
                clearGroundItems(w);
                for (Player p : w.getPlayers()) {
                    clearInventory(p);
                    p.teleport(lobbySpawn);
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }
            participants.clear();
            eliminated.clear();
            stopAt = null;
            ending = false;
            scoreboard.setStopAtForWorld(cfg.ffaWorld, null);
            scoreboard.setTimeSecondsForWorld(cfg.ffaWorld, 0);
            updateAlive();
        }, 100L);
    }

    private void clearGroundItems(World w) {
        if (w == null) return;
        for (Item it : w.getEntitiesByClass(Item.class)) {
            it.remove();
        }
    }

    private void broadcastEventOpened(PluginConfig cfg) {
        String fmt = plugin.getConfig().getString("eventBroadcast.opened", "&a{event} is opened");
        String name = plugin.getConfig().getString("eventBroadcast.names.ffa", "FFA");
        String msg = Text.replacePlaceholders(fmt, java.util.Map.of("event", name));
        Bukkit.broadcastMessage(Text.color(cfg.msgPrefix + msg));
    }

    private void broadcastEventEnded(PluginConfig cfg) {
        String fmt = plugin.getConfig().getString("eventBroadcast.ended", "&c{event} has ended");
        String name = plugin.getConfig().getString("eventBroadcast.names.ffa", "FFA");
        String msg = Text.replacePlaceholders(fmt, java.util.Map.of("event", name));
        Bukkit.broadcastMessage(Text.color(cfg.msgPrefix + msg));
    }

    private static void clearInventory(Player p) {
        if (p == null) return;
        var inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new org.bukkit.inventory.ItemStack[0]);
        inv.setItemInOffHand(null);
        p.updateInventory();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getPlayer();
        if (!p.getWorld().getName().equalsIgnoreCase(cfg.ffaWorld)) return;
        if (!eliminated.contains(p.getUniqueId())) return;
        if (cfg.ffaSpectatorSpawn != null) {
            e.setRespawnLocation(cfg.ffaSpectatorSpawn);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.setGameMode(GameMode.SPECTATOR);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        eliminated.remove(e.getPlayer().getUniqueId());
        participants.remove(e.getPlayer().getUniqueId());
        updateAlive();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!isActive()) return;
        Player p = e.getPlayer();
        if (p.getWorld().getName().equalsIgnoreCase(cfg.ffaWorld) || e.getFrom().getName().equalsIgnoreCase(cfg.ffaWorld)) {
            updateAlive();
        }
    }

    private Location randomInRegion(ArenaCuboid region, World world) {
        if (region == null || world == null) return null;
        int minX = region.minX(), maxX = region.maxX();
        int minY = region.minY(), maxY = region.maxY();
        int minZ = region.minZ(), maxZ = region.maxZ();
        int x = randBetween(minX, maxX);
        int y = randBetween(minY, maxY);
        int z = randBetween(minZ, maxZ);
        return new Location(world, x + 0.5, y + 0.1, z + 0.5);
    }

    private int randBetween(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        if (min == max) return min;
        return min + random.nextInt((max - min) + 1);
    }
}


