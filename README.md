# Crisis OS 🚨

### Offline-First Mesh Communication System for Android

Crisis OS is a real-time, offline-first Android communication system designed for scenarios where traditional internet connectivity is unavailable or unreliable. The application enables peer-to-peer messaging using device-to-device mesh networking, allowing users to discover nearby devices, establish connections, and exchange messages without relying on centralized servers, cellular networks, or Wi-Fi infrastructure.

This project demonstrates a robust implementation of Clean Architecture in a real-world Android application, integrating low-level networking, reactive state management, and modular system design.

---

## 📌 Overview

In emergency situations such as natural disasters, remote expeditions, or network outages, communication becomes a critical challenge. Crisis OS addresses this problem by leveraging the Nearby Connections API to create a decentralized mesh network between Android devices.

Each device acts as both a client and a relay node, enabling message propagation across multiple peers. This allows communication to extend beyond direct Bluetooth or Wi-Fi range through multi-hop transmission.

---

## 🚀 Core Features

### 📡 Offline Mesh Networking

* Peer-to-peer communication without internet dependency
* Automatic device discovery and connection handling
* Multi-hop message propagation through intermediate devices

### 💬 Real-Time Messaging

* Send and receive messages instantly across connected peers
* Message delivery states (sending, queued, sent, failed)
* Persistent message storage for reliability

### 🔍 Device Discovery & Connection Management

* Dynamic discovery of nearby devices
* Connection request and acceptance workflow
* Live peer count and connection status updates

### 💾 Local Data Persistence

* Messages stored locally using Room Database
* Automatic synchronization between UI and database
* Reliable state recovery across app restarts

### ⚡ Reactive UI System

* Built using Kotlin Coroutines and Flow
* Real-time UI updates based on state changes
* Efficient handling of asynchronous data streams

### 🧠 Clean Architecture Implementation

* Strict separation of concerns
* Scalable and maintainable code structure
* Fully decoupled layers with dependency injection

---

## 🏗 Architecture

The project follows a layered Clean Architecture model, ensuring clear separation between business logic, data handling, and presentation.

```
UI → UseCase → Repository → Data / Device
```

### 🔹 UI Layer

* Built with Jetpack Compose
* Contains ViewModels and Composable screens
* Responsible only for rendering state and handling user input

### 🔹 Domain Layer

* Contains business logic and core models
* Defines repository interfaces
* Includes use cases that represent application actions

### 🔹 Data Layer

* Implements repository interfaces
* Handles data sources (local database + mesh communication)
* Maps between DTOs, entities, and domain models

### 🔹 Device Layer

* Contains Android-specific implementations
* Handles hardware interactions (networking, permissions, services)
* Encapsulates platform-dependent logic

---

## 🔄 Data Flow

1. User sends a message from UI
2. ViewModel triggers a UseCase
3. UseCase calls Repository interface
4. Repository interacts with:

   * Local database (for persistence)
   * Mesh layer (for transmission)
5. Message is propagated to nearby devices
6. Incoming messages are captured via event system
7. Data is stored and UI updates reactively

---

## 🧩 Key Components

### 📦 Mesh Communication Layer

* Built on Nearby Connections API
* Handles advertising, discovery, and connection lifecycle
* Encapsulates packet creation and parsing

### 🗄 Database Layer

* Room Database for local storage
* DAO-based data access
* Type converters for complex data models

### 🔗 Dependency Injection

* Managed using Hilt
* Provides loose coupling between layers
* Centralized configuration of dependencies

---

## 🛠 Tech Stack

* **Language:** Kotlin
* **UI:** Jetpack Compose
* **Architecture:** Clean Architecture
* **Dependency Injection:** Hilt
* **Database:** Room
* **Async Programming:** Coroutines + Flow
* **Networking:** Nearby Connections API

---

## 📱 Application Flow

### 1. App Initialization

* Mesh services start in background
* Device begins advertising and discovering peers

### 2. Peer Discovery

* Nearby devices appear dynamically
* Users can view available connections

### 3. Connection Establishment

* Devices connect directly or through mesh
* Connection state is tracked in real-time

### 4. Messaging

* Messages are created and stored locally
* Sent through mesh network
* Received messages are persisted and displayed instantly

---

## 🔒 Design Principles

* **Offline-first approach** — functionality does not depend on internet
* **Separation of concerns** — each layer has a single responsibility
* **Scalability** — easy to extend with new features
* **Testability** — domain logic is independent of Android framework
* **Maintainability** — modular and organized codebase

---

## ⚙️ Setup & Installation

### Requirements

* Android Studio (latest stable version)
* Android device (recommended for testing mesh features)

### Steps

```bash
git clone https://github.com/Vishalchaudhary10/Crisis-Os-Mesh.git
cd Crisis-Os-Mesh
```

1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Connect a physical Android device
4. Run the application

---

## ⚠️ Notes on Testing

* Mesh communication works best on **real devices**, not emulators
* Multiple devices are required to fully test peer-to-peer communication
* Bluetooth and Location permissions must be enabled

---

## 📊 Project Highlights

* Real-world use case implementation (offline communication)
* Advanced architecture design (Clean Architecture)
* Integration of low-level networking APIs
* Reactive UI with modern Android development practices

---

## 🧠 What This Project Demonstrates

This project is not just a messaging app—it demonstrates:

* System design thinking
* Handling of asynchronous distributed systems
* Practical Clean Architecture implementation
* Ability to manage complex data flow across multiple layers

---

## 📌 Conclusion

Crisis OS is a practical demonstration of building resilient communication systems in constrained environments. It combines modern Android development practices with decentralized networking concepts to create a scalable and reliable solution for offline communication.

The project showcases both technical depth and architectural discipline, making it a strong foundation for further development or real-world deployment in emergency communication scenarios.
