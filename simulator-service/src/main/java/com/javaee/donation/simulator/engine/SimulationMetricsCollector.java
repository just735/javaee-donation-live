package com.javaee.donation.simulator.engine;

import com.javaee.donation.simulator.dto.FailureSample;
import com.javaee.donation.simulator.dto.SimulationStartResult;
import com.javaee.donation.simulator.dto.TimeSeriesPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class SimulationMetricsCollector {

    private final int maxFailureSamples;
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger acceptedCount = new AtomicInteger();
    private final AtomicInteger settledCount = new AtomicInteger();
    private final AtomicInteger duplicateCount = new AtomicInteger();
    private final AtomicInteger failedCount = new AtomicInteger();
    private final AtomicInteger timeoutCount = new AtomicInteger();
    private final AtomicInteger blockedCount = new AtomicInteger();
    private final AtomicInteger requestedCount = new AtomicInteger();
    private final LongAdder latencySumMs = new LongAdder();
    private final CopyOnWriteArrayList<Long> latenciesMs = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<FailureSample> failureSamples = new CopyOnWriteArrayList<>();
    private final Map<Long, SecondBucket> secondBuckets = new ConcurrentHashMap<>();

    public SimulationMetricsCollector(int maxFailureSamples) {
        this.maxFailureSamples = maxFailureSamples;
    }

    public void recordRequested() {
        requestedCount.incrementAndGet();
    }

    public void recordAccepted(long latencyMs, long epochSecond) {
        acceptedCount.incrementAndGet();
        successCount.incrementAndGet();
        recordLatency(latencyMs, epochSecond, true);
    }

    public void recordSettled(long latencyMs, long epochSecond) {
        settledCount.incrementAndGet();
        successCount.incrementAndGet();
        recordLatency(latencyMs, epochSecond, true);
    }

    public void recordDuplicate(long latencyMs, long epochSecond) {
        duplicateCount.incrementAndGet();
        successCount.incrementAndGet();
        recordLatency(latencyMs, epochSecond, true);
    }

    public void recordFailed(long latencyMs, long epochSecond, String traceId, String reason) {
        failedCount.incrementAndGet();
        recordLatency(latencyMs, epochSecond, false);
        addFailureSample(traceId, reason, latencyMs);
    }

    public void recordTimeout(long latencyMs, long epochSecond, String traceId, String reason) {
        timeoutCount.incrementAndGet();
        recordLatency(latencyMs, epochSecond, false);
        addFailureSample(traceId, reason, latencyMs);
    }

    public void recordBlocked(long latencyMs, long epochSecond, String traceId, String reason) {
        blockedCount.incrementAndGet();
        recordLatency(latencyMs, epochSecond, false);
        addFailureSample(traceId, reason, latencyMs);
    }

    private void recordLatency(long latencyMs, long epochSecond, boolean success) {
        latencySumMs.add(latencyMs);
        latenciesMs.add(latencyMs);
        secondBuckets.computeIfAbsent(epochSecond, SecondBucket::new).record(latencyMs, success);
    }

    private void addFailureSample(String traceId, String reason, long latencyMs) {
        if (failureSamples.size() >= maxFailureSamples) {
            return;
        }
        failureSamples.add(new FailureSample(traceId, reason, latencyMs));
    }

    public SimulationStartResult buildResult(String runId, String traceId, long durationMillis) {
        SimulationStartResult result = new SimulationStartResult();
        int requested = requestedCount.get();
        int success = successCount.get();
        int failed = failedCount.get();
        int timeout = timeoutCount.get();
        int blocked = blockedCount.get();
        int errors = failed + timeout + blocked;

        result.setRunId(runId);
        result.setTraceId(traceId);
        result.setRequestedCount(requested);
        result.setSuccessCount(success);
        result.setAcceptedCount(acceptedCount.get());
        result.setSettledCount(settledCount.get());
        result.setDuplicateCount(duplicateCount.get());
        result.setFailedCount(failed);
        result.setTimeoutCount(timeout);
        result.setBlockedCount(blocked);
        result.setDurationMillis(durationMillis);
        result.setSuccessRate(requested == 0 ? 0.0 : round4(success * 1.0 / requested));
        result.setErrorRatio(requested == 0 ? 0.0 : round4(errors * 1.0 / requested));
        result.setActualQps(durationMillis <= 0 ? 0.0 : round4(requested * 1000.0 / durationMillis));

        if (requested > 0) {
            result.setAvgLatencyMs(latencySumMs.sum() / requested);
        } else {
            result.setAvgLatencyMs(0L);
        }

        List<Long> sorted = new ArrayList<>(latenciesMs);
        Collections.sort(sorted);
        result.setP95LatencyMs(percentile(sorted, 95));
        result.setP99LatencyMs(percentile(sorted, 99));
        result.setLatencySeries(buildTimeSeries());
        result.setFailureSamples(new ArrayList<>(failureSamples));
        result.setMessage("simulation finished");
        return result;
    }

    private List<TimeSeriesPoint> buildTimeSeries() {
        List<Long> seconds = new ArrayList<>(secondBuckets.keySet());
        Collections.sort(seconds);
        List<TimeSeriesPoint> series = new ArrayList<>();
        for (Long second : seconds) {
            SecondBucket bucket = secondBuckets.get(second);
            series.add(new TimeSeriesPoint(
                    second,
                    bucket.qps(),
                    bucket.avgLatencyMs(),
                    bucket.errorCount.get(),
                    bucket.successCount.get()));
        }
        return series;
    }

    private static long percentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static final class SecondBucket {
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicInteger successCount = new AtomicInteger();
        private final AtomicInteger errorCount = new AtomicInteger();
        private final LongAdder latencySum = new LongAdder();

        private SecondBucket(Long epochSecond) {
        }

        private void record(long latencyMs, boolean success) {
            count.incrementAndGet();
            latencySum.add(latencyMs);
            if (success) {
                successCount.incrementAndGet();
            } else {
                errorCount.incrementAndGet();
            }
        }

        private double qps() {
            return count.get();
        }

        private long avgLatencyMs() {
            int total = count.get();
            return total == 0 ? 0L : latencySum.sum() / total;
        }
    }
}
