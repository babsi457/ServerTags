package de.basky.servertags.data;

import static de.basky.servertags.data.Models.*;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import de.basky.servertags.icon.IconRegistry;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Validates configuration before loading it and keeps a backup on writes. */
public final class JsonStore {
    private static final Gson GSON =
            new GsonBuilder()
                    .setStrictness(Strictness.STRICT)
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create();
    private final Path directory;

    public JsonStore(Path directory) {
        this.directory = directory;
    }

    public Snapshot load() throws IOException {
        Files.createDirectories(directory);
        for (String name : List.of("config.json", "tags.json", "players.json", "icons.json")) {
            if (!Files.exists(directory.resolve(name))) {
                try (InputStream in = JsonStore.class.getResourceAsStream("/defaults/" + name)) {
                    if (in == null) throw new IOException("Standardkonfiguration fehlt: " + name);
                    writeBytes(name, in.readAllBytes());
                }
            }
        }
        try {
            Config config = read("config.json", Config.class);
            Map<String, Tag> tags =
                    read("tags.json", new TypeToken<LinkedHashMap<String, Tag>>() {}.getType());
            Map<String, Icon> icons =
                    read("icons.json", new TypeToken<LinkedHashMap<String, Icon>>() {}.getType());
            Map<UUID, PlayerData> players =
                    read(
                            "players.json",
                            new TypeToken<LinkedHashMap<UUID, PlayerData>>() {}.getType());
            config.validate();
            if (tags.size() > 200 || icons.size() > 512)
                throw new IllegalArgumentException("Zu viele Tags oder Icons.");
            tags.forEach(
                    (id, tag) -> {
                        checkId(id);
                        Objects.requireNonNull(tag).validate();
                    });
            IconRegistry.validate(icons);
            boolean addedIcons = addMissingDefaultIcons(icons);
            IconRegistry.validate(icons);
            players.forEach(
                    (id, player) -> {
                        Objects.requireNonNull(id);
                        Objects.requireNonNull(player);
                        if (player.prefix() != null) checkId(player.prefix());
                        if (player.suffix() != null) player.suffix().validate(icons);
                    });
            if (addedIcons) write("icons.json", icons);
            return new Snapshot(config, tags, players, icons);
        } catch (RuntimeException e) {
            throw new IOException("Ungültige ServerTags-Konfiguration: " + e.getMessage(), e);
        }
    }

    private boolean addMissingDefaultIcons(Map<String, Icon> icons) throws IOException {
        try (InputStream stream = JsonStore.class.getResourceAsStream("/defaults/icons.json")) {
            if (stream == null) throw new IOException("Standard-Icons fehlen.");
            Map<String, Icon> defaults =
                    GSON.fromJson(
                            new InputStreamReader(stream, StandardCharsets.UTF_8),
                            new TypeToken<LinkedHashMap<String, Icon>>() {}.getType());
            IconRegistry.validate(defaults);
            Set<Integer> used = new HashSet<>();
            icons.values().forEach(icon -> used.add(icon.codepoint()));
            boolean changed = false;
            for (var entry : defaults.entrySet()) {
                if (icons.containsKey(entry.getKey())) continue;
                if (!used.add(entry.getValue().codepoint())) {
                    org.slf4j.LoggerFactory.getLogger("ServerTags")
                            .warn(
                                    "Standard-Icon {} nicht ergänzt: Codepoint bereits durch ein"
                                        + " eigenes Icon belegt.",
                                    entry.getKey());
                    continue;
                }
                icons.put(entry.getKey(), entry.getValue());
                changed = true;
            }
            return changed;
        }
    }

    private <T> T read(String name, Type type) throws IOException {
        try (Reader reader =
                Files.newBufferedReader(directory.resolve(name), StandardCharsets.UTF_8)) {
            T value = GSON.fromJson(reader, type);
            if (value == null) throw new IOException(name + " ist leer.");
            return value;
        }
    }

    public void write(String name, Object value) throws IOException {
        writeBytes(name, (GSON.toJson(value) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(String name, byte[] bytes) throws IOException {
        Path target = directory.resolve(name);
        Path temporary = Files.createTempFile(directory, name, ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.exists(target))
                Files.copy(
                        target,
                        directory.resolve(name + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
