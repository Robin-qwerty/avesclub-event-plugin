package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.spleef.SpleefEventManager;
import club.aves.anvildrop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public final class SpleefEventCommand implements CommandExecutor {

    private final Plugin plugin;
    private final SpleefEventManager spleef;
    private final club.aves.anvildrop.event.AnvilDropEventManager anvil;
    private final club.aves.anvildrop.parkour.ParkourEventManager parkour;
    private final club.aves.anvildrop.ffa.FFAEventManager ffa;

    public SpleefEventCommand(Plugin plugin,
                              SpleefEventManager spleef,
                              club.aves.anvildrop.event.AnvilDropEventManager anvil,
                              club.aves.anvildrop.parkour.ParkourEventManager parkour,
                              club.aves.anvildrop.ffa.FFAEventManager ffa) {
        this.plugin = plugin;
        this.spleef = spleef;
        this.anvil = anvil;
        this.parkour = parkour;
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
            sender.sendMessage(Text.color(cfg.msgPrefix + "&eUsage: &f/spleefevent open|start|stop"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "open" -> {
                if ((anvil != null && anvil.isActive()) || (parkour != null && parkour.isActive()) || (ffa != null && ffa.isActive())) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cYou can't open Spleef while another event is active."));
                    return true;
                }
                boolean ok = spleef.open();
                sender.sendMessage(Text.color(cfg.msgPrefix + (ok
                        ? plugin.getConfig().getString("spleefMessages.opened", "&aSpleef event opened!")
                        : "&cFailed to open Spleef. Check config worlds/spawns.")));
                return true;
            }
            case "start" -> {
                if ((anvil != null && anvil.isActive()) || (parkour != null && parkour.isActive()) || (ffa != null && ffa.isActive())) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cYou can't start Spleef while another event is active."));
                    return true;
                }
                Integer stopAt = parseStopAt(args);
                if (stopAt != null) {
                    int alive = spleef.getAliveCount();
                    if (alive <= stopAt) {
                        sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgStopAtTooHigh, java.util.Map.of("alive", String.valueOf(alive)))));
                        return true;
                    }
                }
                boolean ok = spleef.start(stopAt);
                sender.sendMessage(Text.color(cfg.msgPrefix + (ok
                        ? plugin.getConfig().getString("spleefMessages.started", "&aSpleef started!")
                        : "&cFailed to start Spleef. Check config worlds/spawns.")));
                return true;
            }
            case "stop" -> {
                spleef.stop();
                sender.sendMessage(Text.color(cfg.msgPrefix + plugin.getConfig().getString("spleefMessages.stopped", "&cSpleef stopped.")));
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


