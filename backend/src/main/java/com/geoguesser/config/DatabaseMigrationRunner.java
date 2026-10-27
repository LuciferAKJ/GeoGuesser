package com.geoguesser.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Pre-JPA database migration runner.
 * Executes safe, idempotent schema updates and data backfills BEFORE Hibernate
 * initializes EntityManagerFactory, preventing startup crashes from NOT NULL constraints
 * on pre-existing database rows.
 */
@Component("databaseMigrationRunner")
@Order(1)
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        migrateDatabase();
    }

    @Override
    public void run(String... args) {
        migrateDatabase();
    }

    public synchronized void migrateDatabase() {
        try {
            log.info("Executing safe, non-destructive schema migrations before JPA initialization...");

            // 1. Base table players (if not already existing)
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS players ("
                                + "id BIGSERIAL PRIMARY KEY, "
                                + "username VARCHAR(50) UNIQUE NOT NULL, "
                                + "email VARCHAR(100) UNIQUE NOT NULL, "
                                + "password_hash VARCHAR(255) NOT NULL, "
                                + "total_score BIGINT DEFAULT 0, "
                                + "games_played INT DEFAULT 0, "
                                + "best_score INT DEFAULT 0, "
                                + "is_verified BOOLEAN DEFAULT FALSE, "
                                + "created_at TIMESTAMP DEFAULT NOW()"
                                + ");"
                );
            } catch (Exception e) {
                log.debug("Note on players table initialization: {}", e.getMessage());
            }

            // 2. Base table verification_tokens (must exist before querying it for legacy backfill)
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS verification_tokens ("
                                + "id BIGSERIAL PRIMARY KEY, "
                                + "token_hash VARCHAR(64) NOT NULL UNIQUE, "
                                + "player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE, "
                                + "expiry_date TIMESTAMP NOT NULL, "
                                + "used_at TIMESTAMP, "
                                + "created_at TIMESTAMP DEFAULT NOW()"
                                + ");"
                );
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_verification_tokens_hash ON verification_tokens(token_hash);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_verification_tokens_player ON verification_tokens(player_id);");
            } catch (Exception e) {
                log.debug("Note on verification_tokens migration: {}", e.getMessage());
            }

            // 3. Ensure is_verified column exists (without NOT NULL yet)
            try {
                jdbcTemplate.execute("ALTER TABLE players ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;");
            } catch (Exception e) {
                log.debug("Note on adding is_verified column: {}", e.getMessage());
            }

            // 4. Safe legacy account migration for any rows where is_verified IS NULL:
            // 4a. Active legacy players with games played / scores are verified
            try {
                jdbcTemplate.execute(
                        "UPDATE players SET is_verified = TRUE WHERE is_verified IS NULL AND (games_played > 0 OR total_score > 0);"
                );
            } catch (Exception e) {
                log.debug("Note on active player backfill: {}", e.getMessage());
            }

            // 4b. Legacy registered users created prior to verification system (no tokens exist in verification_tokens)
            try {
                jdbcTemplate.execute(
                        "UPDATE players SET is_verified = TRUE WHERE is_verified IS NULL AND id NOT IN (SELECT DISTINCT player_id FROM verification_tokens);"
                );
            } catch (Exception e) {
                log.debug("Note on legacy user backfill: {}", e.getMessage());
            }

            // 4c. Any remaining NULLs default to FALSE
            try {
                jdbcTemplate.execute("UPDATE players SET is_verified = FALSE WHERE is_verified IS NULL;");
            } catch (Exception e) {
                log.debug("Note on default FALSE for remaining nulls: {}", e.getMessage());
            }

            // 4d. Enforce default and NOT NULL constraint safely (now guaranteed 0 nulls)
            try {
                jdbcTemplate.execute("ALTER TABLE players ALTER COLUMN is_verified SET DEFAULT FALSE;");
                jdbcTemplate.execute("ALTER TABLE players ALTER COLUMN is_verified SET NOT NULL;");
            } catch (Exception e) {
                log.debug("Note on enforcing NOT NULL on is_verified: {}", e.getMessage());
            }

            // 5. Locations table and zoom column
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS locations ("
                                + "id BIGSERIAL PRIMARY KEY, "
                                + "latitude DOUBLE PRECISION NOT NULL, "
                                + "longitude DOUBLE PRECISION NOT NULL, "
                                + "country VARCHAR(100) NOT NULL, "
                                + "city VARCHAR(100) NOT NULL, "
                                + "image_url VARCHAR(1024), "
                                + "difficulty VARCHAR(10) CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')) DEFAULT 'MEDIUM', "
                                + "times_played INT DEFAULT 0, "
                                + "zoom INT DEFAULT 13"
                                + ");"
                );
                jdbcTemplate.execute("ALTER TABLE locations ADD COLUMN IF NOT EXISTS zoom INT DEFAULT 13;");
                jdbcTemplate.execute("UPDATE locations SET zoom = 13 WHERE zoom IS NULL;");
            } catch (Exception e) {
                log.debug("Note on locations zoom migration: {}", e.getMessage());
            }

            // 6. Games table and performance indexes
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS games ("
                                + "id BIGSERIAL PRIMARY KEY, "
                                + "player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE, "
                                + "location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE CASCADE, "
                                + "guess_latitude DOUBLE PRECISION, "
                                + "guess_longitude DOUBLE PRECISION, "
                                + "distance_km DOUBLE PRECISION, "
                                + "score INT DEFAULT 0, "
                                + "time_taken_seconds INT, "
                                + "played_at TIMESTAMP DEFAULT NOW(), "
                                + "is_daily_challenge BOOLEAN DEFAULT FALSE"
                                + ");"
                );
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_games_player ON games(player_id);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_games_location ON games(location_id);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_players_score ON players(total_score DESC);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_players_username ON players(username);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_locations_country ON locations(country);");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_locations_difficulty ON locations(difficulty);");
            } catch (Exception e) {
                log.debug("Note on games table and index migration: {}", e.getMessage());
            }

            log.info("Pre-JPA database schema migrations completed successfully.");
        } catch (Exception e) {
            log.warn("Non-fatal notice during database migration: {}", e.getMessage());
        }
    }
}
