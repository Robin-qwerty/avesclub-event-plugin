package club.aves.anvildrop.tablist;

import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public final class DeadTabListManager implements Listener {

    private final Plugin plugin;
    private final DeadPermissionService deadPerms;
    private BukkitTask task;

    private static final String TEAM_PREFIX = "tl_";

    public DeadTabListManager(Plugin plugin, DeadPermissionService deadPerms) {
        this.plugin = plugin;
        this.deadPerms = deadPerms;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("tabList.deadEnabled", true);
    }

    public void refreshAll() {
        if (!isEnabled()) return;
        String deadPrefix = Text.color(plugin.getConfig().getString("tabList.deadPrefix", "&c☠ &7"));
        Set<UUID> trackedDead = deadPerms.getTrackedDeadUuids(); // source of truth (avoids LP inheritance making everyone look dead)

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard sb = viewer.getScoreboard();
            if (sb == null) continue;

            ensureTeams(sb, deadPrefix);
            clearManagedTeams(sb);

            for (Player target : Bukkit.getOnlinePlayers()) {
                Rank r = rankOf(target);
                boolean dead = trackedDead.contains(target.getUniqueId());
                String teamName = teamName(r, dead);
                Team t = sb.getTeam(teamName);
                if (t != null) {
                    t.addEntry(target.getName());
                }
            }
        }
    }

    private void ensureTeams(Scoreboard sb, String deadPrefix) {
        for (Rank r : Rank.values()) {
            ensureTeam(sb, teamName(r, false), r.prefix);
            ensureTeam(sb, teamName(r, true), deadPrefix);
        }
    }

    private void ensureTeam(Scoreboard sb, String name, String prefix) {
        Team t = sb.getTeam(name);
        if (t == null) t = sb.registerNewTeam(name);
        t.setPrefix(prefix);
        t.setSuffix("");
        // Force name color (this is what makes it actually greyed out in tab).
        if (name.endsWith("_d")) {
            t.setColor(ChatColor.GRAY);
        } else {
            // Set to the rank color for alive entries
            for (Rank r : Rank.values()) {
                if (name.contains(r.order)) {
                    t.setColor(r.color);
                    break;
                }
            }
        }
    }

    private void clearManagedTeams(Scoreboard sb) {
        for (Team t : sb.getTeams()) {
            if (!t.getName().startsWith(TEAM_PREFIX)) continue;
            // avoid concurrent modification
            for (String entry : new ArrayList<>(t.getEntries())) {
                t.removeEntry(entry);
            }
        }
    }

    private static String teamName(Rank r, boolean dead) {
        return TEAM_PREFIX + r.order + (dead ? "_d" : "_a");
    }

    /**
     * Determine "highest" rank based on permissions commonly present with LuckPerms:
     * group.owner, group.dev, group.admin, group.mod, group.media, group.vip.
     */
    private static Rank rankOf(Player p) {
        if (p == null) return Rank.DEFAULT;
        if (p.hasPermission("group.owner")) return Rank.OWNER;
        if (p.hasPermission("group.dev")) return Rank.DEV;
        if (p.hasPermission("group.admin")) return Rank.ADMIN;
        if (p.hasPermission("group.mod")) return Rank.MOD;
        if (p.hasPermission("group.media")) return Rank.MEDIA;
        if (p.hasPermission("group.vip")) return Rank.VIP;
        return Rank.DEFAULT;
    }

    private enum Rank {
        OWNER("00_owner", ChatColor.GOLD),
        DEV("01_dev", ChatColor.DARK_PURPLE),
        ADMIN("02_admin", ChatColor.DARK_BLUE),
        MOD("03_mod", ChatColor.AQUA),
        MEDIA("04_media", ChatColor.LIGHT_PURPLE),
        VIP("05_vip", ChatColor.GREEN),
        DEFAULT("06_default", ChatColor.WHITE);

        final String order;
        final String prefix;
        final ChatColor color;

        Rank(String order, ChatColor color) {
            this.order = order;
            this.color = color;
            this.prefix = color.toString();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTask(plugin, this::refreshAll);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // no-op; entries get rebuilt each refresh
    }
}


