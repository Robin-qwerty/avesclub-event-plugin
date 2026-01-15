package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.event.AnvilDropEventManager;
import club.aves.anvildrop.hooks.WorldEditHook;
import club.aves.anvildrop.model.ArenaCuboid;
import club.aves.anvildrop.util.Text;
import club.aves.anvildrop.ffa.FFAEventManager;
import club.aves.anvildrop.parkour.ParkourEventManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AnvilDropCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final AnvilDropEventManager manager;
    private final WorldEditHook worldEdit;
    private final ParkourEventManager parkour;
    private final FFAEventManager ffa;

    public AnvilDropCommand(Plugin plugin, AnvilDropEventManager manager, WorldEditHook worldEdit, ParkourEventManager parkour, FFAEventManager ffa) {
        this.plugin = plugin;
        this.manager = manager;
        this.worldEdit = worldEdit;
        this.parkour = parkour;
        this.ffa = ffa;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());

        if (args.length == 0) {
            sender.sendMessage(color(cfg.msgPrefix + "&eUsage: &f/anvildrop open|start|p|r|stop|setarena|reload|<percent>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (isPercent(sub)) {
            if (!sender.hasPermission("anvildrop.control")) {
                sender.sendMessage(color(cfg.msgPrefix + cfg.msgNoPerm));
                return true;
            }
            double percent = Double.parseDouble(sub);
            boolean ok = manager.dropNow(percent);
            if (ok) {
                sender.sendMessage(color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgPercentNow, Map.of("percent", String.valueOf(percent)))));
            } else {
                sender.sendMessage(color(cfg.msgPrefix + cfg.msgArenaNotSet));
            }
            return true;
        }

        switch (sub) {
            case "open" -> {
                if (!sender.hasPermission("anvildrop.open")) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgNoPerm));
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgPlayerOnly));
                    return true;
                }
                if ((parkour != null && parkour.isActive()) || (ffa != null && ffa.isActive())) {
                    sender.sendMessage(color(cfg.msgPrefix + "&cYou can't open AnvilDrop while Parkour is active."));
                    return true;
                }

                if (Bukkit.getWorld(cfg.lobbyWorld) == null) {
                    sender.sendMessage(color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgWorldMissing, Map.of("world", cfg.lobbyWorld))));
                    return true;
                }
                if (Bukkit.getWorld(cfg.eventWorld) == null) {
                    sender.sendMessage(color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgWorldMissing, Map.of("world", cfg.eventWorld))));
                    return true;
                }
                if (cfg.eventSpawn == null) {
                    sender.sendMessage(color(cfg.msgPrefix + "&cEvent spawn is invalid (world missing). Check config."));
                    return true;
                }

                if (manager.openEvent(p)) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgOpened));
                } else {
                    sender.sendMessage(color(cfg.msgPrefix + "&cFailed to open event. Check config worlds/spawns."));
                }
                return true;
            }
            case "start" -> {
                if (!sender.hasPermission("anvildrop.start")) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgNoPerm));
                    return true;
                }
                if ((parkour != null && parkour.isActive()) || (ffa != null && ffa.isActive())) {
                    sender.sendMessage(color(cfg.msgPrefix + "&cYou can't start AnvilDrop while Parkour is active."));
                    return true;
                }
                Integer stopAt = parseStopAt(args);
                if (stopAt != null) {
                    int alive = manager.getAliveCount();
                    if (alive <= stopAt) {
                        sender.sendMessage(color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgStopAtTooHigh, Map.of("alive", String.valueOf(alive)))));
                        return true;
                    }
                }
                if (manager.startEvent(stopAt)) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgStarted));
                } else {
                    sender.sendMessage(color(cfg.msgPrefix + "&cEvent is not open (or worlds missing)."));
                }
                return true;
            }
            case "p", "pause" -> {
                if (!sender.hasPermission("anvildrop.control")) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgNoPerm));
                    return true;
                }
                if (manager.pause()) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgPaused));
                } else {
                    sender.sendMessage(color(cfg.msgPrefix + "&cEvent is not running."));
                }
                return true;
            }
            case "r", "resume" -> {
                if (!sender.hasPermission("anvildrop.control")) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgNoPerm));
                    return true;
                }
                if (manager.resume()) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgResumed));
                } else {
                    sender.sendMessage(color(cfg.msgPrefix + "&cEvent is not paused."));
                }
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("anvildrop.control")) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgNoPerm));
                    return true;
                }
                manager.stopEvent();
                sender.sendMessage(color(cfg.msgPrefix + cfg.msgStopped));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("anvildrop.reload")) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgNoPerm));
                    return true;
                }
                manager.reload();
                sender.sendMessage(color(cfg.msgPrefix + PluginConfig.load(plugin.getConfig()).msgReloaded));
                return true;
            }
            case "setarena" -> {
                if (!sender.hasPermission("anvildrop.control")) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgNoPerm));
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(color(cfg.msgPrefix + cfg.msgPlayerOnly));
                    return true;
                }
                if (!worldEdit.isAvailable()) {
                    sender.sendMessage(color(cfg.msgPrefix + "&cWorldEdit is not installed."));
                    return true;
                }
                ArenaCuboid sel = worldEdit.getPlayerSelection(p);
                if (sel == null) {
                    sender.sendMessage(color(cfg.msgPrefix + "&cNo WorldEdit selection. Select pos1/pos2 first."));
                    return true;
                }
                // Save bounds to config (and keep eventWorld as the selection world if different)
                plugin.getConfig().set("worlds.eventWorld", sel.worldName());
                plugin.getConfig().set("arena.min.x", sel.minX());
                plugin.getConfig().set("arena.min.y", sel.minY());
                plugin.getConfig().set("arena.min.z", sel.minZ());
                plugin.getConfig().set("arena.max.x", sel.maxX());
                plugin.getConfig().set("arena.max.y", sel.maxY());
                plugin.getConfig().set("arena.max.z", sel.maxZ());
                plugin.saveConfig();

                sender.sendMessage(color(cfg.msgPrefix + "&aArena saved: &f" + sel.worldName()
                        + " &7(" + sel.minX() + "," + sel.minY() + "," + sel.minZ() + ") -> ("
                        + sel.maxX() + "," + sel.maxY() + "," + sel.maxZ() + ")"));
                return true;
            }
            default -> {
                sender.sendMessage(color(cfg.msgPrefix + "&cUnknown subcommand."));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            out.add("open");
            out.add("start");
            out.add("p");
            out.add("r");
            out.add("stop");
            out.add("setarena");
            out.add("reload");
            out.add("10");
            out.add("25");
            out.add("50");
            out.add("75");
            out.add("90");
            return out;
        }
        return List.of();
    }

    private static boolean isPercent(String s) {
        try {
            double d = Double.parseDouble(s);
            return d >= 0.0 && d <= 100.0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Integer parseStopAt(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase("-stopat")) {
                try {
                    int v = Integer.parseInt(args[i + 1]);
                    return Math.max(1, v);
                } catch (Exception ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String color(String s) {
        return Text.color(s);
    }
}


