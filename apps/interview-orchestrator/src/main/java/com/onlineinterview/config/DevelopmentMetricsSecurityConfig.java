package com.onlineinterview.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Enables unauthenticated Prometheus scraping only in workstation and shared-development
 * environments. UAT and production retain the application's authenticated security chain.
 */
@Configuration
@Profile({"local", "dev"})
public class DevelopmentMetricsSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain developmentMetricsSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.to(PrometheusScrapeEndpoint.class))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.disable())
                .sessionManagement(session -> session.disable())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
