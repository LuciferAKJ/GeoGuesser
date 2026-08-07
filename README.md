# GeoGuesser — Full Stack Java

A GeoGuesser-style geography game with JavaFX frontend + Spring Boot REST API + PostgreSQL.

---

## Tech Stack

| Layer    | Technology              |
|----------|-------------------------|
| Frontend | JavaFX 21               |
| Backend  | Spring Boot 3.2         |
| Database | PostgreSQL 15+          |
| Maps     | Mapbox Static Images API|
| Auth     | JWT (jjwt)              |

---

## Project Structure

```
geoguesser/
├── backend/               ← Spring Boot REST API
│   └── src/main/java/com/geoguesser/
│       ├── GeoGuesserApplication.java
│       ├── model/         ← Location, Player, Game entities
│       ├── repository/    ← Spring Data JPA repos
│       ├── service/       ← ScoringService, LocationService, GameService
│       ├── controller/    ← REST endpoints
│       └── config/        ← JWT, Security, DataSeeder
│
└── frontend/              ← JavaFX desktop client
    └── src/main/java/com/geoguesser/
        ├── App.java
        ├── service/ApiClient.java
        └── ui/screens/
            ├── LoginScreen.java
            ├── RegisterScreen.java
            ├── GameScreen.java
            └── LeaderboardScreen.java
```

---

## Setup Guide

### 1. Prerequisites

- Java 17+
- Maven
- PostgreSQL 15+
- Mapbox account (free tier is fine)

### 2. Database

```bash
psql -U postgres
CREATE DATABASE geoguesser;
\q
```

Then run the schema:
```bash
psql -U postgres -d geoguesser -f backend/src/main/resources/schema.sql
```

### 3. Configure the Backend

Edit `backend/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/geoguesser
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD

jwt.secret=GENERATE_WITH: openssl rand -base64 64

mapbox.access-token=YOUR_MAPBOX_PUBLIC_TOKEN
```

### 4. Run the Backend

```bash
cd backend
mvn spring-boot:run
```

Backend starts on http://localhost:8080

The DataSeeder auto-populates 10 starter locations on first run.

### 5. Run the Frontend

```bash
cd frontend
mvn javafx:run
```

---

## API Endpoints

| Method | Path                         | Auth? | Description            |
|--------|------------------------------|-------|------------------------|
| POST   | /api/auth/register           | No    | Create account         |
| POST   | /api/auth/login              | No    | Login → JWT token      |
| GET    | /api/location/random         | No    | Get random location    |
| GET    | /api/location/random?difficulty=HARD | No | Filtered location |
| GET    | /api/location/daily          | No    | Today's challenge      |
| GET    | /api/location/batch?count=5  | No    | Multi-round game       |
| POST   | /api/game/guess?playerId=1   | Yes   | Submit a guess         |
| GET    | /api/game/history?playerId=1 | Yes   | Recent games           |
| GET    | /api/player/leaderboard      | No    | Top 10 players         |
| GET    | /api/player/{id}/stats       | Yes   | Player stats           |

---

## Adding More Locations

POST to `/api/location` with JSON:

```json
{
  "latitude": 27.1751,
  "longitude": 78.0421,
  "country": "India",
  "city": "Agra",
  "difficulty": "MEDIUM"
}
```

The `imageUrl` is auto-generated from Mapbox using the coordinates.

---

## Scoring

| Distance    | Score          |
|-------------|----------------|
| < 1 km      | 5000 (perfect) |
| 1–10 km     | 4500–5000      |
| 10–50 km    | 3000–4500      |
| 50–100 km   | 2000–3000      |
| > 100 km    | max(0, 5000 – dist×2) |

Plus up to **+500 time bonus** for guessing within 10 seconds.

---

## What This Demonstrates (for interviews)

- **REST API design** — clean endpoints, DTOs, proper HTTP status codes
- **Spring Boot** — JPA, Security, JWT, Bean injection
- **PostgreSQL** — schema design, native queries, indexing
- **Haversine formula** — geospatial distance calculation
- **JavaFX** — scene graph, async image loading, Timeline animation
- **Full-stack integration** — JavaFX ↔ Spring Boot over HTTP
- **Auth flow** — registration, BCrypt hashing, JWT tokens



## Environment Variables

Create a local configuration using the following variables:

- DB_URL
- DB_USERNAME
- DB_PASSWORD
- JWT_SECRET
- MAPBOX_ACCESS_TOKEN