package club.aves.anvildrop.commands;

import club.aves.anvildrop.config.PluginConfig;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.event.AnvilDropEventManager;
import club.aves.anvildrop.ffa.FFAEventManager;
import club.aves.anvildrop.mods.ModRegistry;
import club.aves.anvildrop.parkour.ParkourEventManager;
import club.aves.anvildrop.spleef.SpleefEventManager;
import club.aves.anvildrop.ui.EventSettingsUI;
import club.aves.anvildrop.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public final class ReviveCommand implements CommandExecutor {

    private final Plugin plugin;
    private final AnvilDropEventManager eventManager;
    private final DeadPermissionService deadPerms;
    private final EventSettingsUI settingsUI;
    private final ModRegistry mods;
    private final ParkourEventManager parkour;
    private final FFAEventManager ffa;
    private final SpleefEventManager spleef;

    private static final Map<String, Long> reviveAllConfirmUntil = new HashMap<>();

    public ReviveCommand(Plugin plugin,
                        AnvilDropEventManager eventManager,
                        DeadPermissionService deadPerms,
                        EventSettingsUI settingsUI,
                        ModRegistry mods,
                        ParkourEventManager parkour,
                        FFAEventManager ffa,
                        SpleefEventManager spleef) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.deadPerms = deadPerms;
        this.settingsUI = settingsUI;
        this.mods = mods;
        this.parkour = parkour;
        this.ffa = ffa;
        this.spleef = spleef;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginConfig cfg = PluginConfig.load(plugin.getConfig());

        if (!sender.hasPermission("event.revive")) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgNoPerm));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveUsage));
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            // Requirement: /revive all only works when no event is active and sender is in lobby world.
            if (!(sender instanceof Player sp)) {
                sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveAllNotAllowed));
                return true;
            }
            if (eventManager.isActive() || (parkour != null && parkour.isActive()) || (ffa != null && ffa.isActive()) || (spleef != null && spleef.isActive())) {
                sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveAllNotAllowed));
                return true;
            }
            if (!sp.getWorld().getName().equalsIgnoreCase(cfg.lobbyWorld)) {
                sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveAllNotAllowed));
                return true;
            }

            String key = sender.getName().toLowerCase();
            long now = System.currentTimeMillis();

            if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                Long until = reviveAllConfirmUntil.get(key);
                if (until == null || until < now) {
                    sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveAllWarn));
                    reviveAllConfirmUntil.put(key, now + 5000L);
                    return true;
                }
                reviveAllConfirmUntil.remove(key);

                // Teleport everyone to lobby (requirement)
                org.bukkit.World lobby = Bukkit.getWorld(cfg.lobbyWorld);
                org.bukkit.Location lobbySpawn = cfg.lobbySpawn != null ? cfg.lobbySpawn : (lobby != null ? lobby.getSpawnLocation() : null);
                if (lobbySpawn != null) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.teleport(lobbySpawn);
                        p.setGameMode(GameMode.SURVIVAL);
                    }
                }

                // Clear tracked dead UUIDs (works for offline too via LuckPerms command)
                for (var uuid : deadPerms.getTrackedDeadUuids()) {
                    deadPerms.clearDeadUuid(uuid);
                }

                // Also clean up any online players' UI/in-memory state
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!deadPerms.isDead(p)) {
                        settingsUI.ensureCompass(p);
                    }
                }

                sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveAllDone));
                return true;
            }

            reviveAllConfirmUntil.put(key, now + 5000L);
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveAllWarn));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveNotOnline));
            return true;
        }

        if (mods.isMod(target.getUniqueId())) {
            // Mods aren't considered dead for the event, but allow clearing just in case.
            deadPerms.clearDead(target);
            sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgRevived, Map.of("player", target.getName()))));
            return true;
        }

        if (!deadPerms.isDead(target)) {
            // Allow revive for parkour-eliminated players too.
            if (parkour != null && parkour.isActive() && parkour.isEliminated(target)) {
                parkour.reviveToStart(target);
                sender.sendMessage(Text.color(cfg.msgPrefix + Text.replacePlaceholders(cfg.msgRevived, Map.of("player", target.getName()))));
                return true;
            }
            sender.sendMessage(Text.color(cfg.msgPrefix + cfg.msgReviveNotDead));
            return true;
        }

        // Remove dead marker permission and revive in the in-memory elimination tracker
        deadPerms.clearDead(target);
        if (eventManager.isActive()) {
            eventManager.revivePlayer(target);
        }

        // If parkour is active and they're in the parkour world (or eliminated there), send them to parkour start.
        PluginConfig c = PluginConfig.load(plugin.getConfig());
        if (parkour != null && parkour.isActive() && (target.getWorld().getName().equalsIgnoreCase(c.parkourWorld) || parkour.isEliminated(target))) {
            parkour.reviveToStart(target);
            sender.sendMessage(Text.color(c.msgPrefix + Text.replacePlaceholders(c.msgRevived, Map.of("player", target.getName()))));
            return true;
        }

        if (ffa != null && ffa.isActive() && target.getWorld().getName().equalsIgnoreCase(c.ffaWorld)) {
            ffa.reviveToFight(target);
            sender.sendMessage(Text.color(c.msgPrefix + Text.replacePlaceholders(c.msgRevived, Map.of("player", target.getName()))));
            return true;
        }

        if (spleef != null && spleef.isActive()) {
            spleef.revive(target);
            sender.sendMessage(Text.color(c.msgPrefix + Text.replacePlaceholders(c.msgRevived, Map.of("player", target.getName()))));
            return true;
        }

        // If anvil event is active, teleport back into it. Otherwise just clear dead status.
        if (eventManager.isActive()) {
            World eventWorld = Bukkit.getWorld(c.eventWorld);
            Location spawn = c.eventSpawn != null ? c.eventSpawn : (eventWorld != null ? eventWorld.getSpawnLocation() : null);
            if (spawn != null) {
                target.teleport(spawn);
            }
        }
        target.setGameMode(GameMode.SURVIVAL);

        // Give settings item back (if enabled) when appropriate.
        Bukkit.getScheduler().runTask(plugin, () -> settingsUI.ensureCompass(target));

        sender.sendMessage(Text.color(c.msgPrefix + Text.replacePlaceholders(c.msgRevived, Map.of("player", target.getName()))));
        return true;
    }
}


