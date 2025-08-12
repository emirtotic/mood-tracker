# ---------- Build stage ----------
FROM maven:3.9.8-eclipse-temurin-22-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -T 1C -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -e -T 1C -DskipTests package

# ---------- Run stage ----------
FROM eclipse-temurin:22-jre-alpine
WORKDIR /app
RUN adduser -D -h /app appuser && chown appuser:appuser /app
USER appuser
COPY --from=build --chown=appuser:appuser /app/target/mood-tracker-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="" SERVER_PORT=${PORT}
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]