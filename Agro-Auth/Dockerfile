# Étape 1 : Build (Gradle 8.14+ et JDK 21 requis par Spring Boot 4)
FROM gradle:8.14-jdk21 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle . .
RUN gradle build -x test --no-daemon

# Étape 2 : Exécution sur JRE 21
FROM eclipse-temurin:21-jre-jammy
EXPOSE 8081
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]