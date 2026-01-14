package club.aves.anvildrop.dead;

import club.aves.anvildrop.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DeadPermissionService {

    private final Plugin plugin;
    private final Map<UUID, PermissionAttachment> fallbackAttachments = new HashMap<>();

    public DeadPermissionService(Plugin plugin) {
        this.plugin = plugin;
    }

    private String perm() {
        return PluginConfig.load(plugin.getConfig()).deadPermission;
    }

    private boolean hasLuckPerms() {
        return Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    public boolean isDead(Player player) {
        if (player == null) return false;
        return player.hasPermission(perm());
    }

    public void markDead(Player player) {
        if (player == null) return;
        String permission = perm();

        addDeadUuid(player.getUniqueId());

        if (hasLuckPerms()) {
            // Use console commands to avoid a hard dependency on LuckPerms API.
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getUniqueId() + " permission set " + permission + " true");
            return;
        }

        // Fallback: grant via attachment (non-persistent, but keeps plugin functional without LP)
        fallbackAttachments.computeIfAbsent(player.getUniqueId(), id -> player.addAttachment(plugin))
                .setPermission(permission, true);
    }

    public void clearDead(Player player) {
        if (player == null) return;
        String permission = perm();

        removeDeadUuid(player.getUniqueId());

        if (hasLuckPerms()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getUniqueId() + " permission unset " + permission);
            return;
        }

        PermissionAttachment at = fallbackAttachments.remove(player.getUniqueId());
        if (at != null) {
            try {
                at.unsetPermission(permission);
            } catch (Throwable ignored) {
            }
        }
    }

    public Set<UUID> getTrackedDeadUuids() {
        List<String> list = plugin.getConfig().getStringList("deadTracking.uuids");
        if (list == null || list.isEmpty()) return Set.of();
        Set<UUID> out = new HashSet<>();
        for (String s : list) {
            try {
                out.add(UUID.fromString(s));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public void clearDeadUuid(UUID uuid) {
        if (uuid == null) return;
        removeDeadUuid(uuid);
        if (hasLuckPerms()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + uuid + " permission unset " + perm());
        }
    }

    private void addDeadUuid(UUID uuid) {
        if (uuid == null) return;
        List<String> list = plugin.getConfig().getStringList("deadTracking.uuids");
        Set<String> set = new HashSet<>(list == null ? List.of() : list);
        if (set.add(uuid.toString())) {
            plugin.getConfig().set("deadTracking.uuids", set.stream().toList());
            if (plugin instanceof org.bukkit.plugin.java.JavaPlugin jp) jp.saveConfig();
        }
    }

    private void removeDeadUuid(UUID uuid) {
        if (uuid == null) return;
        List<String> list = plugin.getConfig().getStringList("deadTracking.uuids");
        if (list == null || list.isEmpty()) return;
        Set<String> set = new HashSet<>(list);
        if (set.remove(uuid.toString())) {
            plugin.getConfig().set("deadTracking.uuids", set.stream().toList());
            if (plugin instanceof org.bukkit.plugin.java.JavaPlugin jp) jp.saveConfig();
        }
    }
}


