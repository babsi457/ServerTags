package de.basky.servertags;

import static org.junit.jupiter.api.Assertions.*;

import de.basky.servertags.data.*;
import de.basky.servertags.data.Models.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

class StorageTest {
    @TempDir Path dir;

    TagService service() throws IOException {
        return new TagService(new JsonStore(dir));
    }

    @Test
    void defaultsIncludeFiveTagsAndThirtyTwoUniqueIcons() throws Exception {
        var s = service().snapshot();
        assertEquals(5, s.tags().size());
        assertEquals(32, s.icons().size());
        assertEquals(0xE000, s.icons().get("netherite_ingot").codepoint());
        assertEquals(0xE001, s.icons().get("diamond").codepoint());
        assertEquals(32, s.icons().values().stream().map(Icon::codepoint).distinct().count());
    }

    @Test
    void existingCatalogGainsNewIconsWithoutOverwritingCustomizations() throws Exception {
        var store = new JsonStore(dir);
        var old = service();
        Map<String, Icon> icons = new LinkedHashMap<>(old.snapshot().icons());
        icons.remove("wheat");
        icons.remove("dragon_egg");
        Icon custom = new Icon(0xE000, "minecraft:item/netherite_ingot.png", "Eigener Text");
        icons.put("netherite_ingot", custom);
        store.write("icons.json", icons);
        var upgraded = service().snapshot();
        assertEquals(32, upgraded.icons().size());
        assertEquals(custom, upgraded.icons().get("netherite_ingot"));
        assertTrue(Files.readString(dir.resolve("icons.json")).contains("dragon_egg"));
    }

    @Test
    void customCodepointCollisionIsPreservedAndConflictingDefaultSkipped() throws Exception {
        var store = new JsonStore(dir);
        var s = service().snapshot();
        Map<String, Icon> icons = new LinkedHashMap<>(s.icons());
        Icon wheat = icons.remove("wheat");
        icons.put("custom", new Icon(wheat.codepoint(), wheat.texture(), "Eigenes Icon"));
        store.write("icons.json", icons);
        var upgraded = service().snapshot();
        assertFalse(upgraded.icons().containsKey("wheat"));
        assertTrue(upgraded.icons().containsKey("custom"));
    }

    @Test
    void survivesRestartAndClearKeepsAward() throws Exception {
        UUID id = UUID.randomUUID();
        var a = service();
        a.select(id, "builder");
        a.suffix(id, "minecraft:netherite_ingot");
        var b = service();
        assertEquals("builder", b.snapshot().player(id).prefix());
        assertEquals(new Suffix("icon", "netherite_ingot"), b.snapshot().player(id).suffix());
        b.clear(id);
        assertNotNull(service().snapshot().player(id).suffix());
    }

    @Test
    void onlyOnePrefixAndDeletingNeverResurrectsSelection() throws Exception {
        UUID id = UUID.randomUUID();
        var a = service();
        a.select(id, "builder");
        a.select(id, "miner");
        assertEquals("miner", a.snapshot().player(id).prefix());
        a.delete("miner");
        a.create("miner", "Neu");
        assertNull(service().snapshot().player(id).prefix());
    }

    @Test
    void literalTextCanMatchAnIconId() throws Exception {
        var a = service();
        UUID id = UUID.randomUUID();
        a.suffix(id, "text:diamond");
        assertEquals(new Suffix("text", "diamond"), a.snapshot().player(id).suffix());
    }

    @Test
    void malformedReloadPreservesLiveDataAndFile() throws Exception {
        var a = service();
        Files.writeString(dir.resolve("tags.json"), "{broken");
        assertThrows(IOException.class, a::reload);
        assertEquals(5, a.snapshot().tags().size());
        assertEquals("{broken", Files.readString(dir.resolve("tags.json")));
    }

