package com.javaee.donation.finance.dto;

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
public class WithdrawResponse {

    private String streamerId;
    private BigDecimal withdrawAmount;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;
    private LocalDateTime withdrawnAt;
    private String status;
}
