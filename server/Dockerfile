ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION}-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY gradle gradle
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY shared shared
COPY server server

RUN chmod +x gradlew
RUN ./gradlew -PserverOnly=true :server:bootJar --no-daemon

FROM eclipse-temurin:${JAVA_VERSION}-jre-jammy

WORKDIR /app

RUN useradd --create-home --shell /usr/sbin/nologin appuser

COPY --chown=appuser:appuser --from=build /workspace/server/build/libs/*.jar app.jar

ENV SERVER_PORT=8080

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
