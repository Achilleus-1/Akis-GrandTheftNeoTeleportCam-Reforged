package dev.codex.gtaliketeleport;

public final class ArrivalTerrainWaitTest {
    public static void main(String[] args) {
        ArrivalTerrainWait wait = new ArrivalTerrainWait();
        // Frames from the departure/travel phases must not pre-approve descent.
        for (int i = 0; i < 20; ++i) wait.onTerrainRendered(true);
        require(!wait.shouldHold(false), "Travel must not be delayed");
        require(wait.delayTicks() == 0, "No delay before arrival");
        require(wait.shouldHold(true), "Arrival must wait for destination frames");

        // Slow loading must never fall through the old 80-tick handoff timeout.
        for (int i = 0; i < 1200; ++i) {
            wait.onTerrainRendered(false);
            require(wait.shouldHold(true), "Loading must hold even after a minute");
        }
        for (int i = 0; i < 4; ++i) wait.onTerrainRendered(true);
        require(wait.shouldHold(true), "A partially settled renderer must still hold");
        wait.onTerrainRendered(false);
        for (int i = 0; i < 4; ++i) wait.onTerrainRendered(true);
        require(wait.shouldHold(true), "New loading must reset settled frames");
        wait.onTerrainRendered(true);
        require(!wait.shouldHold(true), "Settled destination must resume descent");
        require(!wait.isWaiting(), "Rendering hook must stop checking after release");
        int delay = wait.delayTicks();
        wait.onTerrainRendered(false);
        require(!wait.shouldHold(true), "Later updates must not restart the descent");
        require(wait.delayTicks() == delay, "Animation timing must stay fixed after release");

        wait.reset();
        require(wait.delayTicks() == 0 && !wait.isWaiting(), "Next teleport must reset all delay");
        require(wait.shouldHold(true), "Next teleport must check its own destination");

        // Model the controller's moving boundary for both normal and skipped travel.
        checkTimeline(100, 110); // Normal travel: descent includes the pre-push glide.
        checkTimeline(120, 120); // Cross-dimension travel: hold before the push starts.
        System.out.println("Arrival terrain wait checks passed.");
    }

    private static void checkTimeline(int motionStart, int pushStart) {
        ArrivalTerrainWait wait = new ArrivalTerrainWait();
        int total = pushStart + 80;
        for (int tick = motionStart - 1; tick < motionStart + 200; ++tick) {
            if (wait.shouldHold(tick >= motionStart + wait.delayTicks() - 1)) ++total;
            require(tick + 0.999f < motionStart + wait.delayTicks(), "Partial frames must stay above descent");
            require(total - (pushStart + wait.delayTicks()) == 80, "Hold must preserve zoom and release duration");
            wait.onTerrainRendered(false);
        }
        for (int i = 0; i < 5; ++i) wait.onTerrainRendered(true);
        require(!wait.shouldHold(true), "Timeline must resume once terrain is ready");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
