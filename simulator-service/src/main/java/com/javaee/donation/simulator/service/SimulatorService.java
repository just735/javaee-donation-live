package com.javaee.donation.simulator.service;

import com.javaee.donation.simulator.config.SimulatorProperties;
import com.javaee.donation.simulator.dto.SimulationStartRequest;
import com.javaee.donation.simulator.dto.SimulationStartResult;
import com.javaee.donation.simulator.engine.LoadTestEngine;
import com.javaee.donation.simulator.report.SimulationReportGenerator;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SimulatorService {

    private final LoadTestEngine loadTestEngine;
    private final SimulatorProperties properties;
    private final SimulationReportGenerator reportGenerator;
    private final SimulationResultStore resultStore;

    public SimulatorService(LoadTestEngine loadTestEngine,
                            SimulatorProperties properties,
                            SimulationReportGenerator reportGenerator,
                            SimulationResultStore resultStore) {
        this.loadTestEngine = loadTestEngine;
        this.properties = properties;
        this.reportGenerator = reportGenerator;
        this.resultStore = resultStore;
    }

    public SimulationStartResult start(SimulationStartRequest request, String parentTraceId) {
        SimulationStartRequest normalized = normalize(request);
        String runId = UUID.randomUUID().toString().substring(0, 8);
        SimulationStartResult result = loadTestEngine.run(normalized, runId, parentTraceId);
        result.setReportMarkdown(reportGenerator.generate(normalized, result));
        resultStore.save(result);
        return result;
    }

    public SimulationStartRequest defaultTemplate() {
        SimulationStartRequest request = new SimulationStartRequest();
        request.setStreamerCount(properties.getDefaultStreamerCount());
        request.setViewerCount(properties.getDefaultViewerCount());
        request.setQps(properties.getDefaultQps());
        request.setDurationSeconds(properties.getDefaultDurationSeconds());
        request.setMode("FIXED");
        request.setStepQps(100);
        request.setStepDurationSeconds(5);
        request.setFailureRate(0.0);
        request.setTimeoutRate(0.0);
        request.setStreamerId("streamer-1");
        return request;
    }

    private SimulationStartRequest normalize(SimulationStartRequest request) {
        SimulationStartRequest normalized = new SimulationStartRequest();
        normalized.setRequestCount(request.getRequestCount());
        normalized.setViewerCount(defaultInt(request.getViewerCount(), properties.getDefaultViewerCount()));
        normalized.setStreamerCount(defaultInt(request.getStreamerCount(), properties.getDefaultStreamerCount()));
        normalized.setQps(defaultInt(request.getQps(), properties.getDefaultQps()));
        normalized.setDurationSeconds(request.getDurationSeconds());
        normalized.setStreamerId(request.getStreamerId());
        normalized.setMode(request.getMode() == null || request.getMode().isBlank() ? "FIXED" : request.getMode());
        normalized.setStepQps(defaultInt(request.getStepQps(), Math.max(properties.getDefaultQps() / 5, 1)));
        normalized.setStepDurationSeconds(defaultInt(request.getStepDurationSeconds(), 5));
        normalized.setFailureRate(request.getFailureRate() == null ? 0.0 : request.getFailureRate());
        normalized.setTimeoutRate(request.getTimeoutRate() == null ? 0.0 : request.getTimeoutRate());
        return normalized;
    }

    private static int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
