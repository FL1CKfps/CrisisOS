# Crisis OS — Android Mesh Communication App

## Overview

Crisis OS is an offline-first, decentralized communication and safety platform for Android. It enables peer-to-peer messaging via Bluetooth and Wi-Fi mesh networking (Google Nearby Connections API) without internet or cellular connectivity. Designed for emergencies, natural disasters, and off-grid scenarios.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** Clean Architecture (UI → UseCase → Repository → Data/Device)
- **Dependency Injection:** Hilt (Dagger)
- **Database:** Room (offline-first local persistence)
- **Async:** Kotlin Coroutines + Flow
- **Networking:** Google Play Services Nearby Connections API
- **Maps:** OSMDroid (offline OpenStreetMap)
- **AI:** LiteRT-LM (on-device Gemma 2b/7b)
- **Build System:** Gradle with Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`)
- **Min SDK:** 26 | Target SDK: 35 | Compile SDK: 37

## Project Structure

```
app/src/main/java/com/elv8/crisisos/
├── ui/          - Jetpack Compose screens and ViewModels
├── domain/      - Use cases, repository interfaces, business logic
├── data/        - Repository implementations, Room DAOs, mesh data
│   └── remote/mesh/  - MeshConnectionManager, MeshMessenger (P2P core)
├── core/        - EventBus, notifications, AI context, diagnostics
└── device/      - Hardware: audio, file management, media
```

## Key Features

- Offline mesh networking (multi-hop message propagation)
- SOS alerts, Dead Man Switch, missing person/child alerts
- Supply request tracking
- Offline maps (OSMDroid) with danger/safe zones
- On-device AI assistant (Gemma, fully offline)
- Voice messages, photos, and videos over mesh
- Room database for persistent offline storage

## Replit Environment Notes

- **Java Runtime:** GraalVM 22.3.1 (JDK 19) — installed via Replit modules
- **Build:** Gradle 9.4.1 via `./gradlew`
- **Android SDK:** Not available in Replit — full APK builds require Android Studio or CI/CD with Android SDK
- **gradle.properties:** Fixed Windows-specific `org.gradle.java.home` path (removed), auto-detection enabled
- **Workflow:** Console workflow runs `./gradlew tasks --all` to show available build tasks

## Building the APK

To build and run this app, use **Android Studio** on a local machine:

1. Clone the repository
2. Open in Android Studio (latest stable)
3. Sync Gradle dependencies
4. Connect a physical Android device (mesh features require real hardware)
5. Run the application

Alternatively, use a CI/CD system (GitHub Actions, Bitrise) with an Android SDK environment.

## Architecture Notes

- Clean Architecture with strict layer separation
- Hilt provides dependency injection across all layers
- WorkManager handles background retries, cleanup, and Dead Man Switch monitoring
- Kotlinx Serialization (JSON) used for packet serialization over mesh
