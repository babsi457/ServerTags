package de.basky.servertags.chat;

import de.basky.servertags.ServerTags;

import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ChatIntegration {
    private ChatIntegration() {}

    public static ChatType.Bound decorate(
            ChatType.Bound original, UUID senderId, ServerPlayer viewer) {
        ServerTags mod = ServerTags.instance();
        if (mod == null
                || !mod.service().snapshot().config().chatEnabled()
                || !original.chatType().is(ChatType.CHAT)) return original;
        ServerPlayer sender = viewer.level().getServer().getPlayerList().getPlayer(senderId);
        if (sender == null) return original;
        Component name = mod.formatter().name(senderId, viewer.getUUID(), sender.getName());
        return new ChatType.Bound(original.chatType(), name, original.targetName());
    }
}
