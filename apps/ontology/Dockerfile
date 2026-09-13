FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package || (echo "=== MAVEN FAILED ==="; ls -la; exit 1)

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 pulse
COPY --from=build /workspace/target/pulse-ontology-0.1.0.jar /app/pulse.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-XX:+ExitOnOutOfMemoryError","-jar","/app/pulse.jar"]
