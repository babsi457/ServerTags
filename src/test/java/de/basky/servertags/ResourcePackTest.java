package de.basky.servertags;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;

import de.basky.servertags.data.*;
import de.basky.servertags.icon.ResourcePackState;
import de.basky.servertags.pack.ResourcePackGenerator;

import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket.Action;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

class ResourcePackTest {
    @TempDir Path dir;

    @Test
    void releaseMetadataAcceptsExistingServerDependencies() throws Exception {
        try (var input = getClass().getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(input);
            var metadata =
                    JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            var depends = metadata.getAsJsonObject("depends");
            assertEquals(">=0.19.5", depends.get("fabricloader").getAsString());
            assertEquals(">=0.162.0+26.3", depends.get("fabric-api").getAsString());
            assertEquals("1.2.0", metadata.get("version").getAsString());
        }
    }

    @Test
    void packHasCorrectVersionGlyphsTexturesAndFontExtension() throws Exception {
        var service = new TagService(new JsonStore(dir.resolve("data")));
        Path zip = dir.resolve("pack.zip");
        ResourcePackGenerator.generate(service.snapshot().icons(), zip);
        try (ZipFile pack = new ZipFile(zip.toFile())) {
            JsonObject metadata = read(pack, "pack.mcmeta").getAsJsonObject("pack");
            assertEquals(97, metadata.getAsJsonArray("min_format").get(0).getAsInt());
            assertEquals(1, metadata.getAsJsonArray("min_format").get(1).getAsInt());
            JsonArray providers =
                    read(pack, "assets/servertags/font/icons.json").getAsJsonArray("providers");
            assertEquals(32, providers.size());
            assertEquals(
                    "\uE000",
                    providers
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonArray("chars")
                            .get(0)
                            .getAsString());
            assertEquals(
                    "minecraft:item/diamond.png",
                    providers.get(1).getAsJsonObject().get("file").getAsString());
            assertEquals(
                    "reference",
                    read(pack, "assets/minecraft/font/default.json")
                            .getAsJsonArray("providers")
                            .get(0)
                            .getAsJsonObject()
                            .get("type")
                            .getAsString());
            for (JsonElement provider : providers)
                assertTrue(
                        GlyphProviderDefinition.Conditional.CODEC
                                .parse(JsonOps.INSTANCE, provider)
                                .isSuccess(),
                        provider.toString());
            for (JsonElement provider :
                    read(pack, "assets/minecraft/font/default.json").getAsJsonArray("providers"))
                assertTrue(
                        GlyphProviderDefinition.Conditional.CODEC
                                .parse(JsonOps.INSTANCE, provider)
                                .isSuccess(),
                        provider.toString());
            assertNotNull(pack.getEntry("assets/servertags/textures/item/ancient_debris.png"));
        }
        assertEquals(40, Files.readString(dir.resolve("pack.zip.sha1")).trim().length());
        byte[] first = Files.readAllBytes(zip);
        ResourcePackGenerator.generate(service.snapshot().icons(), zip);
        assertArrayEquals(first, Files.readAllBytes(zip));
    }

    @Test
    void fallbackRequiresSuccessfulMatchingPackAndSurvivesConfigReload() {
        var packs = new ResourcePackState();
        UUID player = UUID.randomUUID(), pack = UUID.randomUUID();
        packs.configure(pack);
        packs.response(player, UUID.randomUUID(), Action.SUCCESSFULLY_LOADED);
        assertFalse(packs.loaded(player));
        packs.response(player, pack, Action.ACCEPTED);
        assertFalse(packs.loaded(player));
        packs.response(player, pack, Action.SUCCESSFULLY_LOADED);
        assertTrue(packs.loaded(player));
        packs.configure(pack);
        assertTrue(packs.loaded(player));
        packs.response(player, pack, Action.FAILED_RELOAD);
        assertFalse(packs.loaded(player));
        packs.response(player, pack, Action.SUCCESSFULLY_LOADED);
        packs.forget(player);
        assertFalse(packs.loaded(player));
    }

    @Test
    void minecraftLoadsEveryBitmapAndFindsItsGlyph() throws Exception {
        Path client = Path.of(System.getProperty("servertags.minecraftClientJar"));
        var icons = new TagService(new JsonStore(dir.resolve("client-data"))).snapshot().icons();
        Path output = dir.resolve("client-pack.zip");
        ResourcePackGenerator.generate(icons, output);
        var source =
                new PackLocationInfo(
                        "vanilla-test",
                        Component.literal("Vanilla"),
                        PackSource.BUILT_IN,
                        Optional.empty());
        var custom =
                new PackLocationInfo(
                        "servertags-test",
                        Component.literal("ServerTags"),
                        PackSource.SERVER,
                        Optional.empty());
        PackResources vanilla =
                (PackResources)
                        new FilePackResources.FileResourcesSupplier(client).openMetadata(source);
        PackResources pack =
                (PackResources)
                        new FilePackResources.FileResourcesSupplier(output).openMetadata(custom);
        try (var resources =
                        new MultiPackResourceManager(
                                PackType.CLIENT_RESOURCES, List.of(vanilla, pack));
                ZipFile zip = new ZipFile(output.toFile())) {
            JsonArray definitions =
                    read(zip, "assets/servertags/font/icons.json").getAsJsonArray("providers");
            for (JsonElement definition : definitions) {
                var parsed =
                        GlyphProviderDefinition.Conditional.CODEC
                                .parse(JsonOps.INSTANCE, definition)
                                .getOrThrow();
                try (var glyphs =
                        parsed.definition().unpack().left().orElseThrow().load(resources)) {
                    String chars =
                            definition
                                    .getAsJsonObject()
                                    .getAsJsonArray("chars")
                                    .get(0)
                                    .getAsString();
                    int codepoint = chars.codePointAt(0);
                    assertNotNull(glyphs.getGlyph(codepoint), definition.toString());
                    assertTrue(glyphs.getSupportedGlyphs().contains(codepoint));
                }
            }
        }
    }

    static JsonObject read(ZipFile zip, String path) throws Exception {
        try (var in = zip.getInputStream(zip.getEntry(path))) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
