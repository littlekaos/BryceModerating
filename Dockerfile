# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

COPY . .

RUN mvn -B -DskipTests package

# Stage 2: Run
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/BryceModerating-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]