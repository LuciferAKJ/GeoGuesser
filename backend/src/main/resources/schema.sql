-- Migration ledger table
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(64) PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at TIMESTAMP DEFAULT NOW()
);

-- Drop statements or rely on IF NOT EXISTS for safe initial execution
CREATE TABLE IF NOT EXISTS players (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    total_score BIGINT DEFAULT 0,
    games_played INT DEFAULT 0,
    best_score INT DEFAULT 0,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Base table verification_tokens (must exist before querying it for legacy backfill)
CREATE TABLE IF NOT EXISTS verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    expiry_date TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_verification_tokens_hash ON verification_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_verification_tokens_player ON verification_tokens(player_id);

-- Idempotent column addition for existing databases
ALTER TABLE players ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE;

-- One-time legacy account backfill (executed only when not recorded in migration ledger)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = 'V2__legacy_account_verification_backfill') THEN
        UPDATE players SET is_verified = TRUE WHERE (games_played > 0 OR total_score > 0 OR id IN (SELECT DISTINCT player_id FROM games));
        UPDATE players SET is_verified = TRUE WHERE id NOT IN (SELECT DISTINCT player_id FROM verification_tokens);
        INSERT INTO schema_migrations (version, description) VALUES ('V2__legacy_account_verification_backfill', 'One-time legacy player account verification backfill')
        ON CONFLICT (version) DO NOTHING;
    END IF;
END $$;

UPDATE players SET is_verified = FALSE WHERE is_verified IS NULL;
ALTER TABLE players ALTER COLUMN is_verified SET DEFAULT FALSE;
ALTER TABLE players ALTER COLUMN is_verified SET NOT NULL;

CREATE TABLE IF NOT EXISTS locations (
    id BIGSERIAL PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    country VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    image_url VARCHAR(1024),
    difficulty VARCHAR(10) CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')) DEFAULT 'MEDIUM',
    times_played INT DEFAULT 0,
    zoom INT DEFAULT 13
);

-- Idempotent column addition for existing databases
ALTER TABLE locations ADD COLUMN IF NOT EXISTS zoom INT DEFAULT 13;

CREATE TABLE IF NOT EXISTS games (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    guess_latitude DOUBLE PRECISION,
    guess_longitude DOUBLE PRECISION,
    distance_km DOUBLE PRECISION,
    score INT DEFAULT 0,
    time_taken_seconds INT,
    played_at TIMESTAMP DEFAULT NOW(),
    is_daily_challenge BOOLEAN DEFAULT FALSE
);

-- Performance and Query Optimization Indexes
CREATE INDEX IF NOT EXISTS idx_games_player ON games(player_id);
CREATE INDEX IF NOT EXISTS idx_games_location ON games(location_id);
CREATE INDEX IF NOT EXISTS idx_players_score ON players(total_score DESC);
CREATE INDEX IF NOT EXISTS idx_players_username ON players(username);
CREATE INDEX IF NOT EXISTS idx_locations_country ON locations(country);
CREATE INDEX IF NOT EXISTS idx_locations_difficulty ON locations(difficulty);