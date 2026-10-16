FROM eclipse-temurin:21-jdk
WORKDIR /workspace
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY backend backend
RUN chmod +x gradlew
CMD ["./gradlew", ":backend:check", "--no-daemon"]
