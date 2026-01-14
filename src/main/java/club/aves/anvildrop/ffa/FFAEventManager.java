package club.aves.anvildrop.ffa;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.model.ArenaCuboid;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class FFAEventManager {

    private final JavaPlugin plugin;
    private final FFAKitManager kits;
    private final Random random = new Random();

    private volatile FFAEventState state = FFAEventState.IDLE;
    private final Set<UUID> participants = new HashSet<>();

    public FFAEventManager(JavaPlugin plugin, FFAKitManager kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    public boolean isActive() {
        return state != FFAEventState.IDLE;
    }

    public FFAEventState getState() {
        return state;
    }

    public boolean open() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        World ffa = Bukkit.getWorld(cfg.ffaWorld);
        if (lobby == null || ffa == null) return false;
        if (cfg.ffaOpenSpawn == null) return false;

        participants.clear();
        state = FFAEventState.OPEN;

        // Teleport lobby players into FFA open spawn
        for (Player p : lobby.getPlayers()) {
            participants.add(p.getUniqueId());
            p.teleport(cfg.ffaOpenSpawn);
            p.setGameMode(GameMode.SURVIVAL);
        }
        return true;
    }

    public boolean start(String kitName) {
        if (state != FFAEventState.OPEN) return false;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World ffa = Bukkit.getWorld(cfg.ffaWorld);
        if (ffa == null) return false;

        FFAKit kit = kits.load(kitName);
        if (kit == null) return false;

        state = FFAEventState.RUNNING;

        for (UUID id : Set.copyOf(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(cfg.ffaWorld)) continue;
            p.setGameMode(GameMode.SURVIVAL);
            Location spawn = randomInRegion(cfg.ffaStartRegion, ffa);
            if (spawn != null) p.teleport(spawn);
            giveKit(p, kit);
        }
        return true;
    }

    public void stop() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        World ffa = Bukkit.getWorld(cfg.ffaWorld);
        World lobby = Bukkit.getWorld(cfg.lobbyWorld);
        Location lobbySpawn = cfg.lobbySpawn != null ? cfg.lobbySpawn : (lobby != null ? lobby.getSpawnLocation() : null);
        if (ffa != null && lobbySpawn != null) {
            for (Player p : ffa.getPlayers()) {
                p.teleport(lobbySpawn);
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
        participants.clear();
        state = FFAEventState.IDLE;
    }

    private void giveKit(Player p, FFAKit kit) {
        var inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(kit.armor().toArray(new org.bukkit.inventory.ItemStack[0]));
        inv.setContents(kit.contents().toArray(new org.bukkit.inventory.ItemStack[0]));
        inv.setItemInOffHand(kit.offhand());
        p.updateInventory();
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


