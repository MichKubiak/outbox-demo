package com.example.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.outbox.repository.OrderRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

class FlywayMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private Environment environment;

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(strings = {"orders", "outbox_events"})
    void should_createOrdersAndOutboxTables_when_migrationsApplied(String table) {
        // given
        // when
        Integer found = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = ?
                """, Integer.class, table);

        // then
        assertThat(found).isEqualTo(1);
    }

    @Test
    void should_recordAppliedVersion_when_migrationsApplied() {
        // given
        // when
        Map<String, Object> history = jdbcTemplate.queryForMap(
                "select version, success from flyway_schema_history where installed_rank = 1");

        // then
        assertThat(history).containsEntry("version", "1").containsEntry("success", true);
    }

    @ParameterizedTest(name = "{index}: {0}.{1} -> {2}")
    @CsvSource({
            "orders,customer_id,character varying",
            "orders,created_at,timestamp with time zone",
            "outbox_events,payload,jsonb",
            "outbox_events,processed_at,timestamp with time zone",
            "outbox_events,attempts,integer"
    })
    void should_useDeclaredColumnTypes_when_migrationsApplied(String table, String column, String expectedType) {
        // given
        // when
        String dataType = jdbcTemplate.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column);

        // then
        assertThat(dataType).isEqualTo(expectedType);
    }

    @Test
    void should_validateSchema_when_hibernateStarts() {
        // given
        // when
        // then
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(orderRepository.count()).isZero();
    }
}
