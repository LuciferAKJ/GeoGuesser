# 🌍 GeoGuesser — Full Stack Geography Game

A modern, immersive GeoGuesser-style web game built with a Spring Boot REST API, PostgreSQL, JWT authentication, Mapbox satellite imagery, and an interactive Leaflet world map with zoom-tiered country labels.

---

## 🚀 Tech Stack

| Layer | Technology |
|---|---|
| **Frontend** | Vanilla HTML5, Modern CSS3, JavaScript (ES6+) |
| **Mapping & UI** | Leaflet, Esri Dark Canvas, Mapbox Satellite Imagery |
| **Backend** | Spring Boot 3.2, Java 17, Maven |
| **Security** | Spring Security, JWT (jjwt 0.11.5), BCrypt |
| **Database** | PostgreSQL 15+ (Spring Data JPA / Hibernate) |

---

## 📁 Project Structure

```
geoguesser/
├── backend/                              ← Spring Boot REST API
│   ├── pom.xml                           ← Maven dependencies & build plugins
│   └── src/
│       ├── main/java/com/geoguesser/
│       │   ├── GeoGuesserApplication.java
│       │   ├── config/                   ← SecurityConfig, CorsConfig, JwtFilter, JwtUtil, DataSeeder
│       │   ├── controller/               ← AuthController, GameController, LocationController, PlayerController
│       │   ├── dto/                      ← GuessRequest, GameHistoryDTO, LeaderboardDTO, etc.
│       │   ├── model/                    ← Game, Location, Player entities
│       │   ├── repository/               ← Spring Data JPA repositories
│       │   └── service/                  ← ScoringService, GameService, LocationService, PlayerService
│       ├── main/resources/
│       │   ├── application.properties     ← Default local development configuration
│       │   ├── application-prod.properties← Production profile (requires cloud env vars)
│       │   ├── schema.sql                ← PostgreSQL schema DDL
│       │   └── seed.sql                  ← Idempotent initial global locations seed
│       └── test/java/com/geoguesser/     ← JUnit & scoring test suite
│
├── frontend/                             ← Web client (vanilla static assets)
│   ├── index.html                        ← Semantic game layout & auth screens
│   ├── style.css                         ← Dark theme design system & responsive layout
│   ├── app.js                            ← Game engine, map handling, API client
│   ├── config.js                         ← Configurable API URL configuration point
│   └── countries.json                    ← Priority-ranked world country dataset
│
├── .gitignore                            ← Standard exclusions (build target, secrets, logs)
└── README.md                             ← Project overview & deployment documentation
```

---

## 🛠️ Local Development Setup

### 1. Prerequisites
- **Java 17+** (JDK 17)
- **Maven 3.8+**
- **PostgreSQL 15+** running locally on port 5432
- **Mapbox Account** (free tier token for satellite imagery)

### 2. Database Initialization
Create a database named `geoguesser`:
```bash
psql -U postgres -c "CREATE DATABASE geoguesser;"
```
*(Hibernate automatically creates tables on startup, and `DataSeeder` seeds starter locations if empty. You can also optionally run `backend/src/main/resources/schema.sql` and `backend/src/main/resources/seed.sql`)*.

### 3. Local Secrets Configuration
Create a private `backend/src/main/resources/application-local.properties` file (automatically ignored by Git):
```properties
spring.datasource.password=YOUR_LOCAL_DB_PASSWORD
mapbox.access-token=YOUR_MAPBOX_TOKEN
```

### 4. Running the Backend
```bash
cd backend
mvn spring-boot:run
```
The backend starts on `http://localhost:8080`.

### 5. Running the Frontend
Serve the `frontend/` directory with any static server:
```bash
# Example with Python
python -m http.server 5500 --directory frontend

# Or using Node
npx serve frontend -p 5500
```
Open your browser at `http://localhost:5500`.

---

## ☁️ Production Deployment

### 1. Backend Configuration
For production hosting (AWS, Render, Heroku, Railway, Fly.io, etc.), activate the production profile:
```bash
SPRING_PROFILES_ACTIVE=prod
```

When `SPRING_PROFILES_ACTIVE=prod` is set, `application-prod.properties` strictly requires the following environment variables (no development fallbacks are accepted):

| Environment Variable | Description | Example |
|---|---|---|
| `PORT` | Web server listening port | `8080` |
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://db-host:5432/geoguesser` |
| `DB_USERNAME` | PostgreSQL database user | `geoguesser_user` |
| `DB_PASSWORD` | PostgreSQL database password | *Secret* |
| `JWT_SECRET` | 256-bit+ cryptographic signing key | *Generate with `openssl rand -base64 64`* |
| `MAPBOX_ACCESS_TOKEN` | Mapbox API public token | `pk.eyJ...` |
| `CORS_ALLOWED_ORIGINS` | Deployed frontend origin(s) | `https://geoguesser.yourdomain.com` |

Build the production jar:
```bash
cd backend
mvn clean package -DskipTests=false
# Generates target/geoguesser-backend-1.0.0.jar
```

Run in production:
```bash
java -jar -Dspring.profiles.active=prod target/geoguesser-backend-1.0.0.jar
```

### 2. Frontend Configuration
The frontend connects to the backend without requiring manual `localStorage` configuration:
1. In [frontend/config.js](frontend/config.js), set `API_URL`:
   ```javascript
   window.ENV = {
     API_URL: 'https://api.yourdomain.com/api'
   };
   ```
2. Deploy the `frontend/` folder to any static hosting provider (Vercel, Netlify, Cloudflare Pages, AWS S3 / CloudFront).

---

## 🎯 Game Features & Architecture

- **Satellite Imagery**: Coordinates are converted to Mapbox Static Satellite imagery centered precisely on geographical landmarks.
- **Floating Interactive Minimap**:
  - Collapsed size `360 × 230 px`, anchored at the lower-right.
  - Expands smoothly on hover or via touch toggle to `min(700px, 85vw) × min(500px, 70vh)`.
  - Spatial collision detection for country labels preventing overlapping text.
- **Five-Round Match Flow**:
  - Distance calculated via the Haversine formula.
  - Scores strictly bounded between 0 and 5,000 per round (up to 25,000 total).
  - Time bonus for rapid guesses within 10 seconds.
- **Leaderboard & Match History**: Persisted in PostgreSQL with player stat tracking and personal best records.