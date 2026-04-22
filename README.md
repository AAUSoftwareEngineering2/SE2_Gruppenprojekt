# Kuhhandel for Android

A digital adaptation of the classic card game **Kuhhandel**, developed as part of the Software
Engineering II course at Alpen-Adria-Universität Klagenfurt.

### Tech Stack

* **Frontend:** Kotlin, Jetpack Compose (Android)
* **Backend:** Spring Boot (Kotlin)
* **Communication:** WebSockets via the STOMP protocol (using Krossbow on Android)
* **Build System:** Gradle with ktlint for consistent code styling

## Project Structure

* `:app` - The Android client containing the UI and local state management
* `:server` - The backend responsible for game logic, player synchronization, and room management
* `:shared` - Common data models, enums (e.g., `AnimalType`, `GamePhase`), and logic used by both
  client and server

---

### Kuhhandel Gameplay Flowchart

<img width="734" height="883" alt="Gameplay Flowchart" src="https://github.com/user-attachments/assets/43fb1382-33ab-4d51-bc57-c30ebdee108e" />
