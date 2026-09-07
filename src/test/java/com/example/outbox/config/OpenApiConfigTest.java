package com.example.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    private OpenAPI openApi;

    @BeforeEach
    void setUp() {
        openApi = new OpenApiConfig().outboxOpenApi();
    }

    @Test
    void should_describeApi_when_beanIsCreated() {
        // given
        // when
        // then
        assertThat(openApi.getInfo())
                .extracting("title", "version")
                .containsExactly("Outbox Pattern API", "1.0.0");
    }

    @Test
    void should_documentTransactionalGuarantee_when_beanIsCreated() {
        // given
        // when
        // then
        assertThat(openApi.getInfo().getDescription()).contains("one transaction");
    }

    @Test
    void should_leaveServerUrlToRuntime_when_beanIsCreated() {
        // given
        // when
        // then
        assertThat(openApi.getServers()).isNullOrEmpty();
    }
}
