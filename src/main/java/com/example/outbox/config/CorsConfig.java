package com.example.outbox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final long MAX_AGE_SECONDS = 3600;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/orders")
                .allowedOriginPatterns("*")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept", "X-Correlation-Id")
                .exposedHeaders("X-Correlation-Id", "Retry-After", "X-RateLimit-Remaining")
                .allowCredentials(false)
                .maxAge(MAX_AGE_SECONDS);
    }
}
