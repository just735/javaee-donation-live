package com.javaee.donation.viewer.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "rewardNotifyExecutor")
    public Executor rewardNotifyExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
