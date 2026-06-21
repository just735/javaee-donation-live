package com.javaee.donation.simulator.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class SimulationStartResult {

    private String runId;
    private String traceId;
    private Integer requestedCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer timeoutCount;
    private Integer blockedCount;
    private Double successRate;
    private Double errorRatio;
    private Long avgLatencyMs;
    private Long p95LatencyMs;
    private Long p99LatencyMs;
    private Double actualQps;
    private Long durationMillis;
    private List<TimeSeriesPoint> latencySeries = new ArrayList<>();
    private List<FailureSample> failureSamples = new ArrayList<>();
    private String reportMarkdown;
    private String message;
}
