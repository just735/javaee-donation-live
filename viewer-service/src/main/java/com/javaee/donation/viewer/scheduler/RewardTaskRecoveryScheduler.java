package com.javaee.donation.viewer.scheduler;

import com.javaee.donation.viewer.config.ViewerRewardProperties;
import com.javaee.donation.viewer.entity.RewardIngestTask;
import com.javaee.donation.viewer.service.RewardTaskService;
import com.javaee.donation.viewer.service.ViewerRewardService;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RewardTaskRecoveryScheduler {

    private final RewardTaskService rewardTaskService;
    private final ViewerRewardService viewerRewardService;
    private final ViewerRewardProperties properties;

    public RewardTaskRecoveryScheduler(RewardTaskService rewardTaskService,
                                       ViewerRewardService viewerRewardService,
                                       ViewerRewardProperties properties) {
        this.rewardTaskService = rewardTaskService;
        this.viewerRewardService = viewerRewardService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${viewer.reward.settlement.recovery-fixed-delay-ms:3000}")
    public void recover() {
        List<RewardIngestTask> tasks = rewardTaskService.listRecoverableTasks();
        int limit = Math.min(tasks.size(), properties.getSettlement().getRecoveryBatchSize());
        for (int index = 0; index < limit; index++) {
            viewerRewardService.submitSettlement(tasks.get(index).getRewardNo());
        }
    }
}
