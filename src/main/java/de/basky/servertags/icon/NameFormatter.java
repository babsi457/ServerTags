package de.basky.servertags.icon;

import static de.basky.servertags.data.Models.*;

import de.basky.servertags.data.TagService;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

import java.util.*;

public final class NameFormatter {
    public static final FontDescription FONT =
            new FontDescription.Resource(Identifier.parse("servertags:icons"));
    private final TagService service;
    private final ResourcePackState packs;

    public NameFormatter(TagService service, ResourcePackState packs) {
        this.service = service;
        this.packs = packs;
    }

    public Component name(UUID target, UUID viewer, Component name) {
        Snapshot s = service.snapshot();
        PlayerData p = s.player(target);
        MutableComponent result = Component.empty();
        Tag tag = s.tags().get(p.prefix());
        if (tag != null) result.append(tag(tag)).append(" ");
        result.append(name.copy().withStyle(Style.EMPTY));
        if (p.suffix() != null) result.append(" ").append(suffix(p.suffix(), viewer));
        return result;
    }

    public static MutableComponent tag(Tag tag) {
        TextColor color =
                tag.color().startsWith("#")
                        ? TextColor.fromRgb(Integer.parseInt(tag.color().substring(1), 16))
                        : TextColor.fromLegacyFormat(
                                ChatFormatting.valueOf(tag.color().toUpperCase(Locale.ROOT)));
        return Component.literal(tag.label())
                .withStyle(
                        Style.EMPTY.withColor(color).withBold(tag.bold()).withItalic(tag.italic()));
    }

    public Component suffix(Suffix suffix, UUID viewer) {
        if (suffix.kind().equals("text")) return legacy(suffix.value());
        Icon icon = service.snapshot().icons().get(suffix.value());
        if (icon == null) return Component.literal("[Unbekanntes Icon]");
        return packs.loaded(viewer)
                ? Component.literal(icon.glyph())
                        .withStyle(Style.EMPTY.withFont(FONT).withColor(ChatFormatting.WHITE))
                : Component.literal(icon.fallback()).withStyle(ChatFormatting.GRAY);
    }

    public String tabPrefix(UUID target) {
        Snapshot s = service.snapshot();
        Tag tag = s.tags().get(s.player(target).prefix());
        if (tag == null) return "";
        String color =
                tag.color().startsWith("#")
                        ? "&" + tag.color()
                        : ChatFormatting.valueOf(tag.color().toUpperCase(Locale.ROOT))
                                .toString()
                                .replace('\u00a7', '&');
        return "&r"
                + color
                + (tag.bold() ? "&l" : "")
                + (tag.italic() ? "&o" : "")
                + escapeTab(tag.label())
                + "&r ";
    }

    public String tabSuffix(UUID target, UUID viewer) {
        Snapshot s = service.snapshot();
        Suffix suffix = s.player(target).suffix();
        if (suffix == null) return "";
        if (suffix.kind().equals("text")) return " &r" + escapeTab(suffix.value()) + "&r";
        Icon icon = s.icons().get(suffix.value());
        if (icon == null) return "";
        return " &r&f"
                + (packs.loaded(viewer)
                        ? "<font:servertags:icons>" + icon.glyph() + "</font>"
                        : escapeTab(icon.fallback()))
                + "&r";
    }

    // TAB consumes its own markup. User text cannot introduce placeholders or MiniMessage tags.
    public static String escapeTab(String text) {
        return text.replace("%", "％").replace("<", "‹").replace(">", "›");
    }

    public static MutableComponent legacy(String text) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder part = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                ChatFormatting format = ChatFormatting.getByCode(text.charAt(i + 1));
                if (format != null) {
                    if (!part.isEmpty()) {
                        out.append(Component.literal(part.toString()).withStyle(style));
                        part.setLength(0);
                    }
                    style =
                            format == ChatFormatting.RESET
                                    ? Style.EMPTY
                                    : style.applyLegacyFormat(format);
                    i++;
                    continue;
                }
            }
            part.append(text.charAt(i));
        }
        return out.append(Component.literal(part.toString()).withStyle(style));
    }
}
