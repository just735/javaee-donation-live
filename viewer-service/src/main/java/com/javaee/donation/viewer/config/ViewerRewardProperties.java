package com.javaee.donation.viewer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "viewer.reward")
public class ViewerRewardProperties {

    private int qpsLimit = 800;
    private final Settlement settlement = new Settlement();

    @Data
    public static class Settlement {
        private int threadPoolSize = 256;
        private int queueCapacity = 20000;
        private int retryDelaySeconds = 5;
        private int processingLeaseSeconds = 30;
        private int recoveryBatchSize = 200;
        private long recoveryFixedDelayMs = 3000L;
    }
}