    @Test
    void duplicateGlyphAndBadColorAreRejected() throws Exception {
        var a = service();
        String icons = Files.readString(dir.resolve("icons.json"));
        Files.writeString(dir.resolve("icons.json"), icons.replace("57345", "57344"));
        assertThrows(IOException.class, a::reload);
        assertThrows(IllegalArgumentException.class, () -> a.color("builder", "rainbow"));
    }

    @Test
    void unknownTagAndControlCharactersCannotMutateData() throws Exception {
        var a = service();
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> a.select(id, "missing"));
        assertThrows(IllegalArgumentException.class, () -> a.create("../oops", "Text"));
        assertThrows(IllegalArgumentException.class, () -> a.suffix(id, "Bad\nText"));
        assertTrue(a.snapshot().players().isEmpty());
    }

    @Test
    void ioFailureDoesNotPublishUnsavedChanges() throws Exception {
        var a = service();
        UUID id = UUID.randomUUID();
        Files.createDirectory(dir.resolve("players.json.bak"));
        Files.writeString(dir.resolve("players.json.bak/block"), "x");
        assertThrows(IOException.class, () -> a.select(id, "builder"));
        assertNull(a.snapshot().player(id).prefix());
    }

    @Test
    void oldVersionsAreRejected() throws Exception {
        service();
        Files.writeString(
                dir.resolve("config.json"), "{\"schemaVersion\":2,\"resourcePackId\":\"\"}");
        assertThrows(IOException.class, this::service);
    }

    @Test
    void oldTagFilesRemainPublicAndNoNewTagsAreAdded() throws Exception {
        var a = service();
        Files.writeString(
                dir.resolve("tags.json"),
                "{\"custom\":{\"text\":\"Eigener"
                    + " Tag\",\"color\":\"gold\",\"bold\":false,\"italic\":false}}");
        a.reload();
        assertEquals(Set.of("custom"), a.snapshot().tags().keySet());
        assertFalse(a.requireTag("custom").adminOnly());
        UUID id = UUID.randomUUID();
        a.select(id, "custom");
        assertEquals("custom", a.snapshot().player(id).prefix());
    }

    @Test
    void restrictedTagRequiresAdminAssignmentButRecipientCanAlwaysClearIt() throws Exception {
        var a = service();
        UUID id = UUID.randomUUID();
        a.suffix(id, "diamond");
        a.setAdminOnly("builder", true);
        assertThrows(IllegalArgumentException.class, () -> a.select(id, "builder"));
        assertNull(a.snapshot().player(id).prefix());
        a.assignByAdmin(id, "builder");
        assertEquals("builder", a.snapshot().player(id).prefix());
        a.clear(id);
        assertNull(a.snapshot().player(id).prefix());
        assertEquals(new Suffix("icon", "diamond"), a.snapshot().player(id).suffix());
        assertThrows(IllegalArgumentException.class, () -> a.select(id, "builder"));
    }

    @Test
    void restrictionSurvivesRestartRenameAndColorAndCanBeUnlocked() throws Exception {
        var a = service();
        a.setAdminOnly("builder", true);
        a.rename("builder", "Baumeister");
        a.color("builder", "gold");
        var b = service();
        assertTrue(b.requireTag("builder").adminOnly());
        assertEquals("Baumeister", b.requireTag("builder").text());
        assertEquals("gold", b.requireTag("builder").color());
        b.setAdminOnly("builder", false);
        var c = service();
        assertFalse(c.requireTag("builder").adminOnly());
        UUID id = UUID.randomUUID();
        c.select(id, "builder");
        assertEquals("builder", c.snapshot().player(id).prefix());
    }

    @Test
    void lockingKeepsExistingAssignmentsAndRejectedSelectionKeepsCurrentTag() throws Exception {
        var a = service();
        UUID id = UUID.randomUUID();
        a.select(id, "builder");
        a.setAdminOnly("builder", true);
        assertEquals("builder", a.snapshot().player(id).prefix());
        a.select(id, "miner");
        assertThrows(IllegalArgumentException.class, () -> a.select(id, "builder"));
        assertEquals("miner", a.snapshot().player(id).prefix());
    }
}
