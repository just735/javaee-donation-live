package com.javaee.donation.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.javaee.donation")
public class ViewerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ViewerServiceApplication.class, args);
    }
}
