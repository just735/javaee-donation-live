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
}
