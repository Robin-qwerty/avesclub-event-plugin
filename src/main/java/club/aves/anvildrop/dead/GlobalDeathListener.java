package club.aves.anvildrop.dead;

import club.aves.anvildrop.mods.ModRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Marks players as dead on ANY death, regardless of world/cause.
 */
public final class GlobalDeathListener implements Listener {

    private final DeadPermissionService deadPerms;
    private final ModRegistry mods;

    public GlobalDeathListener(DeadPermissionService deadPerms, ModRegistry mods) {
        this.deadPerms = deadPerms;
        this.mods = mods;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (p == null) return;
        if (mods != null && mods.isMod(p.getUniqueId())) return;
        deadPerms.markDead(p);
    }
}


