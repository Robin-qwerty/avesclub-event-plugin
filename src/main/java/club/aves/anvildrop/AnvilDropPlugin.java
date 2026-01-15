package club.aves.anvildrop;

import club.aves.anvildrop.commands.AnvilDropCommand;
import club.aves.anvildrop.commands.DeadCommand;
import club.aves.anvildrop.commands.DiscordCommand;
import club.aves.anvildrop.commands.EventHelpCommand;
import club.aves.anvildrop.commands.EventAdminCommand;
import club.aves.anvildrop.commands.FFAEventCommand;
import club.aves.anvildrop.commands.MuteChatCommand;
import club.aves.anvildrop.commands.ParkourEventCommand;
import club.aves.anvildrop.commands.ReviveCommand;
import club.aves.anvildrop.commands.VoiceMuteCommand;
import club.aves.anvildrop.chat.ChatMuteListener;
import club.aves.anvildrop.chat.ChatMuteManager;
import club.aves.anvildrop.access.EventAccessListener;
import club.aves.anvildrop.config.ConfigMerge;
import club.aves.anvildrop.config.YamlResourceMerge;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.event.AnvilDropEventManager;
import club.aves.anvildrop.ffa.FFAEventManager;
import club.aves.anvildrop.ffa.FFAKitManager;
import club.aves.anvildrop.hooks.WorldEditHook;
import club.aves.anvildrop.listeners.JoinTeleportListener;
import club.aves.anvildrop.mods.ModRegistry;
import club.aves.anvildrop.parkour.ParkourEventManager;
import club.aves.anvildrop.tablist.DeadTabListManager;
import club.aves.anvildrop.ui.AnvilDropScoreboard;
import club.aves.anvildrop.ui.EventSettingsUI;
import club.aves.anvildrop.reconnect.ReconnectManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnvilDropPlugin extends JavaPlugin {

    private AnvilDropEventManager eventManager;
    private AnvilDropScoreboard scoreboard;
    private WorldEditHook worldEditHook;
    private ModRegistry modRegistry;
    private EventSettingsUI eventSettingsUI;
    private DeadPermissionService deadPerms;
    private ChatMuteManager chatMute;
    private ParkourEventManager parkourEvent;
    private DeadTabListManager deadTab;
    private FFAKitManager ffaKits;
    private FFAEventManager ffaEvent;
    private ReconnectManager reconnectManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        // If you update the plugin, automatically add any new missing config keys with default values.
        ConfigMerge.mergeMissingKeys(this, "config.yml");
        reloadConfig();
        // Merge extra yaml resources
        YamlResourceMerge.ensureAndMerge(this, "eventhelp.yml");

        this.modRegistry = new ModRegistry(this);
        this.deadPerms = new DeadPermissionService(this);
        this.chatMute = new ChatMuteManager(this);
        this.worldEditHook = new WorldEditHook(this);
        this.scoreboard = new AnvilDropScoreboard(this);
        this.eventManager = new AnvilDropEventManager(this, scoreboard, modRegistry, deadPerms);
        this.eventSettingsUI = new EventSettingsUI(this, eventManager, deadPerms);
        this.eventManager.setOnEventEnd(() -> eventSettingsUI.resetAll());
        this.parkourEvent = new ParkourEventManager(this, scoreboard, eventSettingsUI, deadPerms);
        this.deadTab = new DeadTabListManager(this, deadPerms);
        this.deadTab.start();
        this.ffaKits = new FFAKitManager(this);
        this.ffaEvent = new FFAEventManager(this, ffaKits, deadPerms, scoreboard);
        this.reconnectManager = new ReconnectManager(this, deadPerms, eventManager, parkourEvent, ffaEvent);
        this.eventManager.setReconnectManager(reconnectManager);
        this.parkourEvent.setReconnectManager(reconnectManager);
        this.ffaEvent.setReconnectManager(reconnectManager);

        var cmd = getCommand("anvildrop");
        if (cmd != null) {
            var executor = new AnvilDropCommand(this, eventManager, worldEditHook, parkourEvent, ffaEvent);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        var discord = getCommand("discord");
        if (discord != null) {
            discord.setExecutor(new DiscordCommand(this));
        }

        var event = getCommand("event");
        if (event != null) {
            var executor = new EventAdminCommand(this, modRegistry);
            event.setExecutor(executor);
            event.setTabCompleter(executor);
        }

        var eventhelp = getCommand("eventhelp");
        if (eventhelp != null) {
            eventhelp.setExecutor(new EventHelpCommand(this));
        }

        var revive = getCommand("revive");
        if (revive != null) {
            revive.setExecutor(new ReviveCommand(this, eventManager, deadPerms, eventSettingsUI, modRegistry, parkourEvent, ffaEvent));
        }

        var dead = getCommand("dead");
        if (dead != null) {
            dead.setExecutor(new DeadCommand(this, deadPerms, modRegistry));
        }

        var mutechat = getCommand("mutechat");
        if (mutechat != null) {
            mutechat.setExecutor(new MuteChatCommand(this, chatMute));
        }

        var voicemute = getCommand("voicemute");
        if (voicemute != null) {
            voicemute.setExecutor(new VoiceMuteCommand(this));
        }

        var parkour = getCommand("parkourevent");
        if (parkour != null) {
            parkour.setExecutor(new ParkourEventCommand(this, parkourEvent, eventManager, ffaEvent));
        }

        var ffa = getCommand("ffaevent");
        if (ffa != null) {
            ffa.setExecutor(new FFAEventCommand(this, ffaEvent, ffaKits, eventManager, parkourEvent));
        }

        Bukkit.getPluginManager().registerEvents(eventManager, this);
        Bukkit.getPluginManager().registerEvents(scoreboard, this);
        Bukkit.getPluginManager().registerEvents(reconnectManager, this);
        Bukkit.getPluginManager().registerEvents(new JoinTeleportListener(this, reconnectManager), this);
        Bukkit.getPluginManager().registerEvents(eventSettingsUI, this);
        Bukkit.getPluginManager().registerEvents(new ChatMuteListener(this, chatMute), this);
        Bukkit.getPluginManager().registerEvents(parkourEvent, this);
        Bukkit.getPluginManager().registerEvents(deadTab, this);
        Bukkit.getPluginManager().registerEvents(ffaEvent, this);
        Bukkit.getPluginManager().registerEvents(new EventAccessListener(this, eventManager, parkourEvent, ffaEvent, reconnectManager), this);

        // If the plugin is reloaded while players are already online in the event world, ensure they have the compass.
        Bukkit.getScheduler().runTask(this, () -> eventSettingsUI.initForOnlinePlayers());

        // Apply stored voice mute state on boot (so it persists across restarts)
        Bukkit.getScheduler().runTask(this, () -> {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;
            boolean enabled = getConfig().getBoolean("voiceMute.enabled", false);
            String group = getConfig().getString("voiceMute.group", "default");
            String perm = getConfig().getString("voiceMute.permission", "voicechat.speak");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp group " + group + " permission set " + perm + " " + (enabled ? "false" : "true"));
        });
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.shutdown();
        }
        if (scoreboard != null) {
            scoreboard.shutdown();
        }
        if (deadTab != null) {
            deadTab.stop();
        }
    }
}


