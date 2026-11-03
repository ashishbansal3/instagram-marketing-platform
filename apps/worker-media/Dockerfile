FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY apps/worker-media/ ./
RUN apk add --no-cache bash curl unzip \
 && curl -fsSL https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && /opt/gradle-8.11.1/bin/gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache ffmpeg curl
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
COPY fixtures/sample-salon.jpg /app/fixtures/sample-salon.jpg
ENV MOCK_MEDIA_PATH=/app/fixtures/sample-salon.jpg
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
