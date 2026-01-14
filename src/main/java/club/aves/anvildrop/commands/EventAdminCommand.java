package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.mods.ModRegistry;
import club.aves.anvildrop.util.Text;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EventAdminCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final ModRegistry mods;

    public EventAdminCommand(Plugin plugin, ModRegistry mods) {
        this.plugin = plugin;
        this.mods = mods;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());

        if (!sender.hasPermission("event.admin")) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNoPerm));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Text.color(cfg.msgPrefix + "&eUsage: &f/event addmod <player|uuid>&e, &f/event removemod <player|uuid>&e, &f/event listmods"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "addmod" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cUsage: /event addmod <player|uuid>"));
                    return true;
                }
                OfflinePlayer target = mods.resolvePlayer(args[1]);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cPlayer not found."));
                    return true;
                }
                boolean changed = mods.addMod(target);
                sender.sendMessage(Text.color(cfg.msgPrefix + (changed
                        ? "&aAdded mod: &f" + safeName(target)
                        : "&eAlready a mod: &f" + safeName(target))));
                return true;
            }
            case "removemod" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cUsage: /event removemod <player|uuid>"));
                    return true;
                }
                OfflinePlayer target = mods.resolvePlayer(args[1]);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cPlayer not found."));
                    return true;
                }
                boolean changed = mods.removeMod(target);
                sender.sendMessage(Text.color(cfg.msgPrefix + (changed
                        ? "&aRemoved mod: &f" + safeName(target)
                        : "&eNot a mod: &f" + safeName(target))));
                return true;
            }
            case "listmods" -> {
                var ids = mods.snapshot();
                sender.sendMessage(Text.color(cfg.msgPrefix + "&eMods (" + ids.size() + "):"));
                for (var id : ids) {
                    OfflinePlayer op = plugin.getServer().getOfflinePlayer(id);
                    sender.sendMessage(Text.color("&7- &f" + safeName(op) + " &7(" + id + ")"));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Text.color(cfg.msgPrefix + "&cUnknown subcommand."));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("addmod", "removemod", "listmods");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("addmod") || args[0].equalsIgnoreCase("removemod"))) {
            List<String> out = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                out.add(p.getName());
            }
            return out;
        }
        return List.of();
    }

    private static String safeName(OfflinePlayer p) {
        if (p == null) return "unknown";
        String n = p.getName();
        return (n == null || n.isBlank()) ? "unknown" : n;
    }
}


