package com.javaee.donation.viewer.service;

import com.javaee.donation.viewer.dto.ViewerRewardResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.concurrent.Executor;

@Service
public class RewardNotificationService {

    private static final Logger log = LoggerFactory.getLogger(RewardNotificationService.class);

    private final Executor rewardNotifyExecutor;

    public RewardNotificationService(@Qualifier("rewardNotifyExecutor") Executor rewardNotifyExecutor) {
        this.rewardNotifyExecutor = rewardNotifyExecutor;
    }

    public void notifyAsync(String traceId, ViewerRewardResponse rewardResponse) {
        rewardNotifyExecutor.execute(() ->
                log.info("[{}] reward notification sent, rewardNo={}, streamerId={}, amount={}, status={}",
                        traceId, rewardResponse.getRewardNo(), rewardResponse.getStreamerId(),
                        rewardResponse.getRewardAmount(), rewardResponse.getSettleStatus()));
    }
}
