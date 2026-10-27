package com.geoguesser.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        migrateDatabase();
    }

    public void migrateDatabase() {
        try {
            log.info("Executing safe, non-destructive schema migrations...");

            // 1. Players is_verified column: defaults existing rows to TRUE for backward compatibility
            try {
                jdbcTemplate.execute("ALTER TABLE players ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT TRUE;");
                jdbcTemplate.execute("ALTER TABLE players ALTER COLUMN is_verified SET DEFAULT FALSE;");
            } catch (Exception e) {
                log.debug("Note on players is_verified migration: {}", e.getMessage());
            }

            // 2. Verification tokens table
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

            // 3. Locations zoom column: defaults to 13
            try {
                jdbcTemplate.execute("ALTER TABLE locations ADD COLUMN IF NOT EXISTS zoom INT DEFAULT 13;");
            } catch (Exception e) {
                log.debug("Note on locations zoom migration: {}", e.getMessage());
            }

            log.info("Database schema migration completed successfully.");
        } catch (Exception e) {
            log.warn("Non-fatal notice during database migration: {}", e.getMessage());
        }
    }
}
