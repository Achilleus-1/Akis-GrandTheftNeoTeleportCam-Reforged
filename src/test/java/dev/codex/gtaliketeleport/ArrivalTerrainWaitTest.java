package dev.codex.gtaliketeleport;

import java.util.HashSet;
import java.util.Set;

public final class ArrivalTerrainWaitTest {
    private static final long SECOND = 1_000_000_000L;

    public static void main(String[] args) {
        checkHighFpsAndInterruptedReadiness();
        checkSlowFramesAndReset();
        checkDescentCanPauseWithoutRewinding();
        checkRendererFallbackNeverBypassesMissingChunks();
        checkCoverage();
        checkTimeline(100, 110);
        checkTimeline(120, 120);
        System.out.println("Arrival terrain loading, coverage, fallback, and timing checks passed.");
    }

    private static void checkHighFpsAndInterruptedReadiness() {
        ArrivalTerrainWait wait = new ArrivalTerrainWait();
        for (int i = 0; i < 50; ++i) wait.onTerrainRendered(true, true, true, i);
        require(!wait.shouldHold(false, 0), "Travel must not be delayed");
        require(wait.shouldHold(true, 0), "Departure frames cannot approve the destination");
        for (int i = 0; i < 500; ++i) wait.onTerrainRendered(true, true, true, i * 1_000_000L);
        require(wait.shouldHold(true, SECOND), "Hundreds of high-FPS frames cannot bypass settling");
        wait.onTerrainRendered(true, false, true, SECOND);
        for (int i = 0; i < 3; ++i) wait.onTerrainRendered(true, true, true, SECOND + i);
        require(wait.shouldHold(true, SECOND * 2), "New mesh work must restart the entire quiet period");
        require(!wait.shouldHold(true, SECOND + ArrivalTerrainWait.SETTLE_NANOS), "Stable terrain must release");
    }

    private static void checkSlowFramesAndReset() {
        ArrivalTerrainWait wait = new ArrivalTerrainWait();
        wait.shouldHold(true, 0);
        wait.onTerrainRendered(true, true, true, 0);
        require(wait.shouldHold(true, SECOND * 30), "One old rendered frame is insufficient");
        wait.onTerrainRendered(true, true, true, SECOND * 30);
        wait.onTerrainRendered(true, true, true, SECOND * 31);
        require(!wait.shouldHold(true, SECOND * 31), "Low FPS can still release after actual rendered frames");
        wait.reset();
        require(!wait.isMonitoring() && wait.delayTicks() == 0, "Reset must clear all prior teleport state");
        require(wait.shouldHold(true, SECOND * 60), "The next teleport must wait independently");
    }

    private static void checkDescentCanPauseWithoutRewinding() {
        ArrivalTerrainWait wait = new ArrivalTerrainWait();
        wait.shouldHold(true, 0);
        for (int i = 0; i < 3; ++i) wait.onTerrainRendered(true, true, true, i);
        require(!wait.shouldHold(true, SECOND * 2), "Ready destination must release");
        int initialDelay = wait.delayTicks();
        wait.invalidate(); // A late destination chunk/light packet arrives during descent.
        require(wait.shouldPauseDescent(SECOND * 3), "Late data must pause descent");
        wait.onTerrainRendered(false, false, true, SECOND * 4);
        require(wait.shouldPauseDescent(SECOND * 100), "Missing data cannot time out during descent");
        for (int i = 0; i < 3; ++i) wait.onTerrainRendered(true, true, true, SECOND * 101 + i);
        require(!wait.shouldPauseDescent(SECOND * 103), "Descent can resume after settling again");
        require(wait.delayTicks() == initialDelay, "Pausing must not shift the descent's start and rewind it");
    }

