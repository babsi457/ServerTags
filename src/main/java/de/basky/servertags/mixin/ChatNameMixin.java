package de.basky.servertags.mixin;

import de.basky.servertags.chat.ChatIntegration;

import net.minecraft.network.chat.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/**
 * Only substitutes the sender component, separately for each viewer. The signed body is untouched.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatNameMixin {
    @ModifyVariable(method = "sendPlayerChatMessage", at = @At("HEAD"), argsOnly = true)
    private ChatType.Bound servertags$name(
            ChatType.Bound original, PlayerChatMessage message, ChatType.Bound ignored) {
        return ChatIntegration.decorate(
                original,
                message.link().sender(),
                ((ServerGamePacketListenerImpl) (Object) this).getPlayer());
    }
}
