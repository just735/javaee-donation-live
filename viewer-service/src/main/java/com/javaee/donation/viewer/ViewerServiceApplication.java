package com.javaee.donation.viewer;

import com.javaee.donation.viewer.config.ViewerRewardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients
@EnableDiscoveryClient
@EnableScheduling
@EnableConfigurationProperties(ViewerRewardProperties.class)
@SpringBootApplication(scanBasePackages = "com.javaee.donation")
public class ViewerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ViewerServiceApplication.class, args);
    }
}
