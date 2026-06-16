package com.javaee.donation.common.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class CommissionRuleRequest {

    private String streamerId;
    private BigDecimal commissionRate;
    private String effectiveFrom;
    private String effectiveTo;
}
