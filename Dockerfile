FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q clean verify
FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 appuser
WORKDIR /app
COPY --from=build /app/target/mitraa-hackathons-1.0.0.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar","--spring.profiles.active=prod"]
