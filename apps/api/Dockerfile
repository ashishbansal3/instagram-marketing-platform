FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY apps/api/ ./
RUN apk add --no-cache bash curl unzip \
 && curl -fsSL https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o /tmp/gradle.zip \
 && unzip -q /tmp/gradle.zip -d /opt \
 && /opt/gradle-8.11.1/bin/gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
