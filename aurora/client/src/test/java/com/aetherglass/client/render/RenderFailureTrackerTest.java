package com.aetherglass.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RenderFailureTrackerTest {
    private static final RuntimeException ERROR = new RuntimeException("test failure");

    @Test
    void disablesOnlyAfterTwelveConsecutiveFailures() {
        RenderFailureTracker tracker = new RenderFailureTracker("particles", "render");
        for (int i = 1; i < RenderFailureTracker.MAX_FAILURES; i++) {
            tracker.failure(ERROR);
            assertFalse(tracker.disabled());
        }
        tracker.failure(ERROR);
        assertTrue(tracker.disabled());
        tracker.success();
        assertTrue(tracker.disabled(), "Отключённый проход требует перезапуска");
    }

    @Test
    void successfulFrameResetsConsecutiveCount() {
        RenderFailureTracker tracker = new RenderFailureTracker("particles", "render");
        for (int frame = 0; frame < 30; frame++) {
            for (int i = 1; i < RenderFailureTracker.MAX_FAILURES; i++) {
                tracker.failure(ERROR);
            }
            tracker.success();
        }
        assertFalse(tracker.disabled());
    }

    @Test
    void brokenParticlesDoNotDisableBlocksOrFrameUpdate() {
        RenderFailureTracker particles = new RenderFailureTracker("particles", "render");
        RenderFailureTracker blocks = new RenderFailureTracker("blocks", "render");
        RenderFailureTracker frame = new RenderFailureTracker("frame", "update");
        for (int i = 0; i < RenderFailureTracker.MAX_FAILURES; i++) {
            particles.failure(ERROR);
        }
        assertTrue(particles.disabled());
        assertFalse(blocks.disabled());
        assertFalse(frame.disabled());
    }
}
