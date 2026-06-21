package com.javaee.donation.simulator.service;

import com.javaee.donation.simulator.dto.SimulationStartResult;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class SimulationResultStore {

    private final AtomicReference<SimulationStartResult> latestResult = new AtomicReference<>();

    public void save(SimulationStartResult result) {
        latestResult.set(result);
    }

    public SimulationStartResult getLatest() {
        return latestResult.get();
    }
}
