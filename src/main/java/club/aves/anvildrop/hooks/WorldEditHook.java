package club.aves.anvildrop.hooks;

import club.aves.anvildrop.model.ArenaCuboid;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class WorldEditHook {

    private final Plugin plugin;

    public WorldEditHook(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    }

    /**
     * Reads the player's current WorldEdit selection (cuboid / poly regions will be converted to min/max bounds).
     */
    public ArenaCuboid getPlayerSelection(Player player) {
        if (!isAvailable() || player == null) return null;

        try {
            // Reflection-based access so this plugin still compiles even if WorldEdit isn't on the build classpath.
            Class<?> weClass = Class.forName("com.sk89q.worldedit.WorldEdit");
            Object we = weClass.getMethod("getInstance").invoke(null);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object wePlayer = bukkitAdapterClass.getMethod("adapt", Player.class).invoke(null, player);
            Object weWorld = bukkitAdapterClass.getMethod("adapt", org.bukkit.World.class).invoke(null, player.getWorld());

            Object sessionManager = weClass.getMethod("getSessionManager").invoke(we);
            Object localSession = sessionManager.getClass().getMethod("get", wePlayer.getClass()).invoke(sessionManager, wePlayer);

            Object region = localSession.getClass().getMethod("getSelection", weWorld.getClass()).invoke(localSession, weWorld);

            Object min = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object max = region.getClass().getMethod("getMaximumPoint").invoke(region);

            return ArenaCuboid.normalized(
                    player.getWorld().getName(),
                    vecX(min), vecY(min), vecZ(min),
                    vecX(max), vecY(max), vecZ(max)
            );
        } catch (Throwable t) {
            // Handle incomplete selection and other WE errors by returning null.
            if (t.getClass().getName().endsWith("IncompleteRegionException")) return null;
            plugin.getLogger().warning("Failed to read WorldEdit selection: " + t.getMessage());
            return null;
        }
    }

    private static int vecX(Object blockVector3) throws ReflectiveOperationException {
        return (int) callInt(blockVector3, "getX", "getBlockX", "x");
    }

    private static int vecY(Object blockVector3) throws ReflectiveOperationException {
        return (int) callInt(blockVector3, "getY", "getBlockY", "y");
    }

    private static int vecZ(Object blockVector3) throws ReflectiveOperationException {
        return (int) callInt(blockVector3, "getZ", "getBlockZ", "z");
    }

    private static Object callInt(Object target, String... methodNames) throws ReflectiveOperationException {
        for (String m : methodNames) {
            try {
                return target.getClass().getMethod(m).invoke(target);
            } catch (NoSuchMethodException ignored) {
                // try next
            }
        }
        throw new NoSuchMethodException("No vector accessor found on " + target.getClass().getName());
    }
}


