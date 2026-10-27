package de.basky.servertags.data;

import static de.basky.servertags.data.Models.*;

import java.io.IOException;
import java.util.*;
import java.util.function.UnaryOperator;

/** Publishes saved snapshots to TAB's asynchronous placeholder readers. */
public final class TagService {
    private final JsonStore store;
    private volatile Snapshot snapshot;

    public TagService(JsonStore store) throws IOException {
        this.store = store;
        reload();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public synchronized void reload() throws IOException {
        snapshot = store.load();
    }

    public synchronized void create(String id, String text) throws IOException {
        checkId(id);
        if (snapshot.tags().containsKey(id))
            throw new IllegalArgumentException("Diese Tag-ID existiert bereits.");
        if (snapshot.tags().size() >= 200)
            throw new IllegalArgumentException("Maximal 200 Tags erlaubt.");
        saveTag(id, new Tag(text, "aqua", false, false, false));
    }

    public synchronized void rename(String id, String text) throws IOException {
        Tag old = requireTag(id);
        saveTag(id, new Tag(text, old.color(), old.bold(), old.italic(), old.adminOnly()));
    }

    public synchronized void color(String id, String color) throws IOException {
        Tag old = requireTag(id);
        saveTag(
                id,
                new Tag(
                        old.text(),
                        color.toLowerCase(Locale.ROOT),
                        old.bold(),
                        old.italic(),
                        old.adminOnly()));
    }

    public synchronized void setAdminOnly(String id, boolean adminOnly) throws IOException {
        Tag old = requireTag(id);
        saveTag(id, new Tag(old.text(), old.color(), old.bold(), old.italic(), adminOnly));
    }

    public Tag requireTag(String id) {
        Tag tag = snapshot.tags().get(id);
        if (tag == null) throw new IllegalArgumentException("Unbekannter Tag: " + id);
        return tag;
    }

    private void saveTag(String id, Tag tag) throws IOException {
        tag.validate();
        Map<String, Tag> tags = new LinkedHashMap<>(snapshot.tags());
        tags.put(id, tag);
        store.write("tags.json", tags);
        snapshot = new Snapshot(snapshot.config(), tags, snapshot.players(), snapshot.icons());
    }

    public synchronized void delete(String id) throws IOException {
        requireTag(id);
        Map<UUID, PlayerData> players = new HashMap<>(snapshot.players());
        players.replaceAll(
                (uuid, p) -> id.equals(p.prefix()) ? new PlayerData(null, p.suffix()) : p);
        // Remove assignments before the tag so reusing its ID cannot restore an old selection.
        store.write("players.json", players);
        snapshot = new Snapshot(snapshot.config(), snapshot.tags(), players, snapshot.icons());
        Map<String, Tag> tags = new LinkedHashMap<>(snapshot.tags());
        tags.remove(id);
        store.write("tags.json", tags);
        snapshot = new Snapshot(snapshot.config(), tags, players, snapshot.icons());
    }

    public void select(UUID uuid, String id) throws IOException {
        update(
                uuid,
                p -> {
                    if (requireTag(id).adminOnly())
                        throw new IllegalArgumentException(
                                "Diesen Tag darf nur ein OP mit /tag admin set vergeben. Du kannst"
                                    + " ihn jederzeit mit /tag clear ablegen.");
                    return new PlayerData(id, p.suffix());
                });
    }

    /** Called only by the server-side OP-checked administrative command path. */
    public void assignByAdmin(UUID uuid, String id) throws IOException {
        update(
                uuid,
                p -> {
                    requireTag(id);
                    return new PlayerData(id, p.suffix());
                });
    }

    public void clear(UUID uuid) throws IOException {
        update(uuid, p -> new PlayerData(null, p.suffix()));
    }

    public void suffix(UUID uuid, String value) throws IOException {
        Suffix suffix = SuffixService.parse(value, snapshot.icons());
        update(
                uuid,
                p -> {
                    suffix.validate(snapshot.icons());
                    return new PlayerData(p.prefix(), suffix);
                });
    }

    public void clearSuffix(UUID uuid) throws IOException {
        update(uuid, p -> new PlayerData(p.prefix(), null));
    }

    private synchronized void update(UUID uuid, UnaryOperator<PlayerData> change)
            throws IOException {
        Map<UUID, PlayerData> players = new HashMap<>(snapshot.players());
        PlayerData next = change.apply(snapshot.player(uuid));
        if (next.prefix() == null && next.suffix() == null) players.remove(uuid);
        else players.put(uuid, next);
        store.write("players.json", players);
        snapshot = new Snapshot(snapshot.config(), snapshot.tags(), players, snapshot.icons());
    }
}
