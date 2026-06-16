package com.javaee.donation.finance.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationResult {

    private String reconciledAt;
    private int totalStreamers;
    private int matched;
    private int mismatched;
    private List<MismatchDetail> mismatches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MismatchDetail {
        private String streamerId;
        private BigDecimal expectedTotalReward;
        private BigDecimal expectedTotalCommission;
        private BigDecimal expectedWithdrawable;
        private BigDecimal actualTotalReward;
        private BigDecimal actualTotalCommission;
        private BigDecimal actualWithdrawable;
        private boolean autoCorrected;
    }
}
