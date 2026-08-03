package com.onlineinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import com.onlineinterview.knowledge.infrastructure.ObjectStorageProperties;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class OnlineInterviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlineInterviewApplication.class, args);
    }

    /** Daemon-threaded scheduler so scheduled tasks never keep a non-web JVM alive — the
     *  schema-validation init container runs this app with web-application-type=none and must
     *  be able to exit cleanly once validation passes. */
    @Bean
    TaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
