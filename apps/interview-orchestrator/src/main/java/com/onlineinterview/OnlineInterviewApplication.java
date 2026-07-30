package com.onlineinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.onlineinterview.knowledge.infrastructure.ObjectStorageProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class OnlineInterviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlineInterviewApplication.class, args);
    }
}
