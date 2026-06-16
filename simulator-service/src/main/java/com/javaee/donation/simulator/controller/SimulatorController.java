package com.javaee.donation.simulator.controller;

import com.javaee.donation.common.api.ApiResponse;
import com.javaee.donation.common.context.TraceContext;
import com.javaee.donation.common.model.SimulationRequest;
import com.javaee.donation.common.model.SimulationResult;
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
    public ApiResponse<SimulationResult> start(@RequestBody SimulationRequest request) {
        return ApiResponse.success(TraceContext.getTraceId(), simulatorService.start(request));
    }

    @GetMapping("/templates/default")
    public ApiResponse<SimulationRequest> template() {
        return ApiResponse.success(TraceContext.getTraceId(), simulatorService.defaultTemplate());
    }
}
