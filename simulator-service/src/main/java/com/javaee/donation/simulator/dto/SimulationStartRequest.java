package com.javaee.donation.simulator.dto;

import lombok.Data;

@Data
public class SimulationStartRequest {

    private Integer requestCount;
    private Integer viewerCount;
    private Integer streamerCount;
    private Integer qps;
    private Integer durationSeconds;
    private String streamerId;
    private String mode;
    private Integer stepQps;
    private Integer stepDurationSeconds;
    private Double failureRate;
    private Double timeoutRate;
}
