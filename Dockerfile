# ---------- Build stage ----------
FROM maven:3.9.8-eclipse-temurin-22-alpine AS build
WORKDIR /app

# Kopiraj pom.xml i keširaj zavisnosti
COPY pom.xml .
RUN --mount=type=cache,id=maven-cache,target=/root/.m2 mvn -q -e -T 1C -DskipTests dependency:go-offline

# Kopiraj izvorni kod i build-uj
COPY src ./src
RUN --mount=type=cache,id=maven-cache,target=/root/.m2 mvn -q -e -T 1C -DskipTests package

# ---------- Run stage ----------
FROM eclipse-temurin:22-jre-alpine
WORKDIR /app

# Kreiraj user-a i postavi vlasništvo u jednom sloju
RUN adduser -D -h /app appuser && chown appuser:appuser /app
USER appuser

# Kopiraj JAR fajl
COPY --from=build --chown=appuser:appuser /app/target/mood-tracker-0.0.1-SNAPSHOT.jar app.jar

# Eksponiraj port
EXPOSE 8080

# Postavi env varijable za Railway
ENV JAVA_OPTS=""
ENV SERVER_PORT=${PORT}

# Pokreni aplikaciju
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]