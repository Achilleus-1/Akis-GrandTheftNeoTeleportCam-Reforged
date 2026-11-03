package dev.codex.gtaliketeleport.mixin;

import dev.codex.gtaliketeleport.TeleportTransitionController;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow @Final private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    @Shadow private ViewArea viewArea;
    @Shadow private int lastCameraSectionX;
    @Shadow private int lastCameraSectionY;
    @Shadow private int lastCameraSectionZ;

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void gtalikeTeleport$checkArrivalTerrain(DeltaTracker tracker, boolean outline,
            Camera camera, GameRenderer renderer, LightTexture light, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (!TeleportTransitionController.isWaitingForArrivalTerrain()) return;
        boolean complete = ((LevelRenderer) (Object) this).hasRenderedAllSections();
        // An empty worker queue alone can precede scheduling a newly visible section.
        // Sodium replaces the queue check above and maintains its own section list.
        if (complete) {
            for (var section : visibleSections) {
                if (section.hasAllNeighbors() && (section.isDirty()
                        || section.getCompiled() == SectionRenderDispatcher.CompiledSection.UNCOMPILED)) {
                    complete = false;
                    break;
                }
            }
        }
        TeleportTransitionController.onArrivalTerrainRendered(complete);
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void gtalikeTeleport$anchorViewAreaToTransitionCamera(DeltaTracker tracker, boolean outline,
            Camera camera, GameRenderer renderer, LightTexture light, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (!TeleportTransitionController.shouldForceTerrainFrustumApply() || viewArea == null) return;
        var cameraPos = camera.getPosition();
        viewArea.repositionCamera(cameraPos.x, cameraPos.z);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            lastCameraSectionX = SectionPos.posToSectionCoord(player.getX());
            lastCameraSectionY = SectionPos.posToSectionCoord(player.getY());
            lastCameraSectionZ = SectionPos.posToSectionCoord(player.getZ());
        }
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel$ClientLevelData;getHorizonHeight(Lnet/minecraft/world/level/LevelHeightAccessor;)D"))
    private double gtalikeTeleport$useGroundSkyHorizon(ClientLevel.ClientLevelData data, LevelHeightAccessor level) {
        return TeleportTransitionController.shouldUseGroundSkyBackground() ? Double.NEGATIVE_INFINITY : data.getHorizonHeight(level);
    }
}
