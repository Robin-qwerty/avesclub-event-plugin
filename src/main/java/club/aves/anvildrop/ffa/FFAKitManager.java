package club.aves.anvildrop.ffa;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FFAKitManager {

    private final JavaPlugin plugin;
    private final File kitDir;

    public FFAKitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.kitDir = new File(plugin.getDataFolder(), "kits");
        if (!kitDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            kitDir.mkdirs();
        }
    }

    public String normalizeName(String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return null;
        // keep it filesystem-safe
        n = n.replaceAll("[^a-z0-9_\\-]", "_");
        return n;
    }

    public File fileFor(String kitName) {
        return new File(kitDir, kitName + ".yml");
    }

    public boolean exists(String kitNameRaw) {
        String kitName = normalizeName(kitNameRaw);
        if (kitName == null) return false;
        return fileFor(kitName).exists();
    }

    public List<String> listKitNames() {
        File[] files = kitDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null || files.length == 0) return List.of();
        List<String> out = new ArrayList<>();
        for (File f : files) {
            String n = f.getName();
            if (n.toLowerCase(Locale.ROOT).endsWith(".yml")) {
                out.add(n.substring(0, n.length() - 4));
            }
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public boolean saveFromPlayer(String kitNameRaw, Player p, boolean overwrite) {
        String kitName = normalizeName(kitNameRaw);
        if (kitName == null || p == null) return false;

        File outFile = fileFor(kitName);
        if (outFile.exists() && !overwrite) return false;

        var inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        ItemStack[] armor = inv.getArmorContents();
        ItemStack offhand = inv.getItemInOffHand();

        YamlConfiguration y = new YamlConfiguration();
        y.set("name", kitName);
        y.set("contents", toList(contents));
        y.set("armor", toList(armor));
        y.set("offhand", offhand);

        try {
            y.save(outFile);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save kit " + kitName + ": " + e.getMessage());
            return false;
        }
    }

    public FFAKit load(String kitNameRaw) {
        String kitName = normalizeName(kitNameRaw);
        if (kitName == null) return null;
        File f = fileFor(kitName);
        if (!f.exists()) return null;

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<ItemStack> contents = castItemList(y.getList("contents"));
        List<ItemStack> armor = castItemList(y.getList("armor"));
        ItemStack offhand = y.getItemStack("offhand");
        return new FFAKit(kitName, contents, armor, offhand);
    }

    private static List<ItemStack> toList(ItemStack[] arr) {
        List<ItemStack> out = new ArrayList<>();
        if (arr == null) return out;
        for (ItemStack i : arr) out.add(i);
        return out;
    }

    private static List<ItemStack> castItemList(List<?> in) {
        List<ItemStack> out = new ArrayList<>();
        if (in == null) return out;
        for (Object o : in) {
            if (o instanceof ItemStack is) out.add(is);
            else out.add(null);
        }
        return out;
    }
}


