package com.javaee.donation.simulator.engine;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.RewardRequest;
import com.javaee.donation.simulator.client.ViewerRewardClient;
import com.javaee.donation.simulator.client.ViewerRewardTimeoutClient;
import com.javaee.donation.simulator.client.dto.ViewerRewardClientResponse;
import com.javaee.donation.simulator.config.SimulatorProperties;
import com.javaee.donation.simulator.dto.SimulationStartRequest;
import com.javaee.donation.simulator.dto.SimulationStartResult;
import com.javaee.donation.simulator.mock.ViewerRewardMockDataFactory;
import feign.RetryableException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoadTestEngine {

    private static final Logger log = LoggerFactory.getLogger(LoadTestEngine.class);

    private static final String MODE_FIXED = "FIXED";
    private static final String MODE_STEP = "STEP";
    private static final String MODE_SUSTAINED = "SUSTAINED";

    private final SimulatorProperties properties;
    private final ViewerRewardMockDataFactory mockDataFactory;
    private final ViewerRewardClient viewerRewardClient;
    private final ViewerRewardTimeoutClient viewerRewardTimeoutClient;

    public LoadTestEngine(SimulatorProperties properties,
                          ViewerRewardMockDataFactory mockDataFactory,
                          ViewerRewardClient viewerRewardClient,
                          ViewerRewardTimeoutClient viewerRewardTimeoutClient) {
        this.properties = properties;
        this.mockDataFactory = mockDataFactory;
        this.viewerRewardClient = viewerRewardClient;
        this.viewerRewardTimeoutClient = viewerRewardTimeoutClient;
    }

    public SimulationStartResult run(SimulationStartRequest request, String runId, String parentTraceId) {
        int viewerCount = defaultInt(request.getViewerCount(), properties.getDefaultViewerCount());
        int streamerCount = defaultInt(request.getStreamerCount(), properties.getDefaultStreamerCount());
        int targetQps = defaultInt(request.getQps(), properties.getDefaultQps());
        int durationSeconds = resolveDurationSeconds(request);
        double failureRate = defaultDouble(request.getFailureRate(), 0.0);
        double timeoutRate = defaultDouble(request.getTimeoutRate(), 0.0);
        String mode = request.getMode() == null || request.getMode().isBlank() ? MODE_FIXED : request.getMode().toUpperCase();

        int stepQps = defaultInt(request.getStepQps(), Math.max(targetQps / 5, 1));
        int stepDurationSeconds = defaultInt(request.getStepDurationSeconds(), 5);

        long startMillis = System.currentTimeMillis();
        long endMillis = startMillis + durationSeconds * 1000L;

        SimulationMetricsCollector metrics = new SimulationMetricsCollector(properties.getMaxFailureSamples());
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();

        int poolSize = properties.getThreadPoolSize();
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        int initialQps = MODE_STEP.equals(mode) ? stepQps : targetQps;
        QpsRateLimiter rateLimiter = new QpsRateLimiter(initialQps);

        long nextStepAt = startMillis + stepDurationSeconds * 1000L;
        int currentStepQps = initialQps;

        try {
            while (System.currentTimeMillis() < endMillis) {
                if (MODE_STEP.equals(mode)) {
                    long now = System.currentTimeMillis();
                    if (now >= nextStepAt && currentStepQps < targetQps) {
                        currentStepQps = Math.min(currentStepQps + stepQps, targetQps);
                        rateLimiter.setQps(currentStepQps);
                        nextStepAt = now + stepDurationSeconds * 1000L;
                        log.info("[runId={}] step up qps to {}", runId, currentStepQps);
                    }
                }

                rateLimiter.acquire();
                int index = sequence.getAndIncrement();
                metrics.recordRequested();
                inFlight.incrementAndGet();

                executor.submit(() -> executeRequest(
                        request, runId, parentTraceId, index, viewerCount, streamerCount,
                        failureRate, timeoutRate, metrics, inFlight));
            }

            awaitInFlight(inFlight, durationSeconds + 30);
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        long duration = System.currentTimeMillis() - startMillis;
        return metrics.buildResult(runId, parentTraceId, duration);
    }

    private void executeRequest(SimulationStartRequest request,
                                String runId,
                                String parentTraceId,
                                int index,
                                int viewerCount,
                                int streamerCount,
                                double failureRate,
                                double timeoutRate,
                                SimulationMetricsCollector metrics,
                                AtomicInteger inFlight) {
        long epochSecond = Instant.now().getEpochSecond();
        String traceId = "sim-" + runId + "-" + index;
        TraceContext.setTraceId(traceId);
        long start = System.currentTimeMillis();
        try {
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll < failureRate) {
                long latency = System.currentTimeMillis() - start;
                metrics.recordFailed(latency, epochSecond, traceId, "SIMULATED_FAILURE");
                log.warn("[traceId={}] simulated failure, index={}", traceId, index);
                return;
            }

            RewardRequest rewardRequest = mockDataFactory.next(index, viewerCount, streamerCount, request.getStreamerId());
            boolean useTimeoutClient = roll < failureRate + timeoutRate;
            ApiResponse<ViewerRewardClientResponse> response;
            try {
                if (useTimeoutClient) {
                    response = viewerRewardTimeoutClient.reward(rewardRequest);
                } else {
                    response = viewerRewardClient.reward(rewardRequest);
                }
            } catch (RetryableException exception) {
                long latency = System.currentTimeMillis() - start;
                metrics.recordTimeout(latency, epochSecond, traceId, exception.getMessage());
                log.warn("[traceId={}] request timeout, viewerId={}, message={}",
                        traceId, rewardRequest.getViewerId(), exception.getMessage());
                return;
            } catch (Exception exception) {
                long latency = System.currentTimeMillis() - start;
                if (isBlocked(exception, null)) {
                    metrics.recordBlocked(latency, epochSecond, traceId, exception.getMessage());
                } else {
                    metrics.recordFailed(latency, epochSecond, traceId, exception.getMessage());
                }
                log.warn("[traceId={}] request failed, viewerId={}, message={}",
                        traceId, rewardRequest.getViewerId(), exception.getMessage());
                return;
            }

            long latency = System.currentTimeMillis() - start;
            if (response != null && response.isSuccess()) {
                ViewerRewardClientResponse data = response.getData();
                String status = data != null ? data.getSettleStatus() : null;
                if ("SETTLED".equalsIgnoreCase(status)) {
                    metrics.recordSettled(latency, epochSecond);
                } else if ("DUPLICATE".equalsIgnoreCase(status)) {
                    metrics.recordDuplicate(latency, epochSecond);
                } else {
                    metrics.recordAccepted(latency, epochSecond);
                }
                log.debug("[traceId={}] reward sent viewerId={} latencyMs={} status={}",
                        traceId, rewardRequest.getViewerId(), latency, status);
            } else if (isBlocked(null, response)) {
                metrics.recordBlocked(latency, epochSecond, traceId, response != null ? response.getMessage() : "blocked");
            } else {
                metrics.recordFailed(latency, epochSecond, traceId,
                        response != null ? response.getCode() + ": " + response.getMessage() : "empty response");
            }
        } finally {
            TraceContext.clear();
            inFlight.decrementAndGet();
        }
    }

    private static boolean isBlocked(Exception exception, ApiResponse<?> response) {
        if (response != null) {
            if ("RATE_LIMITED".equalsIgnoreCase(response.getCode())) {
                return true;
            }
            String message = response.getMessage();
            if (message != null && (message.contains("限流") || message.contains("频繁"))) {
                return true;
            }
        }
        if (exception != null && exception.getMessage() != null) {
            String message = exception.getMessage();
            return message.contains("429") || message.contains("RATE_LIMITED") || message.contains("频繁");
        }
        return false;
    }

    private int resolveDurationSeconds(SimulationStartRequest request) {
        if (request.getDurationSeconds() != null) {
            return Math.max(request.getDurationSeconds(), 1);
        }
        if (request.getRequestCount() != null && request.getQps() != null && request.getQps() > 0) {
            return Math.max((int) Math.ceil(request.getRequestCount() * 1.0 / request.getQps()), 1);
        }
        String mode = request.getMode();
        if (mode != null && MODE_SUSTAINED.equalsIgnoreCase(mode)) {
            return 60;
        }
        return properties.getDefaultDurationSeconds();
    }

    private static int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static double defaultDouble(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static void awaitInFlight(AtomicInteger inFlight, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (inFlight.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
