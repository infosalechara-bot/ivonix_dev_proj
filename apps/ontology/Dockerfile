# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Cache dependencies (best-effort, don't fail the build if it times out)
COPY pom.xml .
RUN mvn -B dependency:go-offline || true

# Build the app — full output, no -q
COPY src ./src
RUN mvn -B -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --uid 10001 pulse

COPY --from=build /workspace/target/*.jar /app/pulse.jar

RUN chown -R pulse:pulse /app

USER 10001

EXPOSE 8081

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/pulse.jar"]
