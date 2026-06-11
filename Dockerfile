# Stage 1: Build frontend
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
COPY contract/ ../contract/
RUN npm run gen:api
RUN npm run build

# Stage 2: Build backend (Node нужен Gradle-задаче tspCompile для генерации OpenAPI из TypeSpec)
FROM gradle:8.14-jdk21 AS backend-build
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY contract/ ../contract/
COPY backend/ ./
COPY --from=frontend-build /frontend/dist /app/src/main/resources/static
RUN ./gradlew bootJar --no-daemon

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=backend-build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
