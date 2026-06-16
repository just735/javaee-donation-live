package com.javaee.donation.common.model;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class RewardRequest {

    private String rewardNo;
    private String viewerId;
    private String viewerName;
    private String viewerGender;
    private String streamerId;
    private String streamerName;
    private BigDecimal rewardAmount;
    private String rewardTime;
}
