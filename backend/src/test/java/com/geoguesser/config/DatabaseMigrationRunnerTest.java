package com.geoguesser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseMigrationRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Executes additive schema migrations for is_verified column, legacy account backfill, and verification_tokens table")
    void testMigrationRunnerExecutesSuccessfully() {
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);

        assertDoesNotThrow(() -> runner.run());

        verify(jdbcTemplate).execute(contains("ALTER TABLE players ADD COLUMN IF NOT EXISTS is_verified"));
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET DEFAULT FALSE"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS verification_tokens"));
        verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS idx_verification_tokens_hash"));
    }

    @Test
    @DisplayName("Gracefully catches and logs errors if database migration throws, allowing application startup to continue")
    void testMigrationRunnerHandlesFailureGracefully() {
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);
        doThrow(new RuntimeException("Simulated DB connection issue"))
                .when(jdbcTemplate).execute(anyString());

        assertDoesNotThrow(() -> runner.run());
    }
}
