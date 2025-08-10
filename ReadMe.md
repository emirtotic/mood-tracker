# Mood Tracker — Backend

Spring Boot backend for tracking daily mood entries and generating AI insights. Focus: clean REST API, MySQL, JWT auth, MapStruct DTO mapping, and OpenRouter **free** models for AI suggestions.

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Key Features](#key-features)
- [Architecture & Structure](#architecture--structure)
- [Requirements](#requirements)
- [Setup & Run](#setup--run)
- [Configuration](#configuration)
- [Database](#database)
- [AI Integration (OpenRouter)](#ai-integration-openrouter)
- [API](#api)
- [MapStruct & Lombok Notes](#mapstruct--lombok-notes)
- [Seed Data](#seed-data)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)

---

## Tech Stack

- **Java 22**, **Spring Boot 3.5.4**
- **Spring Web**, **Spring Security + JWT (jjwt)**
- **Spring Data JPA** + **MySQL** + **Flyway**
- **MapStruct** + **Lombok**
- (Optional) **Spring for Apache Kafka**
- **AI**: OpenRouter (OpenAI-compatible `/chat/completions`), client: `RestTemplate`

---

## Key Features

- **Users** (JWT authentication via `UserRepository.findByEmail(...)`)
- **Mood Entries**: `entryDate` (DATE), `moodScore` (1–5), `note` (text)
- **AI Analysis**: collects up to the last **30** entries and returns a summary + **five** concrete suggestions.

Example response:

```json
{
  "average": 3.6,
  "summary": "Concise summary in English language, ~120 words max.",
  "suggestions": [
    "Actionable suggestion #1",
    "Actionable suggestion #2",
    "Actionable suggestion #3",
    "Actionable suggestion #4",
    "Actionable suggestion #5"
  ]
}
```

---

## Architecture & Structure

```
com.moodTracker
├─ config/            # RestTemplate, security, etc.
├─ dto/               # MoodEntryDto, MoodEntryAiResponse...
├─ entity/            # User, MoodEntry...
├─ mapper/            # MapStruct interfaces
├─ repository/        # Spring Data JPA repositories
├─ service/           # Service interfaces
├─ service/impl/      # Implementations (AiAdviceServiceImpl, MoodEntryServiceImpl...)
└─ web/               # REST controllers (AiController, MoodEntryController...)
```

---

## Requirements

- **Java 22**
- **Maven 3.9+**
- **MySQL 8.0+**

---

## Setup & Run

1. Create DB:
   ```sql
   CREATE DATABASE moodtracker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Configure properties (see below).
3. Export your OpenRouter key:
   ```bash
   export OPENROUTER_API_KEY=sk-or-...   # macOS/Linux
   # or in IntelliJ Run/Debug Config → Environment → OPENROUTER_API_KEY
   ```
4. Run the app:
   ```bash
   mvn clean spring-boot:run
   ```

---

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=mood-tracker

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/moodtracker?allowPublicKeyRetrieval=true&useSSL=False
spring.datasource.username=yourUsername
spring.datasource.password=yourPassword
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=db/migration
spring.flyway.baseline-on-migrate=true

# Security / JWT
spring.autoconfigure.exclude=org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration
jwt.secret=It is a secret, right? :)
jwt.expiration=86400000

# OpenRouter (AI)
openrouter.base-url=https://openrouter.ai/api/v1
openrouter.api-key=${OPENROUTER_API_KEY}
openrouter.referer=http://localhost:8080
openrouter.title=MoodTracker AI
# optional models
openrouter.model=meta-llama/llama-3.1-8b-instruct:free
openrouter.fallback-model=mistralai/mistral-7b-instruct:free

# Debug (optional)
logging.level.org.springframework.web.client=DEBUG
```

## Database

**Table mood_entries``:

- `id BIGINT PK`
- `user_id BIGINT FK -> users.id`
- `entry_date DATE`
- `mood_score INT` (1–5)
- `note VARCHAR(...)`

Recommended index:

```sql
CREATE INDEX idx_mood_entry_user_date ON mood_entries (user_id, entry_date);
-- or unique if you want one entry per day per user:
-- CREATE UNIQUE INDEX uniq_user_date ON mood_entries (user_id, entry_date);
```

**Repository examples** (date range):

```java
List<MoodEntry> findAllByUser_IdAndEntryDateBetweenOrderByEntryDateDesc(Long userId, LocalDate from, LocalDate to);
List<MoodEntry> findAllByUser_IdAndEntryDateGreaterThanEqualOrderByEntryDateDesc(Long userId, LocalDate from);
```

---

## AI Integration (OpenRouter)

- Endpoint: `POST http://localhost:8080/ai/analyze` (OpenAI-compatible)
- Headers: `Authorization: Bearer ${OPENROUTER_API_KEY}`, `HTTP-Referer`, `X-Title`
- Client: `RestTemplate`
- Flow: gather up to **30** recent entries → compute local average → send logs to the model with a **strict JSON** prompt & few-shot example → parse response and return `summary` + **5 suggestions**.

**Example cURL (list models):**

```bash
curl https://openrouter.ai/api/v1/models \
  -H "Authorization: Bearer $OPENROUTER_API_KEY" \
  -H "HTTP-Referer: http://localhost:8080" \
  -H "X-Title: MoodTracker AI"
```

**Sample response:**

```json
{
  "average": 3.1,
  "summary": "Mood varies; sleep and light activity help stabilize tone.",
  "suggestions": [
    "Set a fixed bedtime for 7–8 hours of sleep",
    "Take a 10–15 minute walk after work",
    "Record daily stress triggers and planned responses",
    "Schedule a brief social activity twice a week",
    "Practice a 5-minute breathing exercise each morning"
  ]
}
```

*(Optional)* Add a POST variant that accepts a `List<MoodEntryDto>` in the body for manual/Postman tests.

---

## MapStruct & Lombok Notes

If MapStruct generates an “empty” `*MapperImpl`:

1. Add `` under `maven-compiler-plugin` → `annotationProcessorPaths`.
2. Run `mvn clean compile` to regenerate.

Minimal mapper (`userId` ↔ `user.id`):

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MoodEntryMapper {
  @Mapping(source = "user.id", target = "userId")
  MoodEntryDto toDto(MoodEntry e);

  @Mapping(target = "user", source = "userId", qualifiedByName = "userFromId")
  MoodEntry toEntity(MoodEntryDto dto);

  @Named("userFromId")
  default User userFromId(Long id) {
    if (id == null) return null;
    User u = new User(); u.setId(id); return u;
  }
}
```

---

## Seed Data

Quick insert for `user_id = 2` (last 10 days):

```sql
INSERT INTO mood_entries (user_id, entry_date, mood_score, note) VALUES
(2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 4, 'Auto seed #1'),
(2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 2, 'Auto seed #2'),
(2, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 5, 'Auto seed #3'),
(2, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 1, 'Auto seed #4'),
(2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 3, 'Auto seed #5'),
(2, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 5, 'Auto seed #6'),
(2, DATE_SUB(CURDATE(), INTERVAL 7 DAY), 2, 'Auto seed #7'),
(2, DATE_SUB(CURDATE(), INTERVAL 8 DAY), 4, 'Auto seed #8'),
(2, DATE_SUB(CURDATE(), INTERVAL 9 DAY), 1, 'Auto seed #9'),
(2, DATE_SUB(CURDATE(), INTERVAL 10 DAY), 3, 'Auto seed #10');
```

---

## Troubleshooting

- **AI returns empty strings**
  - Use a stronger `:free` model (`openrouter.model=meta-llama/llama-3.1-8b-instruct:free`) with fallback (`mistralai/mistral-7b-instruct:free`).
  - Use **strict JSON** prompt + few-shot + `response_format: json_object`.
  - `429` → rate limited; retry with backoff.
- **401/403 to OpenRouter**
  - Check `OPENROUTER_API_KEY`, `HTTP-Referer`, `X-Title`.
- **MapStruct empty implementation**
  - Add `lombok-mapstruct-binding`, then `mvn clean compile`.
- **MySQL date issues**
  - Ensure `entry_date` is `DATE`; use `yyyy-MM-dd` format.

---

## Roadmap

- Full CRUD for entries (pagination, validation)
- Observability for AI (latency, cost, hit-rate)
- Per-user rate limiting
- Additional models + evaluation (A/B)

---

**Author**: Emir • *Backend: Java/Spring, AI integrations*

