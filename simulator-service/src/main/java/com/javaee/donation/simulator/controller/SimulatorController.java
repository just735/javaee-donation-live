package com.javaee.donation.simulator.controller;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.simulator.dto.SimulationStartRequest;
import com.javaee.donation.simulator.dto.SimulationStartResult;
import com.javaee.donation.simulator.service.SimulatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private final SimulatorService simulatorService;

    public SimulatorController(SimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @PostMapping("/start")
    public ApiResponse<SimulationStartResult> start(@RequestBody SimulationStartRequest request) {
        String traceId = TraceContext.getTraceId();
        return ApiResponse.success(traceId, simulatorService.start(request, traceId));
    }

    @GetMapping("/templates/default")
    public ApiResponse<SimulationStartRequest> template() {
        return ApiResponse.success(TraceContext.getTraceId(), simulatorService.defaultTemplate());
    }
}
