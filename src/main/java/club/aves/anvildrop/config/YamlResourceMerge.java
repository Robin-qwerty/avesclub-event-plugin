package club.aves.anvildrop.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class YamlResourceMerge {
    private YamlResourceMerge() {}

    /**
     * Ensures a YAML file exists in the plugin data folder, and merges missing keys from the jar resource.
     * Existing values are not overwritten.
     */
    public static void ensureAndMerge(JavaPlugin plugin, String filename) {
        try {
            if (!plugin.getDataFolder().exists()) {
                //noinspection ResultOfMethodCallIgnored
                plugin.getDataFolder().mkdirs();
            }

            File file = new File(plugin.getDataFolder(), filename);
            if (!file.exists()) {
                plugin.saveResource(filename, false);
            }

            YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
            try (InputStream in = plugin.getResource(filename)) {
                if (in == null) return;
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                current.setDefaults(defaults);
                current.options().copyDefaults(true);
                current.save(file);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to merge resource yaml " + filename + ": " + e.getMessage());
        }
    }
}


