package de.basky.servertags.tab;

import de.basky.servertags.ServerTags;

import me.neznamy.tab.api.*;
import me.neznamy.tab.api.event.EventBus;
import me.neznamy.tab.api.event.player.PlayerLoadEvent;
import me.neznamy.tab.api.event.plugin.TabLoadEvent;
import me.neznamy.tab.api.nametag.NameTagManager;
import me.neznamy.tab.api.tablist.TabListFormatManager;

import net.minecraft.server.MinecraftServer;

import java.util.*;

/** Adds ServerTags placeholders through TAB's prefix and suffix APIs. */
public final class TabIntegration {
    public static final String PREFIX = "%servertags_prefix%";
    public static final String SUFFIX = "%rel_servertags_suffix%";
    private final ServerTags mod;
    private final MinecraftServer server;
    private EventBus bus;
    private final Map<UUID, Previous> previous = new HashMap<>();
    private final Map<UUID, Previous> previousNames = new HashMap<>();
    private boolean warned;
    private final me.neznamy.tab.api.event.EventHandler<PlayerLoadEvent> playerListener;
    private final me.neznamy.tab.api.event.EventHandler<TabLoadEvent> loadListener;

    private record Previous(
            String prefix, String suffix, String appliedPrefix, String appliedSuffix) {}

    public TabIntegration(ServerTags mod, MinecraftServer server) {
        this.mod = mod;
        this.server = server;
        playerListener =
                event ->
                        server.execute(
                                () -> {
                                    previous.remove(event.getPlayer().getUniqueId());
                                    previousNames.remove(event.getPlayer().getUniqueId());
                                    apply(event.getPlayer());
                                });
        loadListener =
                event ->
                        server.execute(
                                () -> {
                                    previous.clear();
                                    previousNames.clear();
                                    registerPlaceholders();
                                    refresh();
                                    ServerTags.LOGGER.info(
                                            "TAB-Reload: ServerTags-Platzhalter und Anzeigen"
                                                + " wiederhergestellt.");
                                });
    }

    public void tick() {
        try {
            EventBus current = TabAPI.getInstance().getEventBus();
            if (current != null && current != bus) {
                if (bus != null) {
                    bus.unregister(playerListener);
                    bus.unregister(loadListener);
                }
                bus = current;
                bus.register(PlayerLoadEvent.class, playerListener);
                bus.register(TabLoadEvent.class, loadListener);
                previous.clear();
                previousNames.clear();
                registerPlaceholders();
                refresh();
                ServerTags.LOGGER.info(
                        "TAB-6.2.1-API angebunden (PlayerLoadEvent und TabLoadEvent).");
            }
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    private void registerPlaceholders() {
        var manager = TabAPI.getInstance().getPlaceholderManager();
        manager.unregisterPlaceholder(PREFIX);
        manager.unregisterPlaceholder(SUFFIX);
        manager.registerPlayerPlaceholder(
                PREFIX, 500, player -> mod.formatter().tabPrefix(player.getUniqueId()));
        manager.registerRelationalPlaceholder(
                SUFFIX,
                500,
                (viewer, target) ->
                        mod.formatter().tabSuffix(target.getUniqueId(), viewer.getUniqueId()));
    }

    public void refresh() {
        try {
            for (TabPlayer player : TabAPI.getInstance().getOnlinePlayers())
                if (player.isLoaded()) apply(player);
        } catch (RuntimeException | LinkageError e) {
            warn(e);
        }
    }

    public void forget(UUID uuid) {
        previous.remove(uuid);
        previousNames.remove(uuid);
    }

    private void apply(TabPlayer player) {
        if (!player.isLoaded()) return;
        var config = mod.service().snapshot().config();
        TabListFormatManager list = TabAPI.getInstance().getTabListFormatManager();
        if (list != null) {
            Previous old = previous.get(player.getUniqueId());
            if (config.tabEnabled()) {
                if (old == null) {
                    String customPrefix = list.getCustomPrefix(player),
                            customSuffix = list.getCustomSuffix(player);
                    String prefix =
                            append(
                                    customPrefix != null
                                            ? customPrefix
                                            : list.getOriginalRawPrefix(player),
                                    PREFIX);
                    String suffix =
                            append(
                                    customSuffix != null
                                            ? customSuffix
                                            : list.getOriginalRawSuffix(player),
                                    SUFFIX);
                    old = new Previous(customPrefix, customSuffix, prefix, suffix);
                    previous.put(player.getUniqueId(), old);
                }
                list.setPrefix(player, old.appliedPrefix());
                list.setSuffix(player, old.appliedSuffix());
            } else if (old != null) {
                if (Objects.equals(list.getCustomPrefix(player), old.appliedPrefix()))
                    list.setPrefix(player, old.prefix());
                if (Objects.equals(list.getCustomSuffix(player), old.appliedSuffix()))
                    list.setSuffix(player, old.suffix());
                previous.remove(player.getUniqueId());
            }
        } else if (config.tabEnabled() && !warned) {
            ServerTags.LOGGER.warn(
                    "TAB: tablist-name-formatting ist deaktiviert. Für die Anzeige in TAB"
                        + " aktivieren.");
            warned = true;
        }
        NameTagManager names = TabAPI.getInstance().getNameTagManager();
        if (names == null) return;
        Previous oldName = previousNames.get(player.getUniqueId());
        if (config.nameTagsEnabled()) {
            if (oldName == null) {
                String cp = names.getCustomPrefix(player), cs = names.getCustomSuffix(player);
                oldName =
                        new Previous(
                                cp,
                                cs,
                                append(
                                        cp != null ? cp : names.getOriginalRawPrefix(player),
                                        PREFIX),
                                append(
                                        cs != null ? cs : names.getOriginalRawSuffix(player),
                                        SUFFIX));
                previousNames.put(player.getUniqueId(), oldName);
            }
            names.setPrefix(player, oldName.appliedPrefix());
            names.setSuffix(player, oldName.appliedSuffix());
        } else if (oldName != null) {
            if (Objects.equals(names.getCustomPrefix(player), oldName.appliedPrefix()))
                names.setPrefix(player, oldName.prefix());
            if (Objects.equals(names.getCustomSuffix(player), oldName.appliedSuffix()))
                names.setSuffix(player, oldName.suffix());
            previousNames.remove(player.getUniqueId());
        }
    }

    public static String append(String original, String placeholder) {
        return original == null
                ? placeholder
                : original.contains(placeholder) ? original : original + placeholder;
    }

    private void warn(Throwable e) {
        if (!warned) {
            ServerTags.LOGGER.error(
                    "TAB-Integration derzeit nicht verfügbar; siehe TAB-Konfiguration.", e);
            warned = true;
        }
    }

    public void close() {
        if (bus != null) {
            bus.unregister(playerListener);
            bus.unregister(loadListener);
        }
        try {
            var placeholders = TabAPI.getInstance().getPlaceholderManager();
            placeholders.unregisterPlaceholder(PREFIX);
            placeholders.unregisterPlaceholder(SUFFIX);
        } catch (RuntimeException ignored) {
            /* TAB may already be stopped. */
        }
        previous.clear();
        previousNames.clear();
    }
}
