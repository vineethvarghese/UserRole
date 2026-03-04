package com.userrole.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the RateLimitInterceptor across all API paths.
 */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final int anonymousRequestsPerMinute;
    private final int authenticatedRequestsPerMinute;

    public RateLimitConfig(
            @Value("${ratelimit.anonymous.requests-per-minute:30}") int anonymousRequestsPerMinute,
            @Value("${ratelimit.authenticated.requests-per-minute:120}") int authenticatedRequestsPerMinute
    ) {
        this.anonymousRequestsPerMinute = anonymousRequestsPerMinute;
        this.authenticatedRequestsPerMinute = authenticatedRequestsPerMinute;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
                new RateLimitInterceptor(anonymousRequestsPerMinute, authenticatedRequestsPerMinute)
        ).addPathPatterns("/api/v1/**");
    }
}
