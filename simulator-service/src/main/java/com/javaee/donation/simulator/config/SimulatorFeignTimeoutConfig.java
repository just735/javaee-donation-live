package com.javaee.donation.simulator.config;

import feign.Request;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;

public class SimulatorFeignTimeoutConfig {

    @Bean
    public Request.Options timeoutOptions() {
        return new Request.Options(1000, TimeUnit.MILLISECONDS, 1, TimeUnit.MILLISECONDS, true);
    }
}
