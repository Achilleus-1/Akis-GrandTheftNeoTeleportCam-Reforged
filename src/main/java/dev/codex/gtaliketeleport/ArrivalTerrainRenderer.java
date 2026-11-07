package dev.codex.gtaliketeleport;

import net.minecraft.core.BlockPos;

/** Implemented by the active client terrain renderer; never used on a server. */
public interface ArrivalTerrainRenderer {
    /** Return true only after this section's geometry has been built and uploaded. */
    boolean gtalikeTeleport$prepareSection(BlockPos origin);
}
