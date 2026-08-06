-- Run this once to set up the database
-- Or let Spring Boot auto-create via spring.jpa.hibernate.ddl-auto=update

CREATE TABLE IF NOT EXISTS players (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    total_score BIGINT DEFAULT 0,
    games_played INT DEFAULT 0,
    best_score INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS locations (
    id BIGSERIAL PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    country VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    image_url VARCHAR(1024),
    difficulty VARCHAR(10) DEFAULT 'MEDIUM',
    times_played INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS games (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT REFERENCES players(id),
    location_id BIGINT REFERENCES locations(id),
    guess_latitude DOUBLE PRECISION,
    guess_longitude DOUBLE PRECISION,
    distance_km DOUBLE PRECISION,
    score INT DEFAULT 0,
    time_taken_seconds INT,
    played_at TIMESTAMP DEFAULT NOW(),
    is_daily_challenge BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_games_player ON games(player_id);
CREATE INDEX IF NOT EXISTS idx_players_score ON players(total_score DESC);
