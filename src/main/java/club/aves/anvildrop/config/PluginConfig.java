package club.aves.anvildrop.config;

import club.aves.anvildrop.model.ArenaCuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public final class PluginConfig {

    public final String lobbyWorld;
    public final String eventWorld;

    public final Location lobbySpawn;
    public final Location eventSpawn;

    public final ArenaCuboid arena;

    public final String openTitle;
    public final String openSubtitle;
    public final int openSeconds;

    public final int countdownSeconds;
    public final String countdownChatFormat;
    public final String countdownStartMessage;

    public final boolean dropperEnabled;
    public final int intervalSeconds;
    public final double initialPercent;
    public final double increaseEachWave;
    public final double maxPercent;
    public final Material anvilMaterial;
    public final boolean onlyReplaceAir;
    public final boolean applyPhysics;

    public final boolean scoreboardEnabled;
    public final String scoreboardTitle;
    public final java.util.List<String> scoreboardLines;
    public final int scoreboardUpdateTicks;

    public final boolean lobbyScoreboardEnabled;
    public final String lobbyScoreboardTitle;
    public final java.util.List<String> lobbyScoreboardLines;
    public final String lobbyHost;

    public final String msgPrefix;
    public final String msgNoPerm;
    public final String msgPlayerOnly;
    public final String msgWorldMissing;
    public final String msgArenaNotSet;
    public final String msgOpened;
    public final String msgStarted;
    public final String msgPaused;
    public final String msgResumed;
    public final String msgStopped;
    public final String msgPercentNow;
    public final String msgReloaded;
    public final String msgDiscord;
    public final String msgReviveUsage;
    public final String msgReviveNoActiveEvent;
    public final String msgReviveNotDead;
    public final String msgReviveNotOnline;
    public final String msgRevived;
    public final String msgReviveAllWarn;
    public final String msgReviveAllDone;
    public final String msgDeadUsage;
    public final String msgDeadDone;
    public final String msgChatMutedOn;
    public final String msgChatMutedOff;
    public final String msgChatMutedBlocked;
    public final String msgVoiceMutedOn;
    public final String msgVoiceMutedOff;

    public final boolean joinTeleportEnabled;

    public final String deadPermission;

    private PluginConfig(FileConfiguration c) {
        this.lobbyWorld = c.getString("worlds.lobbyWorld", "world");
        this.eventWorld = c.getString("worlds.eventWorld", "anvildrop");

        this.lobbySpawn = readSpawn(c, lobbyWorld, "spawns.lobby");
        this.eventSpawn = readSpawn(c, eventWorld, "spawns.event");

        this.arena = readArena(c, eventWorld);

        this.openTitle = c.getString("openScreen.title", "&aAnvil Drop Event");
        this.openSubtitle = c.getString("openScreen.subtitle", "&fGet ready...");
        this.openSeconds = Math.max(1, c.getInt("openScreen.seconds", 5));

        this.countdownSeconds = Math.max(0, c.getInt("countdown.seconds", 10));
        this.countdownChatFormat = c.getString("countdown.chatFormat", "&eEvent starts in &6{seconds}&e...");
        this.countdownStartMessage = c.getString("countdown.startMessage", "&aGO!");

        this.dropperEnabled = c.getBoolean("dropper.enabled", true);
        this.intervalSeconds = Math.max(1, c.getInt("dropper.intervalSeconds", 6));
        this.initialPercent = clampPercent(c.getDouble("dropper.initialAnvilPercent", 40.0));
        this.increaseEachWave = Math.max(0.0, c.getDouble("dropper.increasePercentEachWave", 5.0));
        this.maxPercent = clampPercent(c.getDouble("dropper.maxAnvilPercent", 95.0));
        Material m = Material.matchMaterial(c.getString("dropper.material", "ANVIL"));
        this.anvilMaterial = (m == null ? Material.ANVIL : m);
        this.onlyReplaceAir = c.getBoolean("dropper.onlyReplaceAir", true);
        this.applyPhysics = c.getBoolean("dropper.applyPhysics", true);

        this.scoreboardEnabled = c.getBoolean("scoreboard.enabled", true);
        this.scoreboardTitle = c.getString("scoreboard.title", "&6&lANVIL DROP");
        java.util.List<String> eventLines = c.getStringList("scoreboard.lines");
        if (eventLines == null || eventLines.isEmpty()) {
            // Backwards-compat with older configs
            String aliveLine = c.getString("scoreboard.aliveLine", "&eAlive: &f{alive}");
            eventLines = java.util.List.of(aliveLine);
        }
        this.scoreboardLines = eventLines;
        this.scoreboardUpdateTicks = Math.max(10, c.getInt("scoreboard.updateTicks", 20));

        this.lobbyScoreboardEnabled = c.getBoolean("lobbyScoreboard.enabled", true);
        this.lobbyScoreboardTitle = c.getString("lobbyScoreboard.title", "&6&lAvesclub event");
        java.util.List<String> lobbyLines = c.getStringList("lobbyScoreboard.lines");
        if (lobbyLines == null || lobbyLines.isEmpty()) {
            lobbyLines = java.util.List.of(
                    "",
                    "&ePlayers: &f{online}",
                    "&eHost: &f{host}",
                    "",
                    "&bavesclub.net"
            );
        }
        this.lobbyScoreboardLines = lobbyLines;
        this.lobbyHost = c.getString("lobbyScoreboard.host", "Robin");

        this.msgPrefix = c.getString("messages.prefix", "&8[&6AnvilDrop&8] &r");
        this.msgNoPerm = c.getString("messages.noPerm", "&cYou don't have permission.");
        this.msgPlayerOnly = c.getString("messages.playerOnly", "&cPlayers only.");
        this.msgWorldMissing = c.getString("messages.worldMissing", "&cWorld not found: &f{world}");
        this.msgArenaNotSet = c.getString("messages.arenaNotSet", "&cArena is not set. Use &f/anvildrop setarena&c with a WorldEdit selection.");
        this.msgOpened = c.getString("messages.opened", "&aEvent opened! Teleported lobby players.");
        this.msgStarted = c.getString("messages.started", "&aEvent started!");
        this.msgPaused = c.getString("messages.paused", "&eAnvil drops paused.");
        this.msgResumed = c.getString("messages.resumed", "&aAnvil drops resumed.");
        this.msgStopped = c.getString("messages.stopped", "&cEvent stopped.");
        this.msgPercentNow = c.getString("messages.percentNow", "&eDropping anvils at &6{percent}%&e now.");
        this.msgReloaded = c.getString("messages.reloaded", "&aConfig reloaded.");
        this.msgDiscord = c.getString("messages.discord", "&bDiscord: &f{link}");
        this.msgReviveUsage = c.getString("messages.reviveUsage", "&cUsage: /revive <player> OR /revive all");
        this.msgReviveNoActiveEvent = c.getString("messages.reviveNoActiveEvent", "&cThere is no active event right now.");
        this.msgReviveNotDead = c.getString("messages.reviveNotDead", "&cThat player is not dead.");
        this.msgReviveNotOnline = c.getString("messages.reviveNotOnline", "&cThat player must be online to revive.");
        this.msgRevived = c.getString("messages.revived", "&aRevived &f{player}&a!");
        this.msgReviveAllWarn = c.getString("messages.reviveAllWarn", "&cThis will remove the dead flag from EVERYONE. Type &f/revive all confirm &cwithin 5 seconds.");
        this.msgReviveAllDone = c.getString("messages.reviveAllDone", "&aRevived all dead players.");
        this.msgDeadUsage = c.getString("messages.deadUsage", "&cUsage: /dead <player>");
        this.msgDeadDone = c.getString("messages.deadDone", "&cMarked &f{player}&c as dead.");
        this.msgChatMutedOn = c.getString("messages.chatMutedOn", "&cChat was muted.");
        this.msgChatMutedOff = c.getString("messages.chatMutedOff", "&aChat was unmuted.");
        this.msgChatMutedBlocked = c.getString("messages.chatMutedBlocked", "&cChat is muted.");
        this.msgVoiceMutedOn = c.getString("messages.voiceMutedOn", "&cVoice chat was muted.");
        this.msgVoiceMutedOff = c.getString("messages.voiceMutedOff", "&aVoice chat was unmuted.");

        this.joinTeleportEnabled = c.getBoolean("joinTeleport.enabled", true);

        this.deadPermission = c.getString("deadTracking.permission", "event.dead.status");
    }

    public static PluginConfig load(FileConfiguration c) {
        return new PluginConfig(c);
    }

    private static Location readSpawn(FileConfiguration c, String worldName, String path) {
        World w = Bukkit.getWorld(worldName);
        double x = c.getDouble(path + ".x", 0.5);
        double y = c.getDouble(path + ".y", 80.0);
        double z = c.getDouble(path + ".z", 0.5);
        float yaw = (float) c.getDouble(path + ".yaw", 0.0);
        float pitch = (float) c.getDouble(path + ".pitch", 0.0);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    private static ArenaCuboid readArena(FileConfiguration c, String eventWorld) {
        int minX = c.getInt("arena.min.x", -10);
        int minY = c.getInt("arena.min.y", 60);
        int minZ = c.getInt("arena.min.z", -10);
        int maxX = c.getInt("arena.max.x", 10);
        int maxY = c.getInt("arena.max.y", 90);
        int maxZ = c.getInt("arena.max.z", 10);
        return ArenaCuboid.normalized(eventWorld, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double clampPercent(double p) {
        if (Double.isNaN(p) || Double.isInfinite(p)) return 0.0;
        return Math.max(0.0, Math.min(100.0, p));
    }
}


