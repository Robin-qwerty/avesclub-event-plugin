package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public final class DiscordCommand implements CommandExecutor {

    private final Plugin plugin;

    public DiscordCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());
        String link = plugin.getConfig().getString("discord.link", "https://discord.gg/your-invite");
        String msg = Text.color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgDiscord, Map.of("link", link)));

        // You asked: "send a discord link in the chat for that player" (not broadcast)
        if (sender instanceof Player) {
            sender.sendMessage(msg);
        } else {
            sender.sendMessage(Text.color(cfg.msgPrefix + "&bDiscord: &f" + link));
        }
        return true;
    }
}


