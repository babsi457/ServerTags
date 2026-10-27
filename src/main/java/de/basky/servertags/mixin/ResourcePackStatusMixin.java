package de.basky.servertags.mixin;

import com.mojang.authlib.GameProfile;

import de.basky.servertags.ServerTags;

import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ResourcePackStatusMixin {
    @Shadow
    protected abstract GameProfile playerProfile();

    @Inject(method = "handleResourcePackResponse", at = @At("TAIL"))
    private void servertags$packStatus(ServerboundResourcePackPacket packet, CallbackInfo ci) {
        ServerTags mod = ServerTags.instance();
        if (mod != null) mod.packs().response(playerProfile().id(), packet.id(), packet.action());
    }
}
