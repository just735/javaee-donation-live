package com.javaee.donation.common.model;

import lombok.Data;

@Data
public class SimulationRequest {

    private Integer requestCount;
    private Integer viewerCount;
    private Integer streamerCount;
    private Integer qps;
    private String streamerId;
}
