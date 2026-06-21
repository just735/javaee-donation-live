package com.javaee.donation.simulator.controller;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.simulator.dto.SimulationStartResult;
import com.javaee.donation.simulator.service.SimulationResultStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorResultController {

    private final SimulationResultStore resultStore;

    public SimulatorResultController(SimulationResultStore resultStore) {
        this.resultStore = resultStore;
    }

    @GetMapping("/results/latest")
    public ApiResponse<SimulationStartResult> latestResult() {
        SimulationStartResult result = resultStore.getLatest();
        if (result == null) {
            return ApiResponse.fail(TraceContext.getTraceId(), "NOT_FOUND", "暂无压测结果");
        }
        return ApiResponse.success(TraceContext.getTraceId(), result);
    }

    @GetMapping("/report/latest")
    public ApiResponse<String> latestReport() {
        SimulationStartResult result = resultStore.getLatest();
        if (result == null || result.getReportMarkdown() == null) {
            return ApiResponse.fail(TraceContext.getTraceId(), "NOT_FOUND", "暂无压测报告");
        }
        return ApiResponse.success(TraceContext.getTraceId(), result.getReportMarkdown());
    }
}
