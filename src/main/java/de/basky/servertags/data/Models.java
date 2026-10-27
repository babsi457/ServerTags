package de.basky.servertags.data;

import java.util.*;

public final class Models {
    private Models() {}

    public record Config(
            int schemaVersion,
            boolean chatEnabled,
            boolean tabEnabled,
            boolean nameTagsEnabled,
            String resourcePackId) {
        public static Config defaults() {
            return new Config(1, true, true, false, "");
        }

        public void validate() {
            if (schemaVersion != 1)
                throw new IllegalArgumentException("Unbekannte Konfigurationsversion.");
            if (resourcePackId == null) throw new IllegalArgumentException("resourcePackId fehlt.");
            if (!resourcePackId.isEmpty()) UUID.fromString(resourcePackId);
        }
    }

    public record Tag(String text, String color, boolean bold, boolean italic, boolean adminOnly) {
        public void validate() {
            checkText(text, 48);
            checkColor(color);
        }

        public String label() {
            return text.startsWith("[") && text.endsWith("]") ? text : "[" + text + "]";
        }
    }

    public record Suffix(String kind, String value) {
        public void validate(Map<String, Icon> icons) {
            if (!Set.of("text", "icon").contains(kind))
                throw new IllegalArgumentException("Ungültiger Suffix-Typ.");
            checkText(value, 96);
            if (kind.equals("icon") && !icons.containsKey(value))
                throw new IllegalArgumentException("Unbekanntes Icon: " + value);
        }
    }

    public record PlayerData(String prefix, Suffix suffix) {
        public static PlayerData empty() {
            return new PlayerData(null, null);
        }
    }

    public record Icon(int codepoint, String texture, String fallback) {
        public void validate() {
            if (codepoint < 0xE000 || codepoint > 0xF8FF)
                throw new IllegalArgumentException(
                        "Icon-Codepoint muss im Private-Use-Bereich liegen.");
            if (texture == null
                    || !texture.matches("[a-z0-9_.-]+:[a-z0-9_./-]+\\.png")
                    || texture.contains(".."))
                throw new IllegalArgumentException("Ungültiger Texturpfad.");
            checkText(fallback, 48);
        }

        public String glyph() {
            return Character.toString(codepoint);
        }
    }

    public record Snapshot(
            Config config,
            Map<String, Tag> tags,
            Map<UUID, PlayerData> players,
            Map<String, Icon> icons) {
        public Snapshot {
            tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
            players = Map.copyOf(players);
            icons = Collections.unmodifiableMap(new LinkedHashMap<>(icons));
        }

        public PlayerData player(UUID id) {
            return players.getOrDefault(id, PlayerData.empty());
        }
    }

    public static String checkId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,31}"))
            throw new IllegalArgumentException("IDs: 1–32 Kleinbuchstaben, Zahlen, _ oder -.");
        return id;
    }

    public static void checkText(String text, int max) {
        if (text == null
                || text.isBlank()
                || text.codePointCount(0, text.length()) > max
                || text.codePoints()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                || c == 0xA7
                                                || Character.getType(c) == Character.FORMAT))
            throw new IllegalArgumentException(
                    "Text muss 1–" + max + " sichtbare Zeichen enthalten (keine Steuerzeichen).");
    }

    public static void checkColor(String color) {
        if (color == null
                || !(color.matches("#[0-9a-fA-F]{6}")
                        || Set.of(
                                        "black",
                                        "dark_blue",
                                        "dark_green",
                                        "dark_aqua",
                                        "dark_red",
                                        "dark_purple",
                                        "gold",
                                        "gray",
                                        "dark_gray",
                                        "blue",
                                        "green",
                                        "aqua",
                                        "red",
                                        "light_purple",
                                        "yellow",
                                        "white")
                                .contains(color)))
            throw new IllegalArgumentException("Farbe: Minecraft-Farbname oder #RRGGBB.");
    }
}
