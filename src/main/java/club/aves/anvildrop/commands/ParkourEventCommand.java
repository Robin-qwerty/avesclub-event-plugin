package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.parkour.ParkourEventManager;
import club.aves.anvildrop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public final class ParkourEventCommand implements CommandExecutor {

    private final Plugin plugin;
    private final ParkourEventManager parkour;
    private final club.aves.anvildrop.event.AnvilDropEventManager anvil;
    private final club.aves.anvildrop.ffa.FFAEventManager ffa;

    public ParkourEventCommand(Plugin plugin, ParkourEventManager parkour, club.aves.anvildrop.event.AnvilDropEventManager anvil, club.aves.anvildrop.ffa.FFAEventManager ffa) {
        this.plugin = plugin;
        this.parkour = parkour;
        this.anvil = anvil;
        this.ffa = ffa;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!sender.hasPermission("event.admin")) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNoPerm));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Text.color(cfg.msgPrefix + "&eUsage: &f/parkourevent open|start|stop"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "open" -> {
                if ((anvil != null && anvil.isActive()) || (ffa != null && ffa.isActive())) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cYou can't open Parkour while AnvilDrop is active."));
                    return true;
                }
                boolean ok = parkour.open();
                sender.sendMessage(Text.color(cfg.msgPrefix + (ok
                        ? plugin.getConfig().getString("parkourMessages.opened", "&aParkour event opened!")
                        : "&cFailed to open parkour event. Check config worlds/spawns.")));
                return true;
            }
            case "start" -> {
                if ((anvil != null && anvil.isActive()) || (ffa != null && ffa.isActive())) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cYou can't start Parkour while AnvilDrop is active."));
                    return true;
                }
                Integer stopAt = parseStopAt(args);
                if (stopAt != null) {
                    int alive = parkour.getAliveCount();
                    if (alive <= stopAt) {
                        sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgStopAtTooHigh, java.util.Map.of("alive", String.valueOf(alive)))));
                        return true;
                    }
                }
                boolean ok = parkour.start(stopAt);
                sender.sendMessage(Text.color(cfg.msgPrefix + (ok
                        ? plugin.getConfig().getString("parkourMessages.started", "&aParkour started! Wall removed.")
                        : "&cParkour event is not open.")));
                return true;
            }
            case "stop" -> {
                parkour.stop();
                sender.sendMessage(Text.color(cfg.msgPrefix + plugin.getConfig().getString("parkourMessages.stopped", "&cParkour stopped! Wall placed.")));
                return true;
            }
            default -> {
                sender.sendMessage(Text.color(cfg.msgPrefix + "&cUnknown subcommand."));
                return true;
            }
        }
    }

    private Integer parseStopAt(String[] args) {
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
}


