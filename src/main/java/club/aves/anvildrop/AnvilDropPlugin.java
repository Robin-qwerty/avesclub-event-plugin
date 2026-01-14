package club.aves.anvildrop;

import club.aves.anvildrop.commands.AnvilDropCommand;
import club.aves.anvildrop.commands.DeadCommand;
import club.aves.anvildrop.commands.DiscordCommand;
import club.aves.anvildrop.commands.EventAdminCommand;
import club.aves.anvildrop.commands.MuteChatCommand;
import club.aves.anvildrop.commands.ReviveCommand;
import club.aves.anvildrop.commands.VoiceMuteCommand;
import club.aves.anvildrop.chat.ChatMuteListener;
import club.aves.anvildrop.chat.ChatMuteManager;
import club.aves.anvildrop.config.ConfigMerge;
import club.aves.anvildrop.dead.DeadPermissionService;
import club.aves.anvildrop.event.AnvilDropEventManager;
import club.aves.anvildrop.hooks.WorldEditHook;
import club.aves.anvildrop.listeners.JoinTeleportListener;
import club.aves.anvildrop.mods.ModRegistry;
import club.aves.anvildrop.ui.AnvilDropScoreboard;
import club.aves.anvildrop.ui.EventSettingsUI;
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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        // If you update the plugin, automatically add any new missing config keys with default values.
        ConfigMerge.mergeMissingKeys(this, "config.yml");
        reloadConfig();

        this.modRegistry = new ModRegistry(this);
        this.deadPerms = new DeadPermissionService(this);
        this.chatMute = new ChatMuteManager(this);
        this.worldEditHook = new WorldEditHook(this);
        this.scoreboard = new AnvilDropScoreboard(this);
        this.eventManager = new AnvilDropEventManager(this, scoreboard, modRegistry, deadPerms);
        this.eventSettingsUI = new EventSettingsUI(this, eventManager, deadPerms);
        this.eventManager.setOnEventEnd(() -> eventSettingsUI.resetAll());

        var cmd = getCommand("anvildrop");
        if (cmd != null) {
            var executor = new AnvilDropCommand(this, eventManager, worldEditHook);
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

        var revive = getCommand("revive");
        if (revive != null) {
            revive.setExecutor(new ReviveCommand(this, eventManager, deadPerms, eventSettingsUI, modRegistry));
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

        Bukkit.getPluginManager().registerEvents(eventManager, this);
        Bukkit.getPluginManager().registerEvents(scoreboard, this);
        Bukkit.getPluginManager().registerEvents(new JoinTeleportListener(this), this);
        Bukkit.getPluginManager().registerEvents(eventSettingsUI, this);
        Bukkit.getPluginManager().registerEvents(new ChatMuteListener(this, chatMute), this);

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
    }
}