    private static void checkRendererFallbackNeverBypassesMissingChunks() {
        ArrivalTerrainWait wait = new ArrivalTerrainWait();
        wait.shouldHold(true, 0);
        for (int i = 0; i < 1200; ++i) {
            wait.onTerrainRendered(false, true, false, i * SECOND);
            require(wait.shouldHold(true, i * SECOND), "An empty render queue cannot excuse missing chunk data");
        }
        long base = 1300 * SECOND;
        wait.onTerrainRendered(true, false, false, base);
        require(wait.shouldHold(true, base + 7 * SECOND), "Background renderer work gets a conservative grace period");
        long grace = base + ArrivalTerrainWait.FALLBACK_QUEUE_GRACE_NANOS;
        for (int i = 0; i < 3; ++i) wait.onTerrainRendered(true, false, false, grace + i);
        require(wait.shouldHold(true, grace + SECOND), "Fallback must also settle in elapsed time");
        require(!wait.shouldHold(true, grace + ArrivalTerrainWait.FALLBACK_SETTLE_NANOS),
                "A renderer that never empties its queue must not trap the camera forever");
        wait.onTerrainRendered(true, false, false, grace + 10 * SECOND);
        require(!wait.shouldPauseDescent(grace + 10 * SECOND), "Routine LOD work must not repeatedly freeze descent");
        wait.onTerrainRendered(false, false, false, grace + 11 * SECOND);
        require(wait.shouldPauseDescent(grace + 11 * SECOND), "Lost chunk data must still pause fallback descent");
    }

    private static void checkCoverage() {
        for (int radius = 1; radius <= 32; ++radius) {
            final int distance = radius;
            Set<Long> prepared = new HashSet<>();
            ArrivalTerrainCoverage.ChunkProbe allLoaded = new ArrivalTerrainCoverage.ChunkProbe() {
                public boolean isLoaded(int x, int z) {
                    require(ArrivalTerrainCoverage.contains(x, z, distance), "Never request data outside the advertised server view");
                    return true;
                }
                public boolean prepareChunk(int x, int z) {
                    require(ArrivalTerrainCoverage.hasNeighborCoverage(x, z, distance), "Meshes need a complete neighbor ring");
                    prepared.add(key(x, z));
                    return true;
                }
            };
            require(ArrivalTerrainCoverage.prepare(0, 0, 0, 0, radius, allLoaded), "All received and built chunks must pass");
            require(prepared.contains(key(0, 0)), "Even a low-distance server must prepare the destination");
        }
        ArrivalTerrainCoverage.ChunkProbe missingEdge = new ArrivalTerrainCoverage.ChunkProbe() {
            public boolean isLoaded(int x, int z) { return x != 4 || z != 0; }
            public boolean prepareChunk(int x, int z) { return true; }
        };
        require(!ArrivalTerrainCoverage.prepare(0, 0, 0, 0, 4, missingEdge), "A missing outer data chunk must keep the hold");
        ArrivalTerrainCoverage.ChunkProbe unbuiltOffscreen = new ArrivalTerrainCoverage.ChunkProbe() {
            public boolean isLoaded(int x, int z) { return true; }
            public boolean prepareChunk(int x, int z) { return x != -2 || z != 0; }
        };
        require(!ArrivalTerrainCoverage.prepare(0, 0, 0, 0, 4, unbuiltOffscreen),
                "Received chunks and an idle worker queue cannot hide unbuilt offscreen terrain");
        ArrivalTerrainCoverage.ChunkProbe moving = new ArrivalTerrainCoverage.ChunkProbe() {
            public boolean isLoaded(int x, int z) {
                require(x == 0 && z == 0 || ArrivalTerrainCoverage.contains(x - 2, z, 4), "Respect a moving player's current server view");
                return true;
            }
            public boolean prepareChunk(int x, int z) { return true; }
        };
        require(ArrivalTerrainCoverage.prepare(0, 0, 2, 0, 4, moving), "Movement must not wait for chunks the server stopped sending");
    }

    private static void checkTimeline(int motionStart, int pushStart) {
        ArrivalTerrainWait wait = new ArrivalTerrainWait();
        int total = pushStart + 80;
        for (int tick = motionStart - 1; tick < motionStart + 200; ++tick) {
            if (wait.shouldHold(tick >= motionStart + wait.delayTicks() - 1, tick * SECOND)) ++total;
            require(tick + 0.999f < motionStart + wait.delayTicks(), "Partial frames must stay above descent");
            require(total - (pushStart + wait.delayTicks()) == 80, "Hold must preserve zoom and release duration");
            wait.onTerrainRendered(false, false, true, tick * SECOND);
        }
    }

    private static long key(int x, int z) { return (long) x << 32 ^ (long) z & 0xFFFFFFFFL; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
