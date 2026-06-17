package com.javaee.donation.viewer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewerRewardResponse {

    private String rewardNo;
    private String settleStatus;
    private String streamerId;
    private BigDecimal rewardAmount;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal withdrawableAmount;
    private LocalDateTime settledAt;
    private String message;
}
