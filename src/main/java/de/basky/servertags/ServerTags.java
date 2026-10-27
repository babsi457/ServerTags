package de.basky.servertags;

import de.basky.servertags.command.TagCommands;
import de.basky.servertags.data.*;
import de.basky.servertags.icon.*;
import de.basky.servertags.tab.TabIntegration;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import org.slf4j.*;

import java.io.IOException;
import java.util.UUID;

public final class ServerTags implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ServerTags");
    private static ServerTags instance;
    private TagService service;
    private final ResourcePackState packs = new ResourcePackState();
    private NameFormatter formatter;
    private MinecraftServer server;
    private TabIntegration tab;
    private int ticks;

    public static ServerTags instance() {
        return instance;
    }

    public TagService service() {
        return service;
    }

    public ResourcePackState packs() {
        return packs;
    }

    public NameFormatter formatter() {
        return formatter;
    }

    @Override
    public void onInitialize() {
        try {
            service =
                    new TagService(
                            new JsonStore(
                                    FabricLoader.getInstance()
                                            .getConfigDir()
                                            .resolve("servertags")));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "ServerTags: Konfiguration ungültig. Dateien korrigieren oder .bak"
                        + " wiederherstellen.",
                    e);
        }
        formatter = new NameFormatter(service, packs);
        instance = this;
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, environment) -> new TagCommands(this).register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(
                current -> {
                    server = current;
                    reconfigure();
                    if (FabricLoader.getInstance().isModLoaded("tab")) {
                        tab = new TabIntegration(this, server);
                        tab.tick();
                    } else
                        LOGGER.warn(
                                "NEZNAMY TAB fehlt. Chat und Befehle aktiv; TAB-Anzeige benötigt"
                                    + " TAB 6.2.1.");
                    String version =
                            FabricLoader.getInstance()
                                    .getModContainer("servertags")
                                    .orElseThrow()
                                    .getMetadata()
                                    .getVersion()
                                    .getFriendlyString();
                    LOGGER.info(
                            "ServerTags {} geladen: {} Tags, {} Icons, {} Spielerdatensätze.",
                            version,
                            service.snapshot().tags().size(),
                            service.snapshot().icons().size(),
                            service.snapshot().players().size());
                });
        ServerTickEvents.END_SERVER_TICK.register(
                current -> {
                    if (tab != null && ++ticks % 20 == 0) tab.tick();
                });
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, current) -> {
                    UUID id = handler.getPlayer().getUUID();
                    packs.forget(id);
                    if (tab != null) tab.forget(id);
                });
        ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register(
                (handler, current) -> packs.forget(handler.getOwner().id()));
        ServerLifecycleEvents.SERVER_STOPPING.register(
                current -> {
                    if (tab != null) tab.close();
                    tab = null;
                    server = null;
                });
    }

    public void reconfigure() {
        UUID packId = null;
        String configured = service.snapshot().config().resourcePackId();
        if (!configured.isEmpty()) packId = UUID.fromString(configured);
        else if (server != null)
            packId =
                    server.getServerResourcePack()
                            .map(MinecraftServer.ServerResourcePackInfo::id)
                            .orElse(null);
        packs.configure(packId);
        if (packId == null)
            LOGGER.info(
                    "Kein Server-Resourcepack konfiguriert: Item-Suffixe verwenden lesbare"
                        + " Text-Fallbacks.");
    }

    public void refreshTab() {
        if (tab != null) tab.refresh();
    }
}
