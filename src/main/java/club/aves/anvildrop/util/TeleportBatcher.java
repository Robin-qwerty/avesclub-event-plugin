package club.aves.anvildrop.util;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.function.Consumer;

public final class TeleportBatcher {
    private TeleportBatcher() {}

    public static void preloadChunks(Location center, int radiusChunks) {
        if (center == null || center.getWorld() == null) return;
        World w = center.getWorld();
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                Chunk c = w.getChunkAt(cx + dx, cz + dz);
                if (!c.isLoaded()) c.load();
            }
        }
    }

    public static BukkitTask runInBatches(Plugin plugin,
                                          Collection<? extends Player> players,
                                          int batchSize,
                                          int intervalTicks,
                                          Consumer<Player> action,
                                          Runnable onDone) {
        if (plugin == null || players == null) {
            if (onDone != null) onDone.run();
            return null;
        }
        Queue<Player> queue = new ArrayDeque<>(players);
        int perTick = Math.max(1, batchSize);
        int interval = Math.max(1, intervalTicks);
        if (plugin.getConfig().getBoolean("debug.logBatchOps", true)) {
            Debug.log(plugin, "Batch action started: count=" + queue.size()
                    + ", batchSize=" + perTick + ", intervalTicks=" + interval);
        }

        final BukkitTask[] taskRef = new BukkitTask[1];
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int count = 0;
            while (count < perTick && !queue.isEmpty()) {
                Player p = queue.poll();
                if (p != null && p.isOnline()) {
                    if (action != null) action.accept(p);
                }
                count++;
            }
            if (queue.isEmpty()) {
                if (onDone != null) onDone.run();
                if (taskRef[0] != null) taskRef[0].cancel();
                if (plugin.getConfig().getBoolean("debug.logBatchOps", true)) {
                    Debug.log(plugin, "Batch action completed.");
                }
            }
        }, 0L, interval);
        taskRef[0] = task;
        return task;
    }

    public static BukkitTask teleportInBatches(Plugin plugin,
                                               Collection<? extends Player> players,
                                               Location target,
                                               int batchSize,
                                               int intervalTicks,
                                               Consumer<Player> afterTeleport,
                                               Runnable onDone) {
        if (plugin == null || players == null || target == null) {
            if (onDone != null) onDone.run();
            return null;
        }
        Queue<Player> queue = new ArrayDeque<>(players);
        int perTick = Math.max(1, batchSize);
        int interval = Math.max(1, intervalTicks);
        if (plugin.getConfig().getBoolean("debug.logBatchOps", true)) {
            Debug.log(plugin, "Batch teleport started: count=" + queue.size()
                    + ", batchSize=" + perTick + ", intervalTicks=" + interval
                    + ", target=" + target.getWorld().getName()
                    + " (" + target.getBlockX() + "," + target.getBlockY() + "," + target.getBlockZ() + ")");
        }

        final BukkitTask[] taskRef = new BukkitTask[1];
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int count = 0;
            while (count < perTick && !queue.isEmpty()) {
                Player p = queue.poll();
                if (p != null && p.isOnline()) {
                    p.teleport(target);
                    if (afterTeleport != null) afterTeleport.accept(p);
                }
                count++;
            }
            if (queue.isEmpty()) {
                if (onDone != null) onDone.run();
                if (taskRef[0] != null) taskRef[0].cancel();
                if (plugin.getConfig().getBoolean("debug.logBatchOps", true)) {
                    Debug.log(plugin, "Batch teleport completed.");
                }
            }
        }, 0L, interval);
        taskRef[0] = task;
        return task;
    }

    public static BukkitTask teleportInBatches(Plugin plugin,
                                               Collection<? extends Player> players,
                                               Location target,
                                               int batchSize,
                                               int intervalTicks,
                                               Runnable onDone) {
        return teleportInBatches(plugin, players, target, batchSize, intervalTicks, null, onDone);
    }
}

