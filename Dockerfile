FROM node:24-bookworm-slim AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY backend/ ./backend/
COPY --from=web /web/dist ./frontend/dist
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 agenda && mkdir -p /app/data /app/tmp && chown -R agenda /app
COPY --from=build --chown=agenda:agenda /build/target/agenda-clara-0.9.0.jar /app/agenda.jar
USER agenda
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.io.tmpdir=/app/tmp", "-jar", "/app/agenda.jar"]
