package club.aves.anvildrop.ui;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.event.AnvilDropEventManager;
import club.aves.anvildrop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EventSettingsUI implements Listener {

    private final Plugin plugin;
    private final AnvilDropEventManager eventManager;
    private final DeadPermissionService deadPerms;
    private final Set<UUID> hidePlayersEnabled = new HashSet<>();

    public EventSettingsUI(Plugin plugin, AnvilDropEventManager eventManager, DeadPermissionService deadPerms) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.deadPerms = deadPerms;
    }

    public void initForOnlinePlayers() {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isInAnyEventWorld(p, cfg)) {
                ensureCompass(p);
                // if the viewer already has hide enabled, re-apply hides after reloads
                if (hidePlayersEnabled.contains(p.getUniqueId())) {
                    setHidePlayers(p, true);
                }
            }
        }
    }

    public void resetAll() {
        for (UUID id : Set.copyOf(hidePlayersEnabled)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                setHidePlayers(p, false);
            } else {
                hidePlayersEnabled.remove(id);
            }
        }
    }

    private boolean isInEventWorld(Player p, PluginConfig cfg) {
        return p != null && p.getWorld() != null && p.getWorld().getName().equalsIgnoreCase(cfg.eventWorld);
    }

    private boolean isInParkourWorld(Player p, PluginConfig cfg) {
        return p != null && p.getWorld() != null && p.getWorld().getName().equalsIgnoreCase(cfg.parkourWorld);
    }

    private boolean isInAnyEventWorld(Player p, PluginConfig cfg) {
        return isInEventWorld(p, cfg) || isInParkourWorld(p, cfg);
    }

    private int compassSlot0Based(PluginConfig cfg) {
        int slot1to9 = plugin.getConfig().getInt("eventSettings.compass.slot", 9);
        slot1to9 = Math.max(1, Math.min(9, slot1to9));
        return slot1to9 - 1;
    }

    private int parkourCompassSlot0Based(PluginConfig cfg) {
        int slot1to9 = plugin.getConfig().getInt("parkourSettings.compass.slot", 9);
        slot1to9 = Math.max(1, Math.min(9, slot1to9));
        return slot1to9 - 1;
    }

    public void ensureCompass(Player p) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (isInEventWorld(p, cfg)) {
            if (deadPerms.isDead(p)) {
                removeCompass(p);
                return;
            }
            if (!plugin.getConfig().getBoolean("eventSettings.compass.enabled", true)) return;
            ItemStack compass = buildCompass("eventSettings", "event_settings_compass");
            int slot = compassSlot0Based(cfg);
            p.getInventory().setItem(slot, compass);
            return;
        }

        if (isInParkourWorld(p, cfg)) {
            // In parkour, spectators should never get the compass
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                removeCompass(p);
                return;
            }
            if (!plugin.getConfig().getBoolean("parkourSettings.compass.enabled", true)) return;
            ItemStack compass = buildCompass("parkourSettings", "parkour_settings_compass");
            int slot = parkourCompassSlot0Based(cfg);
            p.getInventory().setItem(slot, compass);
        }
    }

    public void removeCompass(Player p) {
        if (p == null) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        // remove from both possible slots/types
        int slotA = compassSlot0Based(cfg);
        int slotB = parkourCompassSlot0Based(cfg);
        ItemStack curA = p.getInventory().getItem(slotA);
        ItemStack curB = p.getInventory().getItem(slotB);
        if (isAnySettingsCompass(curA)) p.getInventory().setItem(slotA, null);
        if (isAnySettingsCompass(curB)) p.getInventory().setItem(slotB, null);
    }

    private ItemStack buildCompass(String basePath, String keyName) {
        String name = plugin.getConfig().getString(basePath + ".compass.name", "&6Event Settings");
        List<String> lore = plugin.getConfig().getStringList(basePath + ".compass.lore");
        if (lore == null || lore.isEmpty()) lore = List.of("&7Right-click to open");

        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, keyName), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isSettingsCompass(ItemStack item, String keyName) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, keyName), PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public boolean isAnySettingsCompass(ItemStack item) {
        return isSettingsCompass(item, "event_settings_compass") || isSettingsCompass(item, "parkour_settings_compass");
    }

    private Inventory buildMenu(Player p) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        String base = isInParkourWorld(p, cfg) ? "parkourSettings" : "eventSettings";
        String title = plugin.getConfig().getString(base + ".menu.title", "&6Event Settings");
        Inventory inv = Bukkit.createInventory(p, 9, Text.color(title));
        inv.setItem(4, buildToggleItem(p, base));
        return inv;
    }

    private ItemStack buildToggleItem(Player p, String base) {
        boolean enabled = hidePlayersEnabled.contains(p.getUniqueId());
        String name = plugin.getConfig().getString(base + ".menu.toggleHidePlayersName", "&eToggle: Hide players");
        List<String> lore = enabled
                ? plugin.getConfig().getStringList(base + ".menu.enabledLore")
                : plugin.getConfig().getStringList(base + ".menu.disabledLore");
        if (lore == null || lore.isEmpty()) {
            lore = enabled ? List.of("&aEnabled", "&7Click to disable") : List.of("&cDisabled", "&7Click to enable");
        }

        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(name));
            meta.setLore(lore.stream().map(Text::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setHidePlayers(Player viewer, boolean hide) {
        if (viewer == null) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!isInAnyEventWorld(viewer, cfg)) {
            hidePlayersEnabled.remove(viewer.getUniqueId());
            return;
        }

        if (hide) {
            hidePlayersEnabled.add(viewer.getUniqueId());
            for (Player other : viewer.getWorld().getPlayers()) {
                if (other.equals(viewer)) continue;
                viewer.hidePlayer(plugin, other);
            }
        } else {
            hidePlayersEnabled.remove(viewer.getUniqueId());
            for (Player other : viewer.getWorld().getPlayers()) {
                if (other.equals(viewer)) continue;
                viewer.showPlayer(plugin, other);
            }
        }
    }

    /**
     * Force-disable "hide other players" for this viewer (so they can see everyone again),
     * even if they no longer have access to the settings GUI (e.g. when becoming spectator).
     */
    public void forceShowPlayers(Player viewer) {
        setHidePlayers(viewer, false);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!isInAnyEventWorld(p, cfg)) return;
        if (isInEventWorld(p, cfg) && deadPerms.isDead(p)) {
            removeCompass(p);
            return;
        }
        if (isInParkourWorld(p, cfg) && p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            removeCompass(p);
            return;
        }

        ItemStack inHand = e.getItem();
        if (!isAnySettingsCompass(inHand)) return;

        e.setCancelled(true);
        ensureCompass(p);
        p.openInventory(buildMenu(p));
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        String titleA = Text.color(plugin.getConfig().getString("eventSettings.menu.title", "&6Event Settings"));
        String titleB = Text.color(plugin.getConfig().getString("parkourSettings.menu.title", "&aParkour Settings"));
        String viewTitle = e.getView().getTitle();
        if (!titleA.equals(viewTitle) && !titleB.equals(viewTitle)) return;

        e.setCancelled(true);
        if (e.getRawSlot() != 4) return;

        if (!isInAnyEventWorld(p, cfg)) {
            p.closeInventory();
            return;
        }

        boolean now = !hidePlayersEnabled.contains(p.getUniqueId());
        setHidePlayers(p, now);
        String base = isInParkourWorld(p, cfg) ? "parkourSettings" : "eventSettings";
        e.getInventory().setItem(4, buildToggleItem(p, base));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (isInAnyEventWorld(p, cfg)) {
            if (eventManager.shouldForceSpectator(p)) {
                p.setGameMode(org.bukkit.GameMode.SPECTATOR);
            }
            // entering event world
            ensureCompass(p);
            // If other viewers have hide enabled, hide this joining player for them.
            for (UUID viewerId : Set.copyOf(hidePlayersEnabled)) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer == null) {
                    hidePlayersEnabled.remove(viewerId);
                    continue;
                }
                if (!isInAnyEventWorld(viewer, cfg)) continue;
                // Only hide if they are in the same world (event vs parkour separation)
                if (!viewer.getWorld().equals(p.getWorld())) continue;
                if (viewer.equals(p)) continue;
                viewer.hidePlayer(plugin, p);
            }
        } else {
            // leaving event world
            setHidePlayers(p, false);
            removeCompass(p);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        // Reset their setting when they die (your requirement)
        setHidePlayers(p, false);
        // Prevent the settings compass from dropping
        e.getDrops().removeIf(this::isAnySettingsCompass);
        removeCompass(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!isInEventWorld(p, cfg)) return;
        // Requirement: players who become dead during the event should NOT be forced spectator.
        // Only players entering already-dead are forced spectator (handled on world change / open teleport).
        if (deadPerms.isDead(p)) {
            removeCompass(p);
        } else {
            ensureCompass(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hidePlayersEnabled.remove(e.getPlayer().getUniqueId());
    }
}


