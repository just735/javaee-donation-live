package com.javaee.donation.analytics.job;

import com.javaee.donation.analytics.service.AnalyticsService;
import com.javaee.donation.common.context.TraceContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsRebuildScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRebuildScheduler.class);

    private final AnalyticsService analyticsService;

    public AnalyticsRebuildScheduler(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Scheduled(fixedRateString = "${analytics.rebuild.fixed-rate-ms:300000}")
    public void rebuild() {
        TraceContext.setTraceId("analytics-job-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            log.info("[{}] scheduled analytics rebuild triggered", TraceContext.getTraceId());
            analyticsService.rebuild();
        } catch (Exception exception) {
            log.error("[{}] scheduled analytics rebuild failed", TraceContext.getTraceId(), exception);
        } finally {
            TraceContext.clear();
        }
    }
}
