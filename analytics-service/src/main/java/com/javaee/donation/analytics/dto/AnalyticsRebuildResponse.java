package com.javaee.donation.analytics.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsRebuildResponse {

    private Integer viewerProfiles;
    private Integer hourlyStats;
    private Integer streamerViewerSummaries;
    private LocalDateTime rebuiltAt;
}
