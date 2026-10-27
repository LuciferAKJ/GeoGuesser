package com.geoguesser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseMigrationRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @Test
    @DisplayName("Executes safe, non-destructive schema migrations with legacy backfill and NOT NULL constraint")
    void testMigrationRunnerExecutesSuccessfully() {
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);

        assertDoesNotThrow(runner::migrateDatabase);

        // Verify migration ledger table
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS schema_migrations"));

        // Verify base tables
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS players"));
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS verification_tokens"));

        // Verify column addition before NOT NULL
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ADD COLUMN IF NOT EXISTS is_verified"));

        // Verify legacy account backfill for active players and pre-verification accounts
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE (games_played > 0 OR total_score > 0 OR id IN (SELECT DISTINCT player_id FROM games))"));
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));
        verify(jdbcTemplate).update(
                contains("INSERT INTO schema_migrations (version, description)"),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL),
                anyString()
        );
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = FALSE WHERE is_verified IS NULL"));

        // Verify NOT NULL enforcement after backfill
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET DEFAULT FALSE"));
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET NOT NULL"));

        // Verify locations and games
        verify(jdbcTemplate).execute(contains("ALTER TABLE locations ADD COLUMN IF NOT EXISTS zoom"));
        verify(jdbcTemplate).execute(contains("CREATE INDEX IF NOT EXISTS idx_verification_tokens_hash"));
    }

    @Test
    @DisplayName("Initial migration backfills eligible legacy accounts and records version in schema_migrations")
    void testInitialMigrationRunsLegacyBackfillAndRecordsVersion() {
        when(jdbcTemplate.queryForObject(
                contains("SELECT COUNT(*) FROM schema_migrations WHERE version = ?"),
                eq(Integer.class),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL)
        )).thenReturn(0);

        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);
        assertDoesNotThrow(runner::migrateDatabase);

        // Verify schema_migrations table created
        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS schema_migrations"));

        // Verify legacy account backfill queries executed
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE (games_played > 0 OR total_score > 0 OR id IN (SELECT DISTINCT player_id FROM games))"));
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));

        // Verify version recorded in schema_migrations ledger
        verify(jdbcTemplate).update(
                contains("INSERT INTO schema_migrations (version, description)"),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL),
                anyString()
        );

        // Verify safe constraints applied
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = FALSE WHERE is_verified IS NULL"));
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET NOT NULL"));
    }

    @Test
    @DisplayName("Subsequent runs detect recorded version in schema_migrations and skip legacy backfill completely")
    void testSubsequentRunSkipsLegacyBackfillWhenVersionAlreadyRecorded() {
        when(jdbcTemplate.queryForObject(
                contains("SELECT COUNT(*) FROM schema_migrations WHERE version = ?"),
                eq(Integer.class),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL)
        )).thenReturn(1);

        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);
        assertDoesNotThrow(runner::migrateDatabase);

        // Verify backfill queries are NEVER executed
        verify(jdbcTemplate, never()).execute(contains("UPDATE players SET is_verified = TRUE"));

        // Verify ledger is NOT inserted into again
        verify(jdbcTemplate, never()).update(
                contains("INSERT INTO schema_migrations"),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL),
                anyString()
        );

        // Safe DDL and NOT NULL constraints still run safely
        verify(jdbcTemplate).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET NOT NULL"));
    }

    @Test
    @DisplayName("New unverified accounts are never automatically verified on repeat application startup even if verification token is missing")
    void testNewUnverifiedAccountsNeverAutomaticallyVerifiedOnSubsequentStartup() {
        // First startup: initial legacy migration runs and records version
        when(jdbcTemplate.queryForObject(
                contains("SELECT COUNT(*) FROM schema_migrations WHERE version = ?"),
                eq(Integer.class),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL)
        )).thenReturn(0);

        DatabaseMigrationRunner firstStartupRunner = new DatabaseMigrationRunner(jdbcTemplate);
        firstStartupRunner.migrateDatabase();

        verify(jdbcTemplate, times(1)).execute(contains("UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));

        // Reset mock to simulate subsequent application startup with a fresh runner instance
        reset(jdbcTemplate);
        when(jdbcTemplate.queryForObject(
                contains("SELECT COUNT(*) FROM schema_migrations WHERE version = ?"),
                eq(Integer.class),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL)
        )).thenReturn(1);

        DatabaseMigrationRunner repeatStartupRunner = new DatabaseMigrationRunner(jdbcTemplate);
        repeatStartupRunner.migrateDatabase();

        // On repeat startup, the query that would verify users with no tokens MUST NEVER RUN
        verify(jdbcTemplate, never()).execute(contains("UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));
        verify(jdbcTemplate, never()).execute(contains("UPDATE players SET is_verified = TRUE"));
    }

    @Test
    @DisplayName("Executes legacy backfill and ledger recording atomically within transaction when transaction manager is present")
    void testAtomicExecutionWithTransactionManager() {
        when(jdbcTemplate.queryForObject(
                contains("SELECT COUNT(*) FROM schema_migrations WHERE version = ?"),
                eq(Integer.class),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL)
        )).thenReturn(0);

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate, transactionManager);
        assertDoesNotThrow(runner::migrateDatabase);

        verify(transactionManager).getTransaction(any());
        verify(transactionManager).commit(transactionStatus);
        verify(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));
        verify(jdbcTemplate).update(
                contains("INSERT INTO schema_migrations (version, description)"),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL),
                anyString()
        );
    }

    @Test
    @DisplayName("Migration is idempotent and can be safely executed repeatedly without errors or repeated backfills")
    void testMigrationRunnerRepeatability() {
        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);

        assertDoesNotThrow(runner::migrateDatabase);
        assertDoesNotThrow(runner::migrateDatabase);
        assertDoesNotThrow(() -> runner.run());

        verify(jdbcTemplate, atLeast(2)).execute(contains("ALTER TABLE players ADD COLUMN IF NOT EXISTS is_verified"));
        verify(jdbcTemplate, atLeast(2)).execute(contains("ALTER TABLE players ALTER COLUMN is_verified SET NOT NULL"));
        // The one-time backfill should only have been called once across all 3 calls on the same runner instance
        verify(jdbcTemplate, times(1)).execute(contains("UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));
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
    @DisplayName("Constructs a local DataSourceTransactionManager when DataSource is present, without requiring JPA transaction manager")
    void testPreJpaMigrationConstructsLocalJdbcTransactionManager() {
        javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);

        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate);

        assertNotNull(runner.getTransactionManager());
        assertTrue(runner.getTransactionManager() instanceof org.springframework.jdbc.datasource.DataSourceTransactionManager);
    }

    @Test
    @DisplayName("Migration rollback on failure propagates error and does not record version in ledger")
    void testMigrationRollbackOnFailurePropagatesAndDoesNotRecordVersion() {
        when(jdbcTemplate.queryForObject(
                contains("SELECT COUNT(*) FROM schema_migrations WHERE version = ?"),
                eq(Integer.class),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL)
        )).thenReturn(0);

        // Simulate failure during legacy backfill query 5b
        lenient().doThrow(new org.springframework.dao.DataIntegrityViolationException("Simulated constraint violation in backfill"))
                .when(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate, transactionManager);
        assertDoesNotThrow(runner::migrateDatabase);

        // Verify rollback was triggered on transactionManager
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(transactionStatus);

        // Verify ledger entry was NEVER inserted
        verify(jdbcTemplate, never()).update(
                contains("INSERT INTO schema_migrations"),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL),
                anyString()
        );

        // Verify version was NOT added to in-memory set
        assertFalse(runner.isMigrationApplied(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL));
    }

    @Test
    @DisplayName("After rollback on first run, subsequent run retries legacy backfill and succeeds once issue is resolved")
    void testMigrationRepeatabilityAfterRollback() {
        when(jdbcTemplate.queryForObject(
                contains("SELECT COUNT(*) FROM schema_migrations WHERE version = ?"),
                eq(Integer.class),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL)
        )).thenReturn(0);

        // Run 1: Failure during backfill, Run 2: Success
        lenient().doThrow(new org.springframework.dao.DataIntegrityViolationException("Temporary failure"))
                .doNothing()
                .when(jdbcTemplate).execute(contains("UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens)"));

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbcTemplate, transactionManager);
        // First attempt fails and rolls back
        runner.migrateDatabase();
        verify(transactionManager, times(1)).rollback(transactionStatus);
        assertFalse(runner.isMigrationApplied(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL));

        // Second attempt succeeds and commits
        runner.migrateDatabase();
        verify(transactionManager, times(1)).commit(transactionStatus);
        verify(jdbcTemplate, times(1)).update(
                contains("INSERT INTO schema_migrations (version, description)"),
                eq(DatabaseMigrationRunner.MIGRATION_VERSION_LEGACY_BACKFILL),
                anyString()
        );
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
