FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/cab-booking-api/pom.xml backend/cab-booking-api/pom.xml
RUN mvn -B -ntp -f backend/cab-booking-api/pom.xml dependency:go-offline
COPY backend/cab-booking-api/src backend/cab-booking-api/src
RUN mvn -B -ntp -f backend/cab-booking-api/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 --create-home appuser
COPY --from=build /workspace/backend/cab-booking-api/target/cab-booking-api-0.1.0-SNAPSHOT.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
