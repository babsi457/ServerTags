package de.basky.servertags.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import de.basky.servertags.ServerTags;
import de.basky.servertags.data.Models.Suffix;
import de.basky.servertags.data.Models.Tag;
import de.basky.servertags.data.TagService;
import de.basky.servertags.icon.NameFormatter;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class TagCommands {
    private static final List<String> COLORS =
            List.of("aqua", "green", "red", "gold", "yellow", "white", "light_purple", "#55FFFF");

    private final ServerTags mod;
    private final TagService service;

    public TagCommands(ServerTags mod) {
        this.mod = mod;
        this.service = mod.service();
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        VanillaTagAlias.move(dispatcher);
        var root = literal("tag").executes(context -> menu(context.getSource()));
        root.then(literal("list").executes(context -> menu(context.getSource())));
        root.then(literal("help").executes(context -> help(context.getSource())));
        root.then(literal("select").then(tagId(true).executes(this::select)));
        root.then(literal("clear").executes(this::clear));
        root.then(adminCommands());
        dispatcher.register(root);
        verifyCommandTree(dispatcher);
        ServerTags.LOGGER.info(
                "Befehle geprüft: /tag für Spieler; /tag admin nur OP-Level 2+; Vanilla unter /entitytag.");
    }

    private LiteralArgumentBuilder<CommandSourceStack> adminCommands() {
        var admin =
                literal("admin")
                        .requires(OpPermissions::admin)
                        .executes(context -> help(context.getSource()));
        var createText = text("text").executes(this::create);
        var renameText = text("text").executes(this::rename);
        var color =
                argument("farbe", StringArgumentType.word())
                        .suggests(
                                (context, builder) ->
                                        SharedSuggestionProvider.suggest(COLORS, builder))
                        .executes(this::color);

        admin.then(
                literal("create").then(argument("id", StringArgumentType.word()).then(createText)));
        admin.then(literal("delete").then(tagId(false).executes(this::delete)));
        admin.then(literal("rename").then(tagId(false).then(renameText)));
        admin.then(literal("color").then(tagId(false).then(color)));
        admin.then(
                literal("lock").then(tagId(false).executes(context -> setLocked(context, true))));
        admin.then(
                literal("unlock")
                        .then(tagId(false).executes(context -> setLocked(context, false))));
        admin.then(literal("list").executes(this::adminList));
        admin.then(literal("set").then(player().then(tagId(false).executes(this::assign))));
        admin.then(literal("unset").then(player().executes(this::unset)));
        admin.then(literal("reload").executes(this::reload));
        admin.then(literal("icons").executes(this::icons));
        admin.then(suffixCommands());
        return admin;
    }

    private LiteralArgumentBuilder<CommandSourceStack> suffixCommands() {
        var value =
                text("wert")
                        .suggests(
                                (context, builder) ->
                                        SharedSuggestionProvider.suggest(
                                                service.snapshot().icons().keySet(), builder))
                        .executes(this::setSuffix);
        return literal("suffix")
                .then(literal("set").then(player().then(value)))
                .then(literal("clear").then(player().executes(this::clearSuffix)))
                .then(literal("get").then(player().executes(this::getSuffix)));
    }

    private int select(CommandContext<CommandSourceStack> context) {
        return playerAction(
                context,
                player -> service.select(player.getUUID(), value(context, "id")),
                "Tag ausgewählt.");
    }

    private int clear(CommandContext<CommandSourceStack> context) {
        return playerAction(
                context,
                player -> service.clear(player.getUUID()),
                "Präfix entfernt. Deine Auszeichnung bleibt erhalten.");
    }

    private int create(CommandContext<CommandSourceStack> context) {
        return admin(
                context,
                () -> service.create(value(context, "id"), value(context, "text")),
                "Tag erstellt.");
    }

    private int delete(CommandContext<CommandSourceStack> context) {
        return admin(
                context,
                () -> service.delete(value(context, "id")),
                "Tag und alle zugehörigen Auswahlen gelöscht.");
    }

    private int rename(CommandContext<CommandSourceStack> context) {
        return admin(
                context,
                () -> service.rename(value(context, "id"), value(context, "text")),
                "Tag umbenannt.");
    }

    private int color(CommandContext<CommandSourceStack> context) {
        return admin(
                context,
                () -> service.color(value(context, "id"), value(context, "farbe")),
                "Farbe gespeichert.");
    }

    private int setLocked(CommandContext<CommandSourceStack> context, boolean locked) {
        String message =
                locked
                        ? "Tag für die Selbstauswahl gesperrt. Nur OPs können ihn vergeben; vorhandene Zuweisungen bleiben erhalten."
                        : "Tag freigegeben. Alle Spieler können ihn wieder selbst auswählen.";
        return admin(context, () -> service.setAdminOnly(value(context, "id"), locked), message);
    }

    private int adminList(CommandContext<CommandSourceStack> context) {
        return OpPermissions.admin(context.getSource())
                ? menu(context.getSource())
                : denied(context.getSource());
    }

    private int assign(CommandContext<CommandSourceStack> context) {
        return admin(
                context,
                () -> service.assignByAdmin(target(context), value(context, "id")),
                "Präfix zugewiesen.");
    }

    private int unset(CommandContext<CommandSourceStack> context) {
        return admin(context, () -> service.clear(target(context)), "Präfix entfernt.");
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        return admin(
                context,
                () -> {
                    service.reload();
                    mod.reconfigure();
                },
                "Konfiguration geprüft und neu geladen.");
    }

    private int setSuffix(CommandContext<CommandSourceStack> context) {
        return admin(
                context,
                () -> service.suffix(target(context), value(context, "wert")),
                "Auszeichnung gespeichert.");
    }

    private int clearSuffix(CommandContext<CommandSourceStack> context) {
        return admin(context, () -> service.clearSuffix(target(context)), "Auszeichnung entfernt.");
    }

    private int getSuffix(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!OpPermissions.admin(source)) return denied(source);
        return safe(
                source,
                () -> {
                    Suffix suffix = service.snapshot().player(target(context)).suffix();
                    Component message =
                            suffix == null
                                    ? Component.literal("Keine Auszeichnung gesetzt.")
                                    : Component.literal(
                                                    "Auszeichnung ("
                                                            + suffix.kind()
                                                            + ": "
                                                            + suffix.value()
                                                            + "): ")
                                            .append(mod.formatter().suffix(suffix, viewer(source)));
                    send(source, message);
                });
    }

    private int icons(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!OpPermissions.admin(source)) return denied(source);
        send(
                source,
                Component.literal("Minecraft-Icons (mit geladenem Resourcepack als Symbol):")
                        .withStyle(ChatFormatting.GOLD));
        for (String id : service.snapshot().icons().keySet()) {
            send(
                    source,
                    Component.literal(id + "  ")
                            .withStyle(ChatFormatting.AQUA)
                            .append(
                                    mod.formatter()
                                            .suffix(new Suffix("icon", id), viewer(source))));
        }
        return 1;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> tagId(boolean selectableOnly) {
        return argument("id", StringArgumentType.word())
                .suggests(
                        (context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        service.snapshot().tags().entrySet().stream()
                                                .filter(
                                                        entry ->
                                                                !selectableOnly
                                                                        || !entry.getValue()
                                                                                .adminOnly())
                                                .map(java.util.Map.Entry::getKey),
                                        builder));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return argument("spieler", StringArgumentType.word())
                .suggests(
                        (context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        Arrays.asList(
                                                context.getSource()
                                                        .getServer()
                                                        .getPlayerList()
                                                        .getPlayerNamesArray()),
                                        builder));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> text(String name) {
        return argument(name, StringArgumentType.greedyString());
    }

    private static String value(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    private UUID target(CommandContext<CommandSourceStack> context) {
        String input = value(context, "spieler");
        ServerPlayer player =
                context.getSource().getServer().getPlayerList().getPlayerByName(input);
        if (player != null) return player.getUUID();
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Spieler ist nicht online. Für Offline-Spieler bitte die echte UUID angeben.");
        }
    }

    private static UUID viewer(CommandSourceStack source) {
        return source.getPlayer() == null ? new UUID(0, 0) : source.getPlayer().getUUID();
    }

    private int menu(CommandSourceStack source) {
        send(
                source,
                Component.literal("SERVER TAGS – Wähle deinen Tag:")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        var snapshot = service.snapshot();
        String active = snapshot.player(viewer(source)).prefix();
        for (var entry : snapshot.tags().entrySet()) {
            send(
                    source,
                    menuEntry(entry.getKey(), entry.getValue(), entry.getKey().equals(active)));
        }
        send(
                source,
                Component.literal("[Präfix entfernen]")
                        .withStyle(
                                style ->
                                        style.withColor(ChatFormatting.GRAY)
                                                .withClickEvent(
                                                        new ClickEvent.RunCommand("/tag clear"))));
        if (snapshot.tags().isEmpty()) {
            send(
                    source,
                    Component.literal("Noch keine Tags vorhanden. Ein OP kann sie erstellen."));
        }
        return 1;
    }

    private static Component menuEntry(String id, Tag tag, boolean active) {
        MutableComponent label = NameFormatter.tag(tag);
        if (tag.adminOnly()) {
            Component hint =
                    Component.literal(
                            "Nur ein OP kann diesen Tag vergeben. Mit /tag clear kannst du deinen Tag jederzeit ablegen.");
            label.withStyle(
                    style ->
                            style.withColor(ChatFormatting.GRAY)
                                    .withHoverEvent(new HoverEvent.ShowText(hint)));
        } else {
            Component hint = Component.literal("Klicken zum Auswählen: " + id);
            label.withStyle(
                    style ->
                            style.withClickEvent(new ClickEvent.RunCommand("/tag select " + id))
                                    .withHoverEvent(new HoverEvent.ShowText(hint)));
        }
        String description = " (" + id + ")" + (tag.adminOnly() ? " – nur OP-Vergabe" : "");
        return Component.literal(active ? "✓ " : "• ")
                .append(label)
                .append(Component.literal(description).withStyle(ChatFormatting.DARK_GRAY));
    }

    private int help(CommandSourceStack source) {
        send(
                source,
                Component.literal("/tag · /tag list · /tag select <id> · /tag clear · /tag help")
                        .withStyle(ChatFormatting.AQUA));
        if (OpPermissions.admin(source)) {
            for (String line :
                    List.of(
                            "/tag admin create <id> <text> | delete <id> | rename <id> <text>",
                            "/tag admin color <id> <farbe> | list | set <spieler> <id> | unset <spieler>",
                            "/tag admin lock <id> | unlock <id> – Selbstauswahl sperren oder freigeben",
                            "/tag admin suffix set <spieler> <item_id oder text> | clear <spieler> | get <spieler>",
                            "/tag admin icons | reload · /entitytag <ziele> add|remove|list …",
                            "Offline-Verwaltung: echte UUID statt Spielername. Text erzwingen: text:diamond")) {
                send(source, Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }
        return 1;
    }

    private int playerAction(
            CommandContext<CommandSourceStack> context, PlayerAction action, String message) {
        CommandSourceStack source = context.getSource();
        if (!source.isPlayer()) {
            source.sendFailure(
                    Component.literal(
                            "Dieser Befehl ist nur im Spiel verfügbar. In der Konsole: /tag admin set <spieler> <id>."));
            return 0;
        }
        return safe(
                source,
                () -> {
                    action.run(source.getPlayerOrException());
                    success(source, message);
                });
    }

    private int admin(CommandContext<CommandSourceStack> context, Action action, String message) {
        CommandSourceStack source = context.getSource();
        if (!OpPermissions.admin(source)) return denied(source);
        return safe(
                source,
                () -> {
                    action.run();
                    success(source, message);
                });
    }

    private int safe(CommandSourceStack source, Action action) {
        try {
            action.run();
            mod.refreshTab();
            return 1;
        } catch (IOException exception) {
            ServerTags.LOGGER.error("ServerTags-Dateizugriff fehlgeschlagen", exception);
            source.sendFailure(
                    Component.literal(
                            "Speichern/Laden fehlgeschlagen. Details im Serverlog; vorhandene Daten bleiben erhalten."));
        } catch (IllegalArgumentException | CommandSyntaxException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
        }
        return 0;
    }

    private static void send(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void success(CommandSourceStack source, String message) {
        send(source, Component.literal(message).withStyle(ChatFormatting.GREEN));
    }

    private static int denied(CommandSourceStack source) {
        source.sendFailure(
                Component.literal("Nur OPs ab Level 2 dürfen Tags und Auszeichnungen verwalten."));
        return 0;
    }

    private static void verifyCommandTree(CommandDispatcher<CommandSourceStack> dispatcher) {
        var tag = dispatcher.getRoot().getChild("tag");
        var vanilla = dispatcher.getRoot().getChild("entitytag");
        if (tag == null
                || tag.getCommand() == null
                || tag.getChild("targets") != null
                || vanilla == null
                || vanilla.getChild("targets") == null) {
            throw new IllegalStateException("ServerTags-Befehlsbaum ist inkonsistent.");
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws IOException, CommandSyntaxException;
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(ServerPlayer player) throws IOException;
    }
}
