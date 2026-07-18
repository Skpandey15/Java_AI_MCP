FROM gradle:8.12-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle build.gradle ./
COPY src src
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/build/libs/interview-orchestrator-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
