package dev.codex.gtaliketeleport.mixin;

import dev.codex.gtaliketeleport.GtaLikeTeleportClient;
import dev.codex.gtaliketeleport.TeleportTransitionController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ClientPacketListener.class})
public abstract class ClientPacketListenerMixin {
    @Unique private boolean gtalikeTeleport$positionInitialized;
    @Unique private int gtalikeTeleport$lastPositionId = Integer.MIN_VALUE;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void gtalikeTeleport$observeRecipientTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        boolean firstPosition = !gtalikeTeleport$positionInitialized;
        boolean duplicate = packet.getId() == gtalikeTeleport$lastPositionId;
        gtalikeTeleport$positionInitialized = true;
        gtalikeTeleport$lastPositionId = packet.getId();
        if (!firstPosition && !duplicate) GtaLikeTeleportClient.observeServerTeleport(packet);
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void gtalikeTeleport$observeRecipientDimensionChange(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        GtaLikeTeleportClient.observeServerDimensionChange(packet.commonPlayerSpawnInfo().dimension());
        gtalikeTeleport$positionInitialized = false;
        gtalikeTeleport$lastPositionId = Integer.MIN_VALUE;
    }
    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void gtalikeTeleport$destinationChunkArrived(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        TeleportTransitionController.onDestinationChunkChanged(packet.getX(), packet.getZ());
    }

    @Inject(method={"sendCommand"}, at={@At(value="HEAD")}, cancellable=true)
    private void gtalikeTeleport$interceptCommand(String command, CallbackInfo ci) {
        if (!GtaLikeTeleportClient.interceptOutgoingCommand(command)) {
            ci.cancel();
        }
    }

    @Inject(method={"handleForgetLevelChunk"}, at={@At(value="HEAD")}, cancellable=true)
    private void gtalikeTeleport$retainDepartingChunk(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        if (TeleportTransitionController.shouldRetainDepartingChunk(packet.pos().x, packet.pos().z)) {
            ci.cancel();
        }
    }
}
