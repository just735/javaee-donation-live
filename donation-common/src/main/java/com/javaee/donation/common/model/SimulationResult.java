package com.javaee.donation.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationResult {

    private Integer requestedCount;
    private Integer successCount;
    private Integer failedCount;
    private Long durationMillis;
    private String message;
}
