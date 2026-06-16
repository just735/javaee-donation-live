package com.javaee.donation.common.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamerBalanceResponse {

    private String streamerId;
    private BigDecimal totalRewardAmount;
    private BigDecimal totalCommissionAmount;
    private BigDecimal withdrawableAmount;
}
