package com.javaee.donation.simulator.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint {

    private Long epochSecond;
    private Double qps;
    private Long avgLatencyMs;
    private Integer errorCount;
    private Integer successCount;
}
