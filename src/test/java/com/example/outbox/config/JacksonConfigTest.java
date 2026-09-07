package com.example.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JacksonConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().isoDateTimeCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void should_registerJavaTimeModule_when_customizerApplied() {
        // given
        // when
        // then
        assertThat(objectMapper.getRegisteredModuleIds())
                .contains("jackson-datatype-jsr310");
    }

    @Test
    void should_writeIsoTimestamp_when_valueIsOffsetDateTime() throws Exception {
        // given
        OffsetDateTime value = OffsetDateTime.of(2026, 9, 7, 10, 15, 30, 0, ZoneOffset.UTC);

        // when
        String json = objectMapper.writeValueAsString(value);

        // then
        assertThat(json).isEqualTo("\"2026-09-07T10:15:30Z\"");
    }

    @Test
    void should_writeIsoTimestamp_when_valueIsInstant() throws Exception {
        // given
        Instant value = Instant.parse("2026-09-07T10:15:30Z");

        // when
        String json = objectMapper.writeValueAsString(value);

        // then
        assertThat(json).isEqualTo("\"2026-09-07T10:15:30Z\"");
    }
}
