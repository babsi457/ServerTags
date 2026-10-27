package de.basky.servertags.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import de.basky.servertags.*;
import de.basky.servertags.command.OpPermissions;
import de.basky.servertags.data.Models.*;
import de.basky.servertags.tab.TabIntegration;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;

import me.neznamy.tab.api.*;
import me.neznamy.tab.platforms.fabric.*;
import me.neznamy.tab.shared.TAB;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.server.permissions.*;

import java.nio.file.*;
import java.util.*;

/** Runs only from its separate test JAR on an isolated, disposable server. */
public final class ServerIntegrationTests implements ModInitializer {
    private int ticks;
    private static final UUID PLAYER = UUID.fromString("48dbb430-971f-4025-803c-957457aef214");
    private final List<String> results = new ArrayList<>();
    private FabricTabPlayer tabPlayer;
    private ServerPlayer fakePlayer;
    private RecordingConnection fakeConnection;
    private final Map<Path, byte[]> tabFiles = new HashMap<>();
    private boolean finished;

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {
                    if (finished) return;
                    ++ticks;
                    try {
                        if (ticks == 60) {
                            run(server);
                            for (String name : List.of("config.yml", "groups.yml", "users.yml")) {
                                Path file = Path.of("config/tab", name);
                                tabFiles.put(file, Files.readAllBytes(file));
                            }
                            joinTab();
                        }
                        if (ticks == 90) {
                            checkTab("PlayerLoadEvent");
                            TAB.getInstance().getFeatureManager().onQuit(tabPlayer);
                            joinTab();
                        }
                        if (ticks == 120) {
                            checkTab("simulierter Rejoin");
                            TAB.getInstance().getFeatureManager().onQuit(tabPlayer);
                            server.getCommands()
                                    .getDispatcher()
                                    .execute("tab reload", server.createCommandSourceStack());
                        }
                        if (ticks == 180) {
                            check(
                                    TabAPI.getInstance()
                                                    .getPlaceholderManager()
                                                    .getPlaceholder(TabIntegration.PREFIX)
                                            != null,
                                    "Echter /tab reload registriert ServerTags-Platzhalter erneut");
                            joinTab();
                        }
                        if (ticks == 210) {
                            checkTab("nach echtem TAB-Reload");
                            TAB.getInstance().getFeatureManager().onQuit(tabPlayer);
                            for (var entry : tabFiles.entrySet())
                                check(
                                        Arrays.equals(
                                                entry.getValue(),
                                                Files.readAllBytes(entry.getKey())),
                                        "TAB-Datei unverändert: " + entry.getKey().getFileName());
                            results.add("PASS ALL SERVER INTEGRATION TESTS");
                            finish(server);
                        }
                    } catch (Throwable e) {
                        results.add("FAIL " + e);
                        ServerTags.LOGGER.error("SERVER INTEGRATION TEST FAILED", e);
                        finish(server);
                    }
                });
    }

    void finish(MinecraftServer server) {
        finished = true;
        try {
            Files.write(Path.of("integration-results.txt"), results);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        results.forEach(ServerTags.LOGGER::info);
        server.halt(false);
    }

    void joinTab() {
        tabPlayer =
                new FabricTabPlayer((FabricPlatform) TAB.getInstance().getPlatform(), fakePlayer);
        if (tabPlayer.getTabListEntryTracker() instanceof FabricTabListEntryTracker tracker)
            tracker.onPacketSend(
                    ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(
                            List.of(fakePlayer)));
        TAB.getInstance().getFeatureManager().onJoin(tabPlayer);
    }

    void checkTab(String stage) {
        var manager = TabAPI.getInstance().getTabListFormatManager();
        check(tabPlayer.isLoaded(), "TAB-Spieler geladen: " + stage);
        String prefix = manager.getCustomPrefix(tabPlayer),
                suffix = manager.getCustomSuffix(tabPlayer);
        check(
                prefix != null && prefix.contains(TabIntegration.PREFIX),
                "TAB-setPrefix angewendet: " + stage);
        check(
                suffix != null && suffix.contains(TabIntegration.SUFFIX),
                "TAB-setSuffix angewendet: " + stage);
        check(
                prefix.indexOf(TabIntegration.PREFIX) == prefix.lastIndexOf(TabIntegration.PREFIX),
                "TAB-Präfix ohne Duplikat: " + stage);
        check(
                fakeConnection.tabName != null
                        && fakeConnection.tabName.getString().contains("[Builder]")
                        && fakeConnection.tabName.getString().contains("\uE000"),
                "TAB-Paket enthält Präfix und echte Item-Glyphe: " + stage);
        check(
                fakeConnection
                        .tabName
                        .visit(
                                (style, text) ->
                                        text.contains("\uE000")
                                                ? Optional.of(
                                                        style.getFont()
                                                                .equals(
                                                                        de.basky.servertags.icon
                                                                                .NameFormatter
                                                                                .FONT))
                                                : Optional.<Boolean>empty(),
                                Style.EMPTY)
                        .orElse(false),
                "TAB-Glyphe verwendet explizit servertags:icons: " + stage);
    }

    void run(MinecraftServer server) throws Exception {
        var mod = ServerTags.instance();
        var service = mod.service();
        var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        for (var dependency :
                Map.of(
                                "fabricloader",
                                "servertags.test.loader",
                                "fabric-api",
                                "servertags.test.fabricApi")
                        .entrySet()) {
            String actual =
                    loader.getModContainer(dependency.getKey())
                            .orElseThrow()
                            .getMetadata()
                            .getVersion()
                            .getFriendlyString();
            String expected = System.getProperty(dependency.getValue());
            check(
                    expected == null || expected.equals(actual),
                    "Tatsächliche Laufzeitversion " + dependency.getKey() + ": " + actual);
        }
        if (service.snapshot().player(PLAYER).prefix() != null)
            check(
                    "builder".equals(service.snapshot().player(PLAYER).prefix()),
                    "Spielerdaten nach echtem Serverneustart geladen");
        service.clear(PLAYER);
        service.clearSuffix(PLAYER);
        if (service.snapshot().tags().containsKey("test_admin")) service.delete("test_admin");
        var dispatcher = server.getCommands().getDispatcher();
        var console = server.createCommandSourceStack();
        var profile = new GameProfile(PLAYER, "TestSpieler");
        var fake =
                new ServerPlayer(
                        server, server.overworld(), profile, ClientInformation.createDefault());
        fakePlayer = fake;
        server.getPlayerList().getPlayersByUUID().put(PLAYER, fake);
        var source = console.withEntity(fake).withPermission(PermissionSet.NO_PERMISSIONS);
        var op = source.withPermission(LevelBasedPermissionSet.GAMEMASTER);
        check(
                !OpPermissions.admin(source)
                        && OpPermissions.admin(op)
                        && OpPermissions.admin(console),
                "Native OP-Rechte und Konsole");
        check(
                dispatcher.getRoot().getChild("tag").canUse(source),
                "Normaler Spieler kann /tag verwenden");
        check(
                !dispatcher.getRoot().getChild("tag").getChild("admin").canUse(source),
                "Admin-Zweig vor Nicht-OPs verborgen");
        check(dispatcher.execute("tag", source) == 1, "Klickmenü wird ausgegeben");
        try {
            dispatcher.execute("tag admin create verboten Verboten", source);
            throw new AssertionError("Unberechtigte Erstellung möglich");
        } catch (CommandSyntaxException expected) {
            check(
                    !service.snapshot().tags().containsKey("verboten"),
                    "Nicht-OP kann keinen Tag erstellen");
        }
        try {
            dispatcher.execute("tag admin suffix set " + PLAYER + " diamond", source);
            throw new AssertionError("Unberechtigte Auszeichnung möglich");
        } catch (CommandSyntaxException expected) {
            check(
                    service.snapshot().player(PLAYER).suffix() == null,
                    "Nicht-OP kann keinen Suffix vergeben");
        }
        check(
                dispatcher.execute("tag admin create test_admin Test-Admin", op) == 1,
                "OP kann Tags erstellen");
        check(
                dispatcher.execute("tag select builder", source) == 1,
                "Normaler Spieler wählt Präfix");
        check(
                dispatcher.execute("tag admin suffix set " + PLAYER + " netherite_ingot", console)
                        == 1,
                "Konsole vergibt Icon");
        check(
                service.snapshot()
                        .player(PLAYER)
                        .suffix()
                        .equals(new Suffix("icon", "netherite_ingot")),
                "Item-ID korrekt gespeichert");
        check(
                dispatcher.execute("tag clear", source) == 1
                        && service.snapshot().player(PLAYER).suffix() != null,
                "Spieler-clear behält OP-Auszeichnung");
        dispatcher.execute("tag select builder", source);
        try {
            dispatcher.execute("tag admin lock test_admin", source);
            throw new AssertionError("Nicht-OP konnte Tag sperren");
        } catch (CommandSyntaxException expected) {
            check(
                    !service.requireTag("test_admin").adminOnly(),
                    "Nicht-OP kann Tags nicht sperren");
        }
        check(
                dispatcher.execute("tag admin lock test_admin", op) == 1
                        && service.requireTag("test_admin").adminOnly(),
                "OP kann bestehenden Tag für die Selbstauswahl sperren");
        check(
                dispatcher.execute("tag select test_admin", source) == 0
                        && "builder".equals(service.snapshot().player(PLAYER).prefix()),
                "Direkte Auswahl gesperrter Tags scheitert und behält bisherigen Präfix");
        var lockedSuggestions =
                dispatcher
                        .getCompletionSuggestions(dispatcher.parse("tag select test_", source))
                        .join();
        check(
                lockedSuggestions.getList().stream()
                        .noneMatch(s -> s.getText().equals("test_admin")),
                "Gesperrte Tags fehlen in der Selbstauswahl-Completion");
        var adminSuggestions =
                dispatcher
                        .getCompletionSuggestions(
                                dispatcher.parse("tag admin set " + PLAYER + " test_", console))
                        .join();
        check(
                adminSuggestions.getList().stream().anyMatch(s -> s.getText().equals("test_admin")),
                "Gesperrte Tags bleiben in der Admin-Vergabe-Completion");
        check(
                dispatcher.execute("tag admin set " + PLAYER + " test_admin", console) == 1
                        && "test_admin".equals(service.snapshot().player(PLAYER).prefix()),
                "Konsole kann gesperrten Tag einem Nicht-OP zuweisen");
        service.reload();
        check(
                service.requireTag("test_admin").adminOnly()
                        && "test_admin".equals(service.snapshot().player(PLAYER).prefix()),
                "Vergabesperre und zugewiesener Tag überstehen Reload");
        check(
                dispatcher.execute("tag clear", source) == 1
                        && service.snapshot().player(PLAYER).prefix() == null
                        && service.snapshot().player(PLAYER).suffix() != null,
                "Nicht-OP kann gesperrten Tag selbst ablegen und behält Auszeichnung");
        check(
                dispatcher.execute("tag select test_admin", source) == 0
                        && service.snapshot().player(PLAYER).prefix() == null,
                "Nach Ablegen lässt sich gesperrter Tag nicht selbst erneut auswählen");
        try {
            dispatcher.execute("tag admin unlock test_admin", source);
            throw new AssertionError("Nicht-OP konnte Tag freigeben");
        } catch (CommandSyntaxException expected) {
            check(
                    service.requireTag("test_admin").adminOnly(),
                    "Nicht-OP kann Tags nicht freigeben");
        }
        check(
                dispatcher.execute("tag admin unlock test_admin", op) == 1
                        && !service.requireTag("test_admin").adminOnly(),
                "OP kann Tag wieder für alle freigeben");
        check(
                dispatcher.execute("tag select test_admin", source) == 1,
                "Freigegebener Tag ist wieder frei wählbar");
        dispatcher.execute("tag select builder", source);
        var suggestions =
                dispatcher
                        .getCompletionSuggestions(dispatcher.parse("tag select b", source))
                        .join();
        check(
                suggestions.getList().stream().anyMatch(s -> s.getText().equals("builder")),
                "Tag-Completion");
        var icons =
                dispatcher
                        .getCompletionSuggestions(
                                dispatcher.parse(
                                        "tag admin suffix set " + PLAYER + " net", console))
                        .join();
        check(
                icons.getList().stream().anyMatch(s -> s.getText().equals("netherite_ingot")),
                "Icon-Completion");
        check(
                dispatcher.execute("entitytag @s add servertags_probe", op) == 1
                        && fake.entityTags().contains("servertags_probe"),
                "Vanilla-Entitytag add funktioniert");
        check(
                dispatcher.execute("entitytag @s list", op) >= 0,
                "Vanilla-Entitytag list funktioniert");
        check(
                dispatcher.execute("entitytag @s remove servertags_probe", op) == 1
                        && !fake.entityTags().contains("servertags_probe"),
                "Vanilla-Entitytag remove funktioniert");
        var api = TabAPI.getInstance();
        check(
                api.getTabListFormatManager() != null && api.getNameTagManager() != null,
                "Echte TAB-6.2.1-Manager verfügbar");
        check(
                api.getPlaceholderManager().getPlaceholder(TabIntegration.PREFIX) != null
                        && api.getPlaceholderManager().getPlaceholder(TabIntegration.SUFFIX)
                                != null,
                "Eigene TAB-Platzhalter registriert");
        check(
                TabIntegration.append("&aAlt ", TabIntegration.PREFIX)
                        .equals("&aAlt " + TabIntegration.PREFIX),
                "Bestehender TAB-Präfix bleibt erhalten");
        var once = TabIntegration.append("Alt ", TabIntegration.PREFIX);
        check(
                TabIntegration.append(once, TabIntegration.PREFIX).equals(once),
                "Keine doppelten TAB-Platzhalter");
        // Simulate a player lookup and inspect the actual packet generated by the injected Vanilla
        // method.
        server.getPlayerList().getPlayersByUUID().put(PLAYER, fake);
        RecordingConnection connection = new RecordingConnection();
        fakeConnection = connection;
        var listener =
                new ServerGamePacketListenerImpl(
                        server,
                        connection,
                        fake,
                        new CommonListenerCookie(
                                profile, 0, ClientInformation.createDefault(), false));
        fake.connection = listener;
        var bound = ChatType.bind(ChatType.CHAT, server.registryAccess(), fake.getName());
        var unsigned = PlayerChatMessage.unsigned(PLAYER, "Unverändert signierter Inhalt");
        var signature = new MessageSignature(new byte[256]);
        var message =
                new PlayerChatMessage(
                        unsigned.link(),
                        signature,
                        unsigned.signedBody(),
                        null,
                        unsigned.filterMask());
        UUID pack = UUID.randomUUID();
        mod.packs().configure(pack);
        listener.sendPlayerChatMessage(message, bound);
        var fallback = connection.chat;
        check(
                fallback != null
                        && fallback.chatType()
                                .name()
                                .getString()
                                .equals("[Builder] TestSpieler [Netherite-Barren]"),
                "Chatname: Präfix, genau ein Name, Text-Fallback");
        check(
                fallback.signature().orElseThrow().equals(signature)
                        && fallback.body().content().equals(message.signedBody().content())
                        && fallback.unsignedContent().isEmpty(),
                "Chat-Signatur, Body und unsignedContent bleiben unverändert");
        listener.handleResourcePackResponse(
                new ServerboundResourcePackPacket(
                        pack, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        check(mod.packs().loaded(PLAYER), "Resourcepack-Mixin erfasst erfolgreichen Ladezustand");
        listener.sendPlayerChatMessage(message, bound);
        check(
                connection.chat.chatType().name().getString().contains("\uE000")
                        && !connection
                                .chat
                                .chatType()
                                .name()
                                .getString()
                                .contains("Netherite-Barren"),
                "Chat verwendet echte Icon-Glyphe nach Pack-Bestätigung");
        check(
                connection.chat.sender().equals(PLAYER) && connection.sent == 2,
                "Genau ein Paket je Nachricht, echte UUID unverändert");
        service.reload();
        check(
                service.snapshot().player(PLAYER).prefix().equals("builder")
                        && service.snapshot()
                                .player(PLAYER)
                                .suffix()
                                .value()
                                .equals("netherite_ingot"),
                "Persistenz nach erneutem Laden");
        server.getPlayerList().getPlayersByUUID().remove(PLAYER);
        dispatcher.execute("tag admin delete test_admin", console);
    }

    void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        results.add("PASS " + label);
    }

    static final class RecordingConnection extends Connection {
        ClientboundPlayerChatPacket chat;
        int sent;
        volatile Component tabName;

        RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel();
            channel.pipeline().addLast("packet_handler", this);
            try {
                channelActive(channel.pipeline().context(this));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void send(Packet<?> packet) {
            capture(packet);
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener listener) {
            capture(packet);
        }

        @Override
        public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
            capture(packet);
        }

        private void capture(Packet<?> packet) {
            if (packet instanceof ClientboundPlayerChatPacket p) {
                chat = p;
                sent++;
            }
            if (packet instanceof ClientboundPlayerInfoUpdatePacket p
                    && p.actions()
                            .contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME))
                for (var e : p.entries())
                    if (e.profileId().equals(PLAYER) && e.displayName() != null)
                        tabName = e.displayName();
        }
    }
}
