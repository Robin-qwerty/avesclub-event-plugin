package club.aves.anvildrop.model;

import org.bukkit.Location;
import org.bukkit.World;

public record ArenaCuboid(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static ArenaCuboid normalized(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new ArenaCuboid(
                worldName,
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2)
        );
    }

    public boolean isInWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(worldName);
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equalsIgnoreCase(worldName)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}


