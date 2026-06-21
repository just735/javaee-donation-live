package com.javaee.donation.simulator.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SimulationMetricsCollectorTest {

    @Test
    void shouldCalculateSuccessRateAndLatency() {
        SimulationMetricsCollector collector = new SimulationMetricsCollector(10);
        long second = 1_700_000_000L;

        collector.recordRequested();
        collector.recordSuccess(100, second);
        collector.recordRequested();
        collector.recordFailed(200, second, "sim-test-1", "failed");

        var result = collector.buildResult("run-1", "trace-1", 1000L);

        assertEquals(2, result.getRequestedCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(0.5, result.getSuccessRate());
        assertEquals(0.5, result.getErrorRatio());
        assertEquals(150L, result.getAvgLatencyMs());
        assertEquals(2.0, result.getActualQps());
    }
}
