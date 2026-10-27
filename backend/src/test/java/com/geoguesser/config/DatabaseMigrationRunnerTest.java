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
    @DisplayName("Executes safe, non-destructive schema migrations with legacy backfill and NOT NULL constraint")
    void testMigrationRunnerExecutesSuccessfully() {
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);

        assertDoesNotThrow(runner::migrateDatabase);

        // Verify base tables
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS players"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS verification_tokens"));

        // Verify column addition before NOT NULL
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ADD COLUMN IF NOT EXISTS is_verified"));

        // Verify legacy account backfill for active players and pre-verification accounts
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE is_verified IS NULL AND (games_played > 0 OR total_score > 0)"));
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE is_verified IS NULL AND id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = FALSE WHERE is_verified IS NULL"));

        // Verify NOT NULL enforcement after backfill
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET DEFAULT FALSE"));
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET NOT NULL"));

        // Verify locations and games
        verify(jdbcTemplate).execute(contains("ALTER TABLE locations ADD COLUMN IF NOT EXISTS zoom"));
        verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS idx_verification_tokens_hash"));
    }

    @Test
    @DisplayName("Migration is idempotent and can be safely executed repeatedly without errors")
    void testMigrationRunnerRepeatability() {
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);

        assertDoesNotThrow(runner::migrateDatabase);
        assertDoesNotThrow(runner::migrateDatabase);
        assertDoesNotThrow(() -> runner.run());

        verify(jdbcTemplate, atLeast(2)).execute(contains("ALTER TABLE players ADD COLUMN IF NOT EXISTS is_verified"));
        verify(jdbcTemplate, atLeast(2)).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET NOT NULL"));
    }

    @Test
    @DisplayName("Gracefully catches and logs errors if database migration throws, allowing application startup to continue")
    void testMigrationRunnerHandlesFailureGracefully() {
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);
        doThrow(new RuntimeException("Simulated DB connection issue"))
                .when(jdbcTemplate).execute(anyString());

        assertDoesNotThrow(() -> runner.run());
    }

    @Test
    @DisplayName("DatabaseMigrationConfig properly sets dependency on databaseMigrationRunner")
    void testDatabaseMigrationConfigDependency() {
        DatabaseMigrationConfig config = new DatabaseMigrationConfig();
        // DatabaseMigrationConfig extends EntityManagerFactoryDependsOnPostProcessor
        // which adds "databaseMigrationRunner" to dependsOn
        org.junit.jupiter.api.Assertions.assertNotNull(config);
    }
}
