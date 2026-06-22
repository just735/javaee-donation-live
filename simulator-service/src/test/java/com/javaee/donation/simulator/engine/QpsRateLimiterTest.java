package com.javaee.donation.simulator.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QpsRateLimiterTest {

    @Test
    void shouldLimitToTargetQps() {
        QpsRateLimiter limiter = new QpsRateLimiter(100);
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            limiter.acquire();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 900, "expected ~1s for 100 tokens, got " + elapsedMs + "ms");
        assertTrue(elapsedMs < 1500, "expected ~1s for 100 tokens, got " + elapsedMs + "ms");
    }

    @Test
    void shouldAccumulatePermitsAfterDelay() throws InterruptedException {
        QpsRateLimiter limiter = new QpsRateLimiter(50);
        limiter.acquire();

        Thread.sleep(120);

        int permits = limiter.acquireAvailable(10);
        assertTrue(permits >= 5, "expected accumulated permits after delay, got " + permits);
    }
}
