package com.example.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class CorsConfigTest {

    private Map<String, CorsConfiguration> configurations;

    @BeforeEach
    void setUp() {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        new CorsConfig().addCorsMappings(registry);
        configurations = registry.configurations();
    }

    @Test
    void should_configureOrdersPathOnly_when_corsMappingsAdded() {
        // given
        // when
        // then
        assertThat(configurations).containsOnlyKeys("/orders");
    }

    @Test
    void should_allowPostAndPreflight_when_corsMappingsAdded() {
        // given
        // when
        CorsConfiguration configuration = configurations.get("/orders");

        // then
        assertThat(configuration.getAllowedMethods()).containsExactly("POST", "OPTIONS");
    }

    @Test
    void should_rejectCredentials_when_corsMappingsAdded() {
        // given
        // when
        CorsConfiguration configuration = configurations.get("/orders");

        // then
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getAllowedOriginPatterns()).containsExactly("*");
    }

    @Test
    void should_exposeOperationalHeaders_when_corsMappingsAdded() {
        // given
        // when
        CorsConfiguration configuration = configurations.get("/orders");

        // then
        assertThat(configuration.getExposedHeaders())
                .containsExactlyInAnyOrder("X-Correlation-Id", "Retry-After", "X-RateLimit-Remaining");
    }

    @Test
    void should_cachePreflightForOneHour_when_corsMappingsAdded() {
        // given
        // when
        CorsConfiguration configuration = configurations.get("/orders");

        // then
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }

    private static final class ExposedCorsRegistry extends CorsRegistry {

        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
