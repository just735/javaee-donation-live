package com.javaee.donation.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    private int defaultQps = 500;
    private int defaultDurationSeconds = 10;
    private int defaultStreamerCount = 100;
    private int defaultViewerCount = 300000;
    private int threadPoolSize = 128;
    private int maxFailureSamples = 50;
    private Feign feign = new Feign();

    @Data
    public static class Feign {
        private int connectTimeoutMs = 1000;
        private int readTimeoutMs = 3000;
        private int timeoutSimulateReadMs = 1;
    }
}
