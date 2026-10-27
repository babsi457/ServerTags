package de.basky.servertags.pack;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import de.basky.servertags.data.Models.Icon;
import de.basky.servertags.icon.IconRegistry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Builds the bitmap-font resource pack from the icon registry. */
public final class ResourcePackGenerator {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ResourcePackGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Aufruf: ResourcePackGenerator <icons.json> <ausgabe.zip>");
        Map<String, Icon> icons;
        try (Reader reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            icons =
                    GSON.fromJson(
                            reader, new TypeToken<LinkedHashMap<String, Icon>>() {}.getType());
        }
        generate(icons, Path.of(args[1]));
    }

    public static void generate(Map<String, Icon> icons, Path target)
            throws IOException, NoSuchAlgorithmException {
        IconRegistry.validate(icons);
        Files.createDirectories(target.toAbsolutePath().getParent());
        try (ZipOutputStream zip =
                new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            put(
                    zip,
                    "pack.mcmeta",
                    json(
                            Map.of(
                                    "pack",
                                    Map.of(
                                            "description",
                                            "ServerTags · Minecraft-Item-Auszeichnungen · 26.3",
                                            "min_format",
                                            List.of(97, 1),
                                            "max_format",
                                            List.of(97, 1)))));
            try (InputStream icon =
                    ResourcePackGenerator.class.getResourceAsStream("/pack/pack.png")) {
                if (icon == null)
                    throw new IOException("Resourcepack-Vorschaubild pack.png fehlt.");
                put(zip, "pack.png", icon.readAllBytes());
            }
            List<Object> providers = new ArrayList<>();
            for (Icon icon : icons.values())
                providers.add(
                        Map.of(
                                "type",
                                "bitmap",
                                "file",
                                icon.texture(),
                                "ascent",
                                8,
                                "height",
                                9,
                                "chars",
                                List.of(icon.glyph())));
            put(zip, "assets/servertags/font/icons.json", json(Map.of("providers", providers)));
            // Add private-use glyphs alongside the existing Vanilla font providers.
            put(
                    zip,
                    "assets/minecraft/font/default.json",
                    json(
                            Map.of(
                                    "providers",
                                    List.of(
                                            Map.of(
                                                    "type",
                                                    "reference",
                                                    "id",
                                                    "servertags:icons")))));
            Set<String> textures = new TreeSet<>();
            for (Icon icon : icons.values())
                if (icon.texture().startsWith("servertags:"))
                    textures.add(icon.texture().substring(11));
            for (String texture : textures) {
                try (InputStream png =
                        ResourcePackGenerator.class.getResourceAsStream(
                                "/pack/textures/" + texture)) {
                    if (png == null) throw new IOException("Lokale Icon-Textur fehlt: " + texture);
                    put(zip, "assets/servertags/textures/" + texture, png.readAllBytes());
                }
            }
            put(
                    zip,
                    "LIESMICH.txt",
                    "ServerTags 1.1.0 fuer Minecraft Java 26.3\n"
                            + icons.size()
                            + " reine Itemgrafiken ohne Rahmen, Font servertags:icons.\n"
                            + "Vanilla-Items referenzieren die installierten Minecraft-Texturen.\n"
                            + "Blockitems: Projektion lokaler Vanilla-Texturen und -Modelle.\n"
                            + "Minecraft-Texturen (c) Mojang/Microsoft; keine MIT-Lizenz fuer"
                            + " Spielgrafiken.\n"
                            + "Kein offizielles Minecraft-Produkt.\n");
        }
        String sha1 =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-1")
                                        .digest(Files.readAllBytes(target)));
        Files.writeString(
                target.resolveSibling(target.getFileName() + ".sha1"),
                sha1 + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                target.resolveSibling("server-resourcepack.properties.example"),
                "# resource-pack mit der selbst gehosteten, erreichbaren ZIP-URL ausfuellen.\n"
                        + "resource-pack=\nresource-pack-sha1="
                        + sha1
                        + "\n"
                        + "resource-pack-id=9ec9cb9a-a0a0-4e8e-a3bd-c97c81640bac\n"
                        + "require-resource-pack=false\n"
                        + "resource-pack-prompt={\"text\":\"ServerTags:"
                        + " Minecraft-Item-Auszeichnungen laden\"}\n",
                StandardCharsets.UTF_8);
        System.out.println("Resourcepack: " + target.toAbsolutePath() + "\nSHA-1: " + sha1);
    }

    private static void put(ZipOutputStream zip, String name, String value) throws IOException {
        put(zip, name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(Object value) {
        return GSON.toJson(sorted(GSON.toJsonTree(value)));
    }

    private static JsonElement sorted(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().keySet().stream()
                    .sorted()
                    .forEach(key -> result.add(key, sorted(value.getAsJsonObject().get(key))));
            return result;
        }
        if (value.isJsonArray()) {
            JsonArray result = new JsonArray();
            value.getAsJsonArray().forEach(element -> result.add(sorted(element)));
            return result;
        }
        return value;
    }

    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }
}
