package club.aves.anvildrop.util;

import org.bukkit.ChatColor;

import java.util.Map;

public final class Text {
    private Text() {}

    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String replacePlaceholders(String s, Map<String, String> placeholders) {
        if (s == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return s;
        String out = s;
        for (var e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}


