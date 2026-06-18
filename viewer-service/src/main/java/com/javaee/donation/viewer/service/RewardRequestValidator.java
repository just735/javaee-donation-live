package com.javaee.donation.viewer.service;

import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.viewer.exception.ViewerBusinessException;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class RewardRequestValidator {

    public void validate(RewardRequest request) {
        if (request == null) {
            throw new ViewerBusinessException("INVALID_REQUEST", "打赏请求不能为空");
        }
        if (isBlank(request.getRewardNo())) {
            throw new ViewerBusinessException("INVALID_REWARD_NO", "打赏单号不能为空");
        }
        if (isBlank(request.getViewerId())) {
            throw new ViewerBusinessException("INVALID_VIEWER_ID", "观众ID不能为空");
        }
        if (isBlank(request.getStreamerId())) {
            throw new ViewerBusinessException("INVALID_STREAMER_ID", "主播ID不能为空");
        }
        BigDecimal amount = request.getRewardAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ViewerBusinessException("INVALID_AMOUNT", "打赏金额必须大于0");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
