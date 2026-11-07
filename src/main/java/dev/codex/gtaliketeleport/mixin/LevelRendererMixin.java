package dev.codex.gtaliketeleport.mixin;

import dev.codex.gtaliketeleport.TeleportTransitionController;
import dev.codex.gtaliketeleport.ArrivalTerrainRenderer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin implements ArrivalTerrainRenderer {
    @Shadow private ViewArea viewArea;
    @Shadow private SectionRenderDispatcher sectionRenderDispatcher;
    @Shadow private int lastCameraSectionX;
    @Shadow private int lastCameraSectionY;
    @Shadow private int lastCameraSectionZ;
    @Unique private int gtalikeTeleport$buildBudget;
    @Unique private RenderRegionCache gtalikeTeleport$regionCache;
    @Unique private final Map<SectionRenderDispatcher.RenderSection, SectionRenderDispatcher.CompiledSection> gtalikeTeleport$pending = new IdentityHashMap<>();
    @Unique private final LongSet gtalikeTeleport$prepared = new LongOpenHashSet();
    @Unique private long gtalikeTeleport$revision = Long.MIN_VALUE;

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void gtalikeTeleport$checkArrivalTerrain(DeltaTracker tracker, boolean outline,
            Camera camera, GameRenderer renderer, LightTexture light, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (!TeleportTransitionController.shouldMonitorArrivalTerrain()) {
            gtalikeTeleport$pending.clear();
            gtalikeTeleport$prepared.clear();
            return;
        }
        long revision = TeleportTransitionController.getArrivalTerrainRevision();
        if (revision != gtalikeTeleport$revision) {
            gtalikeTeleport$prepared.clear();
            gtalikeTeleport$revision = revision;
        }
        boolean dataReady = TeleportTransitionController.prepareArrivalTerrain(origin -> true);
        // Replaced renderers retain the public completion API, but may discard the
        // vanilla view grid/dispatcher. Never reach into their private internals.
        boolean vanilla = viewArea != null && viewArea.sections.length > 0 && sectionRenderDispatcher != null
                && !ModList.get().isLoaded("sodium") && !ModList.get().isLoaded("embeddium");
        boolean geometryReady;
        if (vanilla) {
            gtalikeTeleport$buildBudget = 8;
            gtalikeTeleport$regionCache = new RenderRegionCache();
            geometryReady = TeleportTransitionController.prepareArrivalTerrain(this);
            gtalikeTeleport$regionCache = null;
        } else {
            geometryReady = ((LevelRenderer) (Object) this).hasRenderedAllSections();
        }
        // Unknown mods can also replace or supplement rendering. Use the longer
        // conservative path whenever additional mods are present, without having
        // to maintain a list of every renderer and version in existence.
        boolean precise = vanilla && ModList.get().getMods().stream().allMatch(mod ->
                mod.getModId().equals("minecraft") || mod.getModId().equals("neoforge")
                        || mod.getModId().equals("gtalike_teleport"));
        TeleportTransitionController.onArrivalTerrainRendered(dataReady, geometryReady, precise);
    }

    @Override
    public boolean gtalikeTeleport$prepareSection(net.minecraft.core.BlockPos origin) {
        if (viewArea == null || sectionRenderDispatcher == null) return false;
        var section = ((ViewAreaAccessor) viewArea).gtalikeTeleport$getRenderSectionAt(origin);
        // ViewArea is a wrapped grid: a slot can still contain the departure chunk.
        if (section == null || !section.getOrigin().equals(origin)) return false;
        var current = section.getCompiled();
        long position = SectionPos.asLong(origin);
        // Once this chunk's received terrain has a mesh, ordinary block/lighting
        // rebuilds (lava, farms, etc.) must not starve the loading hold forever.
        // A fresh chunk packet invalidates this set via the revision above.
        if (current != SectionRenderDispatcher.CompiledSection.UNCOMPILED
                && gtalikeTeleport$prepared.contains(position)) return true;
        var pending = gtalikeTeleport$pending.get(section);
        if (pending != null) {
            if (pending == current && !section.isDirty()) return false;
            if (pending != current) gtalikeTeleport$pending.remove(section);
        }
        boolean compiled = current != SectionRenderDispatcher.CompiledSection.UNCOMPILED;
        if (compiled && !section.isDirty()) {
            gtalikeTeleport$prepared.add(position);
            return true;
        }
        var level = Minecraft.getInstance().level;
        if (section.isDirty() && gtalikeTeleport$buildBudget > 0 && section.hasAllNeighbors()
                && level != null && level.getLightEngine().lightOnInSection(SectionPos.of(origin))) {
            --gtalikeTeleport$buildBudget;
            gtalikeTeleport$pending.put(section, current);
            section.rebuildSectionAsync(sectionRenderDispatcher, gtalikeTeleport$regionCache);
            section.setNotDirty();
        }
        return false;
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
