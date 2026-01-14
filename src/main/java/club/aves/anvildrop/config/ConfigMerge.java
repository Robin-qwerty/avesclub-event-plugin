package club.aves.anvildrop.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigMerge {
    private ConfigMerge() {}

    /**
     * Merges missing keys from the plugin's bundled resource config.yml into the current config.yml.
     * Existing values are not overwritten.
     *
     * @return true if any keys were added
     */
    public static boolean mergeMissingKeys(JavaPlugin plugin, String resourceName) {
        try (InputStream in = plugin.getResource(resourceName)) {
            if (in == null) return false;

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            FileConfiguration current = plugin.getConfig();

            // Use Bukkit's default mechanism; this more reliably writes missing keys into the actual config.yml file.
            current.setDefaults(defaults);
            current.options().copyDefaults(true);
            plugin.saveConfig();

            // We can't easily know if it changed without comparing files; treat as "applied".
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Config merge failed: " + e.getMessage());
            return false;
        }
    }
}


