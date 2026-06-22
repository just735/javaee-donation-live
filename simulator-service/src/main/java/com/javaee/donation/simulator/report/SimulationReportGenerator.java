package com.javaee.donation.simulator.report;

import com.javaee.donation.simulator.dto.FailureSample;
import com.javaee.donation.simulator.dto.SimulationStartRequest;
import com.javaee.donation.simulator.dto.SimulationStartResult;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SimulationReportGenerator {

    public String generate(SimulationStartRequest request, SimulationStartResult result) {
        StringBuilder report = new StringBuilder();
        report.append("# 压测报告\n\n");
        report.append("## 参数摘要\n\n");
        report.append("| 参数 | 值 |\n");
        report.append("|------|----|\n");
        report.append("| runId | ").append(result.getRunId()).append(" |\n");
        report.append("| traceId | ").append(result.getTraceId()).append(" |\n");
        report.append("| 主播数 | ").append(nullSafe(request.getStreamerCount())).append(" |\n");
        report.append("| 观众数 | ").append(nullSafe(request.getViewerCount())).append(" |\n");
        report.append("| 目标 QPS | ").append(nullSafe(request.getQps())).append(" |\n");
        report.append("| 持续时间(秒) | ").append(nullSafe(request.getDurationSeconds())).append(" |\n");
        report.append("| 模式 | ").append(nullSafe(request.getMode())).append(" |\n");
        report.append("| 失败率模拟 | ").append(nullSafe(request.getFailureRate())).append(" |\n");
        report.append("| 超时率模拟 | ").append(nullSafe(request.getTimeoutRate())).append(" |\n\n");

        report.append("## 核心指标\n\n");
        report.append("| 指标 | 值 |\n");
        report.append("|------|----|\n");
        report.append("| 请求总数 | ").append(result.getRequestedCount()).append(" |\n");
        report.append("| 成功数 | ").append(result.getSuccessCount()).append(" |\n");
        report.append("| 已受理数(ACCEPTED) | ").append(result.getAcceptedCount()).append(" |\n");
        report.append("| 已结算数(SETTLED) | ").append(result.getSettledCount()).append(" |\n");
        report.append("| 幂等重复数(DUPLICATE) | ").append(result.getDuplicateCount()).append(" |\n");
        report.append("| 失败数 | ").append(result.getFailedCount()).append(" |\n");
        report.append("| 超时数 | ").append(result.getTimeoutCount()).append(" |\n");
        report.append("| 限流数 | ").append(result.getBlockedCount()).append(" |\n");
        report.append("| 成功率 | ").append(result.getSuccessRate()).append(" |\n");
        report.append("| 异常比例 | ").append(result.getErrorRatio()).append(" |\n");
        report.append("| 实际 QPS | ").append(result.getActualQps()).append(" |\n");
        report.append("| 平均延迟(ms) | ").append(result.getAvgLatencyMs()).append(" |\n");
        report.append("| P95延迟(ms) | ").append(result.getP95LatencyMs()).append(" |\n");
        report.append("| P99延迟(ms) | ").append(result.getP99LatencyMs()).append(" |\n");
        report.append("| 总耗时(ms) | ").append(result.getDurationMillis()).append(" |\n\n");

        if (result.getFailureSamples() != null && !result.getFailureSamples().isEmpty()) {
            report.append("## 失败采样\n\n");
            report.append("| traceId | 原因 | 延迟(ms) |\n");
            report.append("|---------|------|----------|\n");
            for (FailureSample sample : result.getFailureSamples()) {
                report.append("| ").append(sample.getTraceId())
                        .append(" | ").append(escape(sample.getReason()))
                        .append(" | ").append(sample.getLatencyMs()).append(" |\n");
            }
            report.append("\n");
        }

        report.append("## 调优建议\n\n");
        if (result.getActualQps() != null && request.getQps() != null
                && result.getActualQps() < request.getQps() * 0.96) {
            report.append("- 实际 QPS 低于目标，可增大 `simulator.thread-pool-size` 或检查 Feign 连接池。\n");
        }
        if (result.getBlockedCount() != null && result.getBlockedCount() > 0) {
            report.append("- 存在下游限流，请检查 viewer-service 的 `viewer.reward.qps-limit` 配置后再做全链路验收。\n");
        }
        if (result.getAcceptedCount() != null && result.getSettledCount() != null
                && result.getAcceptedCount() > result.getSettledCount()) {
            report.append("- 当前更多请求处于已受理未同步结算状态，需结合 viewer 任务表与 finance 落账结果综合验收。\n");
        }
        if (result.getTimeoutCount() != null && result.getTimeoutCount() > 0) {
            report.append("- 存在超时请求，可检查 viewer/finance 服务响应时间或网络状况。\n");
        }
        report.append("- 可通过 traceId 在各服务日志中检索单次请求。\n");

        return report.toString();
    }

    private static Object nullSafe(Object value) {
        return value == null ? "-" : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").lines().collect(Collectors.joining(" "));
    }
}
