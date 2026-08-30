# Mapsforge Compose

A modern Android application for outdoor tracking and map visualization, built with **Jetpack Compose** and **Mapsforge**. This project demonstrates a robust architecture for offline map rendering, GPS tracking, and tour management.

## 🚀 Features

- **Offline Map Rendering**: High-performance offline vector map rendering using the Mapsforge engine.
- **GPS Tracking**: Real-time recording of outdoor activities with background execution support via a Foreground Service.
- **Import & Export**: Support for **GPX** and **KML** formats, allowing users to import existing tracks or export their recorded tours for use in other applications.
- **Custom Render Themes**: Advanced theme management with support for built-in themes and importing custom `.xml` render themes from external storage.
- **Tour History**: Persistent storage for all recorded tours using **Room Database**, including route visualization and detailed statistics.
- **Dynamic Statistics**: Live tracking of distance, current speed, elevation gain, and altitude profile.
- **Eco Mode**: Battery-optimized tracking mode for long-duration tours.
- **Adaptive UI**: Built with Material 3 and Jetpack Compose for a modern, responsive user experience.

## 🛠️ Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Language**: Kotlin (utilizing idiomatic `resolve` for path handling and structured concurrency)
- **Map Engine**: Mapsforge (Vector map support)
- **Architecture**: MVVM with `StateFlow` and `collectAsStateWithLifecycle`
- **Persistence**: Room Database (SQLite)
- **Networking**: Kotlin Coroutines & OkHttp (for map and theme downloads)
- **Service**: Foreground Service for reliable location tracking
- **Logging**: Timber for structured diagnostic logging

## 📦 Project Architecture

- **`MainViewModel`**: Orchestrates UI state, manages screen navigation, and handles data operations between the repository and UI.
- **`TrackingService`**: A lifecycle-aware Foreground Service that handles location updates, statistics calculation, and database persistence during active tracking.
- **`MapsforgeMapView`**: A custom Compose wrapper for the Mapsforge map view, managing layer synchronization and theme application.
- **`SettingsRepository`**: Centralized management of user preferences using SharedPreferences.
- **`TourDatabase`**: Room-based persistence layer for storing tour entities and route points.
- **Utilities**:
  - `GpxUtils` & `KmlUtils`: Handlers for track file serialization/deserialization.
  - `MapDownloader` & `ThemeDownloader`: Managed download and extraction of map assets.

## 🏁 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 24+ (Android 7.0 "Nougat")

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/mapsforge_compose.git
   ```
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Build and Run the `app` module on an emulator or physical device.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
