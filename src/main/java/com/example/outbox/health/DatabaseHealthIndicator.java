package com.example.outbox.health;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return Health.down().withDetail("reason", "connection validation failed").build();
            }
            return Health.up()
                    .withDetail("product", connection.getMetaData().getDatabaseProductName())
                    .withDetail("version", connection.getMetaData().getDatabaseProductVersion())
                    .build();
        } catch (SQLException e) {
            log.error("Database health check failed", e);
            return Health.down().withDetail("reason", "database unavailable").build();
        }
    }
}
