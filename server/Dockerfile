# Multi-Stage-Build: Stufe 1 baut mit dem JDK das JAR, Stufe 2 liefert es in einem schlanken JRE-Image aus.
ARG JAVA_VERSION=21

# Stufe 1 "build": großes JDK-Image - hier wird kompiliert.
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
# baut das Spring-Boot-Fat-JAR; -PserverOnly = nur den Server bauen (nicht die Android-App).
RUN ./gradlew -PserverOnly=true :server:bootJar --no-daemon

# Stufe 2 "runtime": schlankes JRE-Image (kein Compiler/Quellcode) - das wird ausgeliefert.
FROM eclipse-temurin:${JAVA_VERSION}-jre-jammy

WORKDIR /app

# Nicht-Root-User aus Sicherheitsgründen (der Container läuft nicht als root).
RUN useradd --create-home --shell /usr/sbin/nologin appuser

# nur das fertige JAR aus Stufe 1 übernehmen - keine Build-Tools/Quellen im finalen Image.
COPY --chown=appuser:appuser --from=build /workspace/server/build/libs/*.jar app.jar

ENV SERVER_PORT=8080

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
