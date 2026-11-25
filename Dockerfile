# Single-container build: the React app is packaged inside the Spring Boot jar, so one service serves
# both the pages and the API from the same address. Used for hosting on Render (see render.yaml).
# For local Docker with nginx in front, infra/docker-compose.yml uses backend/ and frontend/ instead.

# ---- 1. Build the React app ----
FROM node:24-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. Build the Spring Boot jar, with the React files as its static content ----
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY backend/src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN ./gradlew bootJar --no-daemon -q \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# ---- 3. Run it: just the JRE and the jar ----
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app \
 && mkdir -p /data/photos && chown app:app /data/photos
USER app
COPY --from=backend /backend/app.jar app.jar

ENV PHOTO_STORAGE_DIR=/data/photos \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
