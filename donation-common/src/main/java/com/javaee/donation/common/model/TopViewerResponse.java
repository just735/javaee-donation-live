package com.javaee.donation.common.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopViewerResponse {

    private String viewerId;
    private String viewerName;
    private BigDecimal totalRewardAmount;
    private Long rewardCount;

    public TopViewerResponse(String viewerId, String viewerName, BigDecimal totalRewardAmount) {
        this.viewerId = viewerId;
        this.viewerName = viewerName;
        this.totalRewardAmount = totalRewardAmount;
    }
}
