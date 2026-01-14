package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class EventHelpCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public EventHelpCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        if (!sender.hasPermission("event.admin")) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNoPerm));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgPlayerOnly));
            return true;
        }

        File file = new File(plugin.getDataFolder(), "eventhelp.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        String world = p.getWorld().getName();
        String worldPath = "worlds." + world;
        boolean includeDefault = y.getBoolean(worldPath + ".includeDefault", true);

        String prefix = cfg.msgPrefix;
        Map<String, String> placeholders = Map.of(
                "world", world,
                "prefix", prefix
        );

        // Header: prefer world header, fallback to default header
        String header = y.getString(worldPath + ".header", y.getString("default.header", "{prefix}&6Event Help"));
        if (header != null && !header.isBlank()) {
            p.sendMessage(Text.color(Text.replacePlaceholders(header, placeholders)));
        }

        if (includeDefault) {
            List<String> defLines = y.getStringList("default.lines");
            for (String line : defLines) {
                p.sendMessage(Text.color(Text.replacePlaceholders(line, placeholders)));
            }
        }

        List<String> worldLines = y.getStringList(worldPath + ".lines");
        if (worldLines != null && !worldLines.isEmpty()) {
            for (String line : worldLines) {
                p.sendMessage(Text.color(Text.replacePlaceholders(line, placeholders)));
            }
        } else if (!includeDefault) {
            p.sendMessage(Text.color(prefix + "&7No help configured for this world."));
        }

        return true;
    }
}


