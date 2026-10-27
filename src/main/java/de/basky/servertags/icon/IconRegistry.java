package de.basky.servertags.icon;

import static de.basky.servertags.data.Models.checkId;

import de.basky.servertags.data.Models.Icon;

import java.util.*;

/** Shared icon validation and Minecraft ID lookup. */
public final class IconRegistry {
    private IconRegistry() {}

    public static void validate(Map<String, Icon> icons) {
        if (icons.size() > 512) throw new IllegalArgumentException("Maximal 512 Icons erlaubt.");
        Set<Integer> codepoints = new HashSet<>();
        icons.forEach(
                (id, icon) -> {
                    checkId(id);
                    Objects.requireNonNull(icon).validate();
                    if (!codepoints.add(icon.codepoint()))
                        throw new IllegalArgumentException("Doppelter Icon-Codepoint.");
                });
    }

    public static Optional<String> resolve(Map<String, Icon> icons, String input) {
        String id = input.startsWith("minecraft:") ? input.substring(10) : input;
        return icons.containsKey(id) ? Optional.of(id) : Optional.empty();
    }
}
