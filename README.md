# Kuhhandel for Android

A digital adaptation of the classic card game **Kuhhandel**, developed as part of the Software
Engineering II course at Alpen-Adria-Universität Klagenfurt.

### Tech Stack

* **Frontend:** Android app using Kotlin, Jetpack Compose, and Ktor
* **Backend:** Server using Kotlin and Spring Boot
* **Communication Protocol:** WebSockets with JSON payloads
* **Build System:** Gradle with ktlint

## Project Structure

* `:app` - The Android client containing the UI and local state management
* `:server` - The backend responsible for game logic, player synchronization, and room management
* `:shared` - Common data models, enums (e.g., `AnimalType`, `GamePhase`), and logic used by both
  client and server

## Test Notes

Server persistence tests use Testcontainers with PostgreSQL. They are skipped on machines without
Docker, so a local green `:server:test` without Docker does not include the PostgreSQL persistence
slice. GitHub Actions runners provide Docker and execute those tests before CI/deploy images pass.

---

### Kuhhandel Gameplay Flowchart

<img width="734" height="883" alt="Gameplay Flowchart" src="https://github.com/user-attachments/assets/43fb1382-33ab-4d51-bc57-c30ebdee108e" />
