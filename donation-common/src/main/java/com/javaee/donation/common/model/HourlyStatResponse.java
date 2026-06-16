package com.javaee.donation.common.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HourlyStatResponse {

    private String statHour;
    private String streamerId;
    private String viewerGender;
    private BigDecimal rewardAmount;
    private Long rewardCount;
}
