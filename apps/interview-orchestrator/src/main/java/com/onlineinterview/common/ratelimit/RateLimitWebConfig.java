package com.onlineinterview.common.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link RateLimitInterceptor} on the API surface and owns its configuration.
 * Enabling the properties here (rather than a component-scanned {@code @Component}) keeps the
 * whole feature self-contained, so it loads correctly in {@code @WebMvcTest} slices too.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitWebConfig implements WebMvcConfigurer {
    private final RateLimitInterceptor interceptor;

    public RateLimitWebConfig(RateLimitProperties properties) {
        this.interceptor = new RateLimitInterceptor(properties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/v1/**");
    }
}
