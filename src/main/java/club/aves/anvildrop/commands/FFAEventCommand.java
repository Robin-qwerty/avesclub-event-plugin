package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.ffa.FFAEventManager;
import club.aves.anvildrop.ffa.FFAKitManager;
import club.aves.anvildrop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FFAEventCommand implements CommandExecutor {

    private final Plugin plugin;
    private final FFAEventManager ffa;
    private final FFAKitManager kits;
    private final club.aves.anvildrop.event.AnvilDropEventManager anvil;
    private final club.aves.anvildrop.parkour.ParkourEventManager parkour;

    private static final Map<String, Long> kitOverwriteConfirmUntil = new HashMap<>();

    public FFAEventCommand(Plugin plugin, FFAEventManager ffa, FFAKitManager kits,
                           club.aves.anvildrop.event.AnvilDropEventManager anvil,
                           club.aves.anvildrop.parkour.ParkourEventManager parkour) {
        this.plugin = plugin;
        this.ffa = ffa;
        this.kits = kits;
        this.anvil = anvil;
        this.parkour = parkour;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!sender.hasPermission("event.admin")) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNoPerm));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Text.color(cfg.msgPrefix + "&eUsage: &f/ffaevent open|start <kit>|stop|kit <name> save|kitlist|givekit <kit>"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "open" -> {
                if ((anvil != null && anvil.isActive()) || (parkour != null && parkour.isActive())) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cYou can't open FFA while another event is active."));
                    return true;
                }
                boolean ok = ffa.open();
                sender.sendMessage(Text.color(cfg.msgPrefix + (ok
                        ? plugin.getConfig().getString("ffaMessages.opened", "&aFFA event opened!")
                        : "&cFailed to open FFA. Check config worlds/spawns.")));
                return true;
            }
            case "start" -> {
                if ((anvil != null && anvil.isActive()) || (parkour != null && parkour.isActive())) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cYou can't start FFA while another event is active."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cUsage: /ffaevent start <kitname>"));
                    return true;
                }
                String kit = args[1];
                boolean ok = ffa.start(kit);
                if (ok) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(
                            plugin.getConfig().getString("ffaMessages.started", "&aFFA started with kit &f{kit}&a!"),
                            Map.of("kit", kit)
                    )));
                } else {
                    sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(
                            plugin.getConfig().getString("ffaMessages.kitNotFound", "&cKit not found: &f{kit}"),
                            Map.of("kit", kit)
                    )));
                }
                return true;
            }
            case "stop" -> {
                ffa.stop();
                sender.sendMessage(Text.color(cfg.msgPrefix + plugin.getConfig().getString("ffaMessages.stopped", "&cFFA stopped.")));
                return true;
            }
            case "kit" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgPlayerOnly));
                    return true;
                }
                if (args.length < 3 || !args[2].equalsIgnoreCase("save")) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cUsage: /ffaevent kit <kitname> save"));
                    return true;
                }
                String kitName = args[1];

                String normalized = kits.normalizeName(kitName);
                if (normalized == null) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cInvalid kit name."));
                    return true;
                }

                boolean exists = kits.exists(normalized);
                String confirmKey = sender.getName().toLowerCase() + ":" + normalized;
                long now = System.currentTimeMillis();

                if (exists) {
                    Long until = kitOverwriteConfirmUntil.get(confirmKey);
                    if (until == null || until < now) {
                        kitOverwriteConfirmUntil.put(confirmKey, now + 5000L);
                        sender.sendMessage(Text.color(cfg.msgPrefix + "&eKit already exists. Run &f/ffaevent kit " + normalized + " save &eagain within 5 seconds to overwrite."));
                        return true;
                    }
                    kitOverwriteConfirmUntil.remove(confirmKey);
                }

                boolean ok = kits.saveFromPlayer(normalized, p, true);
                if (ok) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(
                            plugin.getConfig().getString("ffaMessages.kitSaved", "&aSaved kit &f{kit}&a."),
                            Map.of("kit", normalized)
                    )));
                } else {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cFailed to save kit."));
                }
                return true;
            }
            case "kitlist" -> {
                List<String> names = kits.listKitNames();
                if (names.isEmpty()) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&eNo kits saved yet."));
                    return true;
                }
                sender.sendMessage(Text.color(cfg.msgPrefix + "&eKits (&f" + names.size() + "&e): &f" + String.join("&7, &f", names)));
                return true;
            }
            case "givekit" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgPlayerOnly));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + "&cUsage: /ffaevent givekit <kitname>"));
                    return true;
                }
                var kit = kits.load(args[1]);
                if (kit == null) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(
                            plugin.getConfig().getString("ffaMessages.kitNotFound", "&cKit not found: &f{kit}"),
                            Map.of("kit", args[1])
                    )));
                    return true;
                }
                // Clear and apply
                var inv = p.getInventory();
                inv.clear();
                inv.setArmorContents(kit.armor().toArray(new org.bukkit.inventory.ItemStack[0]));
                inv.setContents(kit.contents().toArray(new org.bukkit.inventory.ItemStack[0]));
                inv.setItemInOffHand(kit.offhand());
                p.updateInventory();
                sender.sendMessage(Text.color(cfg.msgPrefix + "&aGiven kit &f" + kit.name() + "&a."));
                return true;
            }
            default -> {
                sender.sendMessage(Text.color(cfg.msgPrefix + "&cUnknown subcommand."));
                return true;
            }
        }
    }
}


