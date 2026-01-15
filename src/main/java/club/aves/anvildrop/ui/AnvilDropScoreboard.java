package club.aves.anvildrop.ui;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnvilDropScoreboard implements Listener {

    private final Plugin plugin;
    private BukkitTask task;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    private final Map<String, Integer> aliveCountsByWorld = new HashMap<>();
    private final Map<String, Integer> timeSecondsByWorld = new HashMap<>();
    private final Map<String, Integer> finishedCountsByWorld = new HashMap<>();
    private final Map<String, Integer> stopAtByWorld = new HashMap<>();

    public AnvilDropScoreboard(Plugin plugin) {
        this.plugin = plugin;
    }

    public void setAliveCount(int aliveCount) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        setAliveCountForWorld(cfg.eventWorld, aliveCount);
    }

    public void setAliveCountForWorld(String worldName, int aliveCount) {
        if (worldName == null) return;
        aliveCountsByWorld.put(worldName.toLowerCase(), Math.max(0, aliveCount));
    }

    public void setTimeSecondsForWorld(String worldName, int seconds) {
        if (worldName == null) return;
        timeSecondsByWorld.put(worldName.toLowerCase(), Math.max(0, seconds));
    }

    public void setFinishedCountForWorld(String worldName, int finished) {
        if (worldName == null) return;
        finishedCountsByWorld.put(worldName.toLowerCase(), Math.max(0, finished));
    }

    public void setStopAtForWorld(String worldName, Integer stopAt) {
        if (worldName == null) return;
        String k = worldName.toLowerCase();
        if (stopAt == null) {
            stopAtByWorld.remove(k);
        } else {
            stopAtByWorld.put(k, Math.max(1, stopAt));
        }
    }

    public void startUpdater() {
        stopUpdater();
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            PluginConfig c = PluginConfig.load(plugin.getConfig());
            for (Player p : Bukkit.getOnlinePlayers()) {
                ensureBoard(p);
                updateBoard(p, c);
            }
        }, 1L, cfg.scoreboardUpdateTicks);
    }

    public void stopUpdater() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stopUpdater();
        boards.clear();
    }

    public void clearFor(Player p) {
        if (p == null) return;
        boards.remove(p.getUniqueId());
        // don't force reset to main scoreboard; just clear our objective if it's set
        // (servers with other scoreboard plugins may overwrite anyway)
    }

    private void ensureBoard(Player p) {
        boards.computeIfAbsent(p.getUniqueId(), (id) -> {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            p.setScoreboard(sb);
            return sb;
        });
    }

    private void updateBoard(Player p, PluginConfig cfg) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;

        String world = p.getWorld().getName();
        if (world.equalsIgnoreCase(cfg.eventWorld) && cfg.scoreboardEnabled) {
            List<String> raw = cfg.scoreboardLines;
            setSidebar(sb, "aves_event", Text.color(cfg.scoreboardTitle),
                    replaceAll(raw, Map.of(
                            "alive", String.valueOf(aliveCountsByWorld.getOrDefault(cfg.eventWorld.toLowerCase(), 0)),
                            "time", formatTime(timeSecondsByWorld.getOrDefault(cfg.eventWorld.toLowerCase(), 0)),
                            "stopat", stopAtString(cfg.eventWorld),
                            "/stopat", slashStopAtString(cfg.eventWorld)
                    )));
            return;
        }

        if (world.equalsIgnoreCase(cfg.parkourWorld) && cfg.parkourScoreboardEnabled) {
            setSidebar(sb, "aves_parkour", Text.color(cfg.parkourScoreboardTitle),
                    replaceAll(cfg.parkourScoreboardLines, Map.of(
                            "alive", String.valueOf(aliveCountsByWorld.getOrDefault(cfg.parkourWorld.toLowerCase(), 0)),
                            "finished", String.valueOf(finishedCountsByWorld.getOrDefault(cfg.parkourWorld.toLowerCase(), 0)),
                            "time", formatTime(timeSecondsByWorld.getOrDefault(cfg.parkourWorld.toLowerCase(), 0)),
                            "stopat", stopAtString(cfg.parkourWorld),
                            "/stopat", slashStopAtString(cfg.parkourWorld)
                    )));
            return;
        }

        if (world.equalsIgnoreCase(cfg.ffaWorld) && cfg.ffaScoreboardEnabled) {
            setSidebar(sb, "aves_ffa", Text.color(cfg.ffaScoreboardTitle),
                    replaceAll(cfg.ffaScoreboardLines, Map.of(
                            "alive", String.valueOf(aliveCountsByWorld.getOrDefault(cfg.ffaWorld.toLowerCase(), 0)),
                            "time", formatTime(timeSecondsByWorld.getOrDefault(cfg.ffaWorld.toLowerCase(), 0)),
                            "stopat", stopAtString(cfg.ffaWorld),
                            "/stopat", slashStopAtString(cfg.ffaWorld)
                    )));
            return;
        }

        if (world.equalsIgnoreCase(cfg.lobbyWorld) && cfg.lobbyScoreboardEnabled) {
            setSidebar(sb, "aves_lobby", Text.color(cfg.lobbyScoreboardTitle),
                    replaceAll(cfg.lobbyScoreboardLines, Map.of(
                            "online", String.valueOf(Bukkit.getOnlinePlayers().size()),
                            "host", cfg.lobbyHost
                    )));
            return;
        }

        // Other worlds: hide our sidebar
        Objective cur = sb.getObjective(DisplaySlot.SIDEBAR);
        if (cur != null && (cur.getName().equals("aves_event") || cur.getName().equals("aves_lobby") || cur.getName().equals("aves_parkour") || cur.getName().equals("aves_ffa"))) {
            cur.unregister();
        }
    }

    private static List<String> replaceAll(List<String> lines, Map<String, String> placeholders) {
        return lines.stream()
                .map(s -> Text.color(Text.replacePlaceholders(s, placeholders)))
                .toList();
    }

    private static String formatTime(int seconds) {
        int s = Math.max(0, seconds);
        int m = s / 60;
        int r = s % 60;
        return String.format("%02d:%02d", m, r);
    }

    private String stopAtString(String worldName) {
        if (worldName == null) return "";
        Integer v = stopAtByWorld.get(worldName.toLowerCase());
        return v == null ? "" : String.valueOf(v);
    }

    private String slashStopAtString(String worldName) {
        if (worldName == null) return "";
        Integer v = stopAtByWorld.get(worldName.toLowerCase());
        return v == null ? "" : ("/" + v);
    }

    /**
     * Bedrock/Geyser clients tend to behave much better when entries stay stable.
     * We use Teams + fixed "invisible" entries, and only update prefix/suffix.
     */
    private void setSidebar(Scoreboard sb, String objectiveName, String title, List<String> lines) {
        Objective obj = sb.getObjective(objectiveName);
        if (obj == null) {
            // create fresh objective for this mode
            Objective existing = sb.getObjective(DisplaySlot.SIDEBAR);
            if (existing != null) existing.unregister();
            obj = sb.registerNewObjective(objectiveName, "dummy", title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.setDisplayName(title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        int max = Math.min(15, lines.size());
        // Scores count down from 15 -> 1 (top to bottom)
        for (int i = 0; i < max; i++) {
            int score = max - i;
            String entry = uniqueEntry(i);
            Team team = sb.getTeam("ln" + i);
            if (team == null) team = sb.registerNewTeam("ln" + i);
            if (!team.hasEntry(entry)) team.addEntry(entry);

            var parts = splitForTeam(lines.get(i));
            team.setPrefix(parts.prefix);
            team.setSuffix(parts.suffix);
            obj.getScore(entry).setScore(score);
        }

        // Cleanup extra lines from previous renders
        for (int i = max; i < 15; i++) {
            String entry = uniqueEntry(i);
            sb.resetScores(entry);
            Team t = sb.getTeam("ln" + i);
            if (t != null) {
                t.removeEntry(entry);
                // keep the team object around; it's fine (avoids churn)
                t.setPrefix("");
                t.setSuffix("");
            }
        }
    }

    private static String uniqueEntry(int i) {
        // Unique (mostly invisible) entries. 0..14 fits ChatColor values safely.
        ChatColor[] colors = ChatColor.values();
        return (i >= 0 && i < colors.length ? colors[i].toString() : (ChatColor.RESET + "" + i));
    }

    private record TeamParts(String prefix, String suffix) {}

    private static TeamParts splitForTeam(String line) {
        if (line == null) return new TeamParts("", "");
        if (line.length() <= 16) return new TeamParts(line, "");
        String prefix = line.substring(0, 16);
        String rest = line.substring(16);
        String last = ChatColor.getLastColors(prefix);
        String suffix = (last + rest);
        if (suffix.length() > 16) suffix = suffix.substring(0, 16);
        return new TeamParts(prefix, suffix);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        Player p = e.getPlayer();
        ensureBoard(p);
        updateBoard(p, cfg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clearFor(e.getPlayer());
    }
}


