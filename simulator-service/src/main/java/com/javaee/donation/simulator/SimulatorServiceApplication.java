package com.javaee.donation.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.javaee.donation")
public class SimulatorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorServiceApplication.class, args);
    }
}
