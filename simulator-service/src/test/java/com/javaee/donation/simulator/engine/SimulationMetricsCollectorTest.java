package com.javaee.donation.simulator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SimulationMetricsCollectorTest {

    @Test
    void shouldCalculateSuccessRateAndLatency() {
        SimulationMetricsCollector collector = new SimulationMetricsCollector(10);
        long second = 1_700_000_000L;

        collector.recordRequested();
        collector.recordAccepted(100, second);
        collector.recordRequested();
        collector.recordSettled(80, second);
        collector.recordRequested();
        collector.recordDuplicate(60, second);
        collector.recordRequested();
        collector.recordFailed(200, second, "sim-test-1", "failed");

        var result = collector.buildResult("run-1", "trace-1", 1000L);

        assertEquals(4, result.getRequestedCount());
        assertEquals(3, result.getSuccessCount());
        assertEquals(1, result.getAcceptedCount());
        assertEquals(1, result.getSettledCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(0.75, result.getSuccessRate());
        assertEquals(0.25, result.getErrorRatio());
        assertEquals(110L, result.getAvgLatencyMs());
        assertEquals(4.0, result.getActualQps());
    }
}
