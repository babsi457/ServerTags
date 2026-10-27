package de.basky.servertags.icon;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks load acknowledgements for the configured server pack. */
public final class ResourcePackState {
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private volatile UUID expected;

    public void configure(UUID id) {
        if (!Objects.equals(expected, id)) {
            expected = id;
            loaded.clear();
        }
    }

    public boolean loaded(UUID player) {
        return loaded.contains(player);
    }

    public void forget(UUID player) {
        loaded.remove(player);
    }

    public void response(UUID player, UUID pack, ServerboundResourcePackPacket.Action action) {
        if (expected == null || !expected.equals(pack)) return;
        if (action == ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED) loaded.add(player);
        else loaded.remove(player);
    }
}
