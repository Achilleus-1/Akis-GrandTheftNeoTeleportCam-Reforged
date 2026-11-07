package dev.codex.gtaliketeleport;

/** Uses elapsed time as well as distinct frames, so high FPS cannot bypass loading. */
final class ArrivalTerrainWait {
    static final long SETTLE_NANOS = 1_500_000_000L;
    static final long FALLBACK_SETTLE_NANOS = 3_000_000_000L;
    static final long FALLBACK_QUEUE_GRACE_NANOS = 8_000_000_000L;
    private static final int REQUIRED_READY_FRAMES = 3;
    private int delayTicks;
    private int readyFrames;
    private long readySince = Long.MIN_VALUE;
    private long dataReadySince = Long.MIN_VALUE;
    private long settleNanos = SETTLE_NANOS;
    private boolean waiting;
    private boolean released;

    boolean shouldHold(boolean atDescentBoundary, long now) {
        if (released || !atDescentBoundary) {
            return false;
        }
        waiting = true;
        if (isSettled(now)) {
            waiting = false;
            released = true;
            return false;
        }
        ++delayTicks;
        return true;
    }

    void onTerrainRendered(boolean dataReady, boolean geometryReady, boolean preciseRenderer, long now) {
        if (waiting || released) {
            settleNanos = preciseRenderer ? SETTLE_NANOS : FALLBACK_SETTLE_NANOS;
            if (!dataReady) dataReadySince = Long.MIN_VALUE;
            else if (dataReadySince == Long.MIN_VALUE) dataReadySince = now;
            // A third-party renderer may keep background LOD/rebuild work queued
            // indefinitely. Only its queue signal has a fallback, never chunk data.
            boolean ready = dataReady && (geometryReady || !preciseRenderer
                    && now - dataReadySince >= FALLBACK_QUEUE_GRACE_NANOS);
            // Once descending, routine third-party rebuilds must not repeatedly
            // freeze the animation. Lost or newly received chunk data still does.
            if (released && !preciseRenderer && !waiting && dataReady) ready = true;
            if (!ready) {
                readyFrames = 0;
                readySince = Long.MIN_VALUE;
                if (released) waiting = true;
            } else {
                if (readyFrames == 0) readySince = now;
                readyFrames = Math.min(REQUIRED_READY_FRAMES, readyFrames + 1);
            }
        }
    }

    void invalidate() {
        readyFrames = 0;
        readySince = Long.MIN_VALUE;
        dataReadySince = Long.MIN_VALUE;
        if (released) waiting = true;
    }

    boolean shouldPauseDescent(long now) {
        if (!released) return false;
        if (waiting && isSettled(now)) {
            waiting = false;
        }
        return waiting;
    }

    private boolean isSettled(long now) {
        return readyFrames >= REQUIRED_READY_FRAMES && now - readySince >= settleNanos;
    }

    boolean isMonitoring() {
        return waiting || released;
    }

    boolean isPaused() {
        return released && waiting;
    }

    boolean isWaiting() {
        return waiting;
    }

    int delayTicks() {
        return delayTicks;
    }

    void reset() {
        delayTicks = 0;
        readyFrames = 0;
        readySince = Long.MIN_VALUE;
        dataReadySince = Long.MIN_VALUE;
        settleNanos = SETTLE_NANOS;
        waiting = false;
        released = false;
    }
}
