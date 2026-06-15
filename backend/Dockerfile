FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY build.gradle gradlew ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon clean build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
