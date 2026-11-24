# ---- Build stage: compile the jar with the full JDK ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy only the build files first. Docker caches this layer, so dependencies are
# re-downloaded only when build.gradle.kts changes, not on every code change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null

COPY src ./src
# Tests run in CI; here we only package.
RUN ./gradlew bootJar --no-daemon -q \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# ---- Runtime stage: just the JRE and the jar (much smaller image) ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Don't run as root inside the container.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
USER app

COPY --from=build /app/app.jar app.jar

# 8080 = API, 8081 = health + metrics (internal only).
EXPOSE 8080 8081
# Use up to 75% of the container's memory limit for the Java heap.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
