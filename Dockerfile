FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
ENV GRADLE_USER_HOME=/workspace/.gradle-home
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon --quiet dependencies
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
RUN groupadd --system outbox && useradd --system --gid outbox --home-dir /app --create-home outbox
WORKDIR /app
COPY --from=build --chown=outbox:outbox /workspace/build/libs/outbox-*.jar app.jar
USER outbox
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"'
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
