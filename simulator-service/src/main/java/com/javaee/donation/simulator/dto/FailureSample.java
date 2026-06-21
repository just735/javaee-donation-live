package com.javaee.donation.simulator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailureSample {

    private String traceId;
    private String reason;
    private Long latencyMs;
}
