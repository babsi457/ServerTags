package de.basky.servertags.data;

import de.basky.servertags.data.Models.*;
import de.basky.servertags.icon.IconRegistry;

import java.util.Map;

public final class SuffixService {
    private SuffixService() {}

    public static Suffix parse(String value, Map<String, Icon> icons) {
        Suffix suffix =
                value.startsWith("text:")
                        ? new Suffix("text", value.substring(5))
                        : IconRegistry.resolve(icons, value)
                                .map(id -> new Suffix("icon", id))
                                .orElseGet(() -> new Suffix("text", value));
        suffix.validate(icons);
        return suffix;
    }
}
