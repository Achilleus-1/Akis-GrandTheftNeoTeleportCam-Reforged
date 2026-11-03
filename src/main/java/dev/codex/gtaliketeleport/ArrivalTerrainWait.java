package dev.codex.gtaliketeleport;

/** Keeps the descent behind a short run of completed terrain frames. */
final class ArrivalTerrainWait {
    private static final int REQUIRED_READY_FRAMES = 5;
    private int delayTicks;
    private int readyFrames;
    private boolean waiting;
    private boolean released;

    boolean shouldHold(boolean atDescentBoundary) {
        if (released || !atDescentBoundary) {
            return false;
        }
        waiting = true;
        if (readyFrames >= REQUIRED_READY_FRAMES) {
            waiting = false;
            released = true;
            return false;
        }
        ++delayTicks;
        return true;
    }

    void onTerrainRendered(boolean ready) {
        if (waiting) {
            readyFrames = ready ? Math.min(REQUIRED_READY_FRAMES, readyFrames + 1) : 0;
        }
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
        waiting = false;
        released = false;
    }
}
