# Mood Tracker
 
**Live Application:** [Mood Tracker](https://moodtracker-app-production.up.railway.app)

The backend and frontend are deployed on Railway — you can try out the app directly at the link above.

## Features

- User authentication with JWT (register, login, logout)
- Secure password change and account management
- Track daily mood entries with notes and emojis support
- Validation for entries (minimum 10 characters, maximum 1000)
- Integration with OpenAI (Spring AI) to analyze moods and provide personalized suggestions
- Integrated with OpenAI (Spring AI) to generate plan based on your entries (moods)
- REST API endpoints tested with Postman
- MySQL database support
- Docker-ready for easy deployment

## Tech Stack

- **Backend:** Java, Spring Boot, Spring Security, Spring Data JPA, Spring AI
- **Database:** MySQL
- **Authentication:** JWT with blacklist and refresh handling
- **Testing:** Postman collections
- **Deployment:** Railway (Dockerized)

## Project Structure

```
mood-tracker/
├── src/main/java/com/moodTracker
│   ├── controller     # REST controllers
│   ├── service        # Business logic services
│   ├── security       # JWT, filters, authentication
│   ├── model          # Entities and DTOs
│   ├── repository     # JPA repositories
│   └── MoodTrackerApplication.java
├── src/main/resources
│   ├── application.properties  # Configurations
│   └── schema.sql              # Database schema (if applicable)
└── pom.xml
```

## Endpoints

### Authentication
- `POST /api/auth/register` – Register new user
- `POST /api/auth/login` – Authenticate and get JWT
- `POST /api/auth/logout` – Invalidate JWT (blacklist)
- `POST /api/auth/change-password` – Change password

### Mood Entries
- `POST /api/mood/create` – Create new mood entry
- `PUT /api/mood/update` – Update mood entry
- `GET /api/mood/date` – Retrieve mood entry by date
- `GET /api/mood/range` – Retrieve moods entries by start & end date
- `DELETE /api/mood/{id}` – Delete entry

### AI Analysis
- `POST /api/ai/analyze` – Analyze mood entry and provide suggestions with AI
- `POST /api/ai/plan` – Generate personalized plan with AI based on mood entries

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

## Running Locally

### Prerequisites
- Java 21+
- Maven 3.9+
- MySQL running locally (or via Docker)
- Docker (for containerized deployment)

### Steps
```bash
# Clone repository
git clone https://github.com/emirtotic/mood-tracker.git
cd mood-tracker

# Build
mvn clean install

# Run
mvn spring-boot:run
```

### Configure Database
Update `application.properties` with your database credentials:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/database_name
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
```

## Docker Deployment

Build and run using Docker:
```bash
docker build -t mood-tracker .
docker run -p 8080:8080 mood-tracker
```

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request.

## Author

**Created by**: Emir Totić • *Backend: Java/Spring Boot, AI integrations*
