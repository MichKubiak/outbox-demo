package com.example.outbox.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.qos.logback.classic.Level;
import com.example.outbox.support.LogCapture;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@ExtendWith(MockitoExtension.class)
class DatabaseHealthIndicatorTest {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    private DatabaseHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new DatabaseHealthIndicator(dataSource);
    }

    @Test
    void should_reportUp_when_connectionIsValid() throws Exception {
        // given
        givenValidConnection();

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_reportProductAndVersion_when_connectionIsValid() throws Exception {
        // given
        givenValidConnection();

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getDetails())
                .containsEntry("product", "PostgreSQL")
                .containsEntry("version", "16.4");
    }

    @Test
    void should_closeConnection_when_checkCompletes() throws Exception {
        // given
        givenValidConnection();

        // when
        indicator.health();

        // then
        then(connection).should().close();
    }

    @Test
    void should_reportDown_when_connectionIsNotValid() throws Exception {
        // given
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(VALIDATION_TIMEOUT_SECONDS)).willReturn(false);

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "connection validation failed");
    }

    @Test
    void should_closeConnection_when_connectionIsNotValid() throws Exception {
        // given
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(VALIDATION_TIMEOUT_SECONDS)).willReturn(false);

        // when
        indicator.health();

        // then
        then(connection).should().close();
    }

    @Test
    void should_reportDown_when_connectionCannotBeAcquired() throws Exception {
        // given
        given(dataSource.getConnection()).willThrow(new SQLException("FATAL: password authentication failed"));

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsExactly(entry("reason", "database unavailable"));
    }

    @Test
    void should_hideDatabaseFailureDetails_when_connectionCannotBeAcquired() throws Exception {
        // given
        given(dataSource.getConnection()).willThrow(new SQLException("FATAL: password authentication failed"));

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getDetails().values()).doesNotContain("FATAL: password authentication failed");
    }

    @Test
    void should_logError_when_connectionCannotBeAcquired() throws Exception {
        // given
        given(dataSource.getConnection()).willThrow(new SQLException("connection refused"));

        // when
        try (LogCapture logs = LogCapture.attachTo(DatabaseHealthIndicator.class)) {
            indicator.health();

            // then
            assertThat(logs.messagesAt(Level.ERROR)).containsExactly("Database health check failed");
        }
    }

    @Test
    void should_reportDown_when_validationCheckThrows() throws Exception {
        // given
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(VALIDATION_TIMEOUT_SECONDS)).willThrow(new SQLException("socket timeout"));

        // when
        Health health = indicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "database unavailable");
    }

    private void givenValidConnection() throws SQLException {
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.isValid(VALIDATION_TIMEOUT_SECONDS)).willReturn(true);
        given(connection.getMetaData()).willReturn(metaData);
        given(metaData.getDatabaseProductName()).willReturn("PostgreSQL");
        given(metaData.getDatabaseProductVersion()).willReturn("16.4");
    }
}
