package club.aves.anvildrop.mods;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ModRegistry {

    private final Plugin plugin;
    private final Set<UUID> mods = new HashSet<>();

    public ModRegistry(Plugin plugin) {
        this.plugin = plugin;
        reloadFromConfig();
    }

    public void reloadFromConfig() {
        mods.clear();
        FileConfiguration c = plugin.getConfig();
        List<String> list = c.getStringList("mods.uuids");
        if (list == null) return;
        for (String s : list) {
            try {
                mods.add(UUID.fromString(s));
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isMod(UUID uuid) {
        return uuid != null && mods.contains(uuid);
    }

    public Set<UUID> snapshot() {
        return Set.copyOf(mods);
    }

    public boolean addMod(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) return false;
        boolean changed = mods.add(player.getUniqueId());
        if (changed) saveToConfig();
        return changed;
    }

    public boolean removeMod(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) return false;
        boolean changed = mods.remove(player.getUniqueId());
        if (changed) saveToConfig();
        return changed;
    }

    private void saveToConfig() {
        plugin.getConfig().set("mods.uuids", mods.stream().map(UUID::toString).toList());
        if (plugin instanceof org.bukkit.plugin.java.JavaPlugin jp) {
            jp.saveConfig();
        }
    }

    public OfflinePlayer resolvePlayer(String nameOrUuid) {
        if (nameOrUuid == null || nameOrUuid.isBlank()) return null;
        try {
            UUID id = UUID.fromString(nameOrUuid);
            return Bukkit.getOfflinePlayer(id);
        } catch (Exception ignored) {
            return Bukkit.getOfflinePlayer(nameOrUuid);
        }
    }
}


