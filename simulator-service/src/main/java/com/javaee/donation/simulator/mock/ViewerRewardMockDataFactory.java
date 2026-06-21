package com.javaee.donation.simulator.mock;

import com.javaee.donation.common.model.RewardRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ViewerRewardMockDataFactory {

    public RewardRequest next(int sequenceIndex, int viewerCount, int streamerCount, String fixedStreamerId) {
        int safeViewerCount = Math.max(viewerCount, 1);
        int safeStreamerCount = Math.max(streamerCount, 1);

        int viewerNo = sequenceIndex % safeViewerCount + 1;
        String viewerId = "viewer-" + viewerNo;

        String streamerId;
        if (fixedStreamerId != null && !fixedStreamerId.isBlank()) {
            streamerId = fixedStreamerId;
        } else {
            int streamerNo = sequenceIndex % safeStreamerCount + 1;
            streamerId = "streamer-" + streamerNo;
        }

        RewardRequest request = new RewardRequest();
        request.setRewardNo(UUID.randomUUID().toString());
        request.setViewerId(viewerId);
        request.setViewerName("观众" + viewerNo);
        request.setViewerGender(sequenceIndex % 2 == 0 ? "MALE" : "FEMALE");
        request.setStreamerId(streamerId);
        request.setStreamerName("主播" + streamerId);
        request.setRewardAmount(new BigDecimal("10.00").add(BigDecimal.valueOf(sequenceIndex % 5)));
        request.setRewardTime(LocalDateTime.now().toString());
        return request;
    }
}
