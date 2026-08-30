# Mapsforge Compose

A modern Android application for outdoor tracking and map visualization, built with **Jetpack Compose** and **Mapsforge**. This project demonstrates a robust architecture for offline map rendering, GPS tracking, and tour management with a focus on battery efficiency and user customization.

## 🚀 Features

- **Offline Map Rendering**: High-performance offline vector map rendering using the Mapsforge engine.
- **GPS Tracking**: Real-time recording of outdoor activities with background execution support via a Foreground Service.
- **Import & Export**: Support for **GPX** and **KML** formats, allowing users to import existing tracks or export their recorded tours.
- **Dynamic Render Themes**: 
  - Support for multiple built-in render themes (Cruiser, Mapsforge, OutdoorActive, etc.).
  - **Custom Theme Import**: Users can import their own `.xml` render themes from external storage.
  - **Dynamic Variable Resolution**: Intelligent path handling for theme assets using idiomatic Kotlin `resolve` for cross-platform and device-specific safety.
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

- **`MainViewModel`**: Orchestrates UI state, manages screen navigation, and handles dynamic theme loading and region switching.
- **`TrackingService`**: A lifecycle-aware Foreground Service that handles location updates, statistics calculation, and database persistence during active tracking.
- **`MapsforgeMapView`**: A custom Compose wrapper for the Mapsforge map view, managing layer synchronization and theme application.
- **`SettingsRepository`**: Centralized management of user preferences using SharedPreferences, including custom theme paths.
- **`TourDatabase`**: Room-based persistence layer for storing tour entities and route points.
- **Utilities**:
  - `GpxUtils` & `KmlUtils`: Handlers for track file serialization/deserialization.
  - `MapDownloader` & `ThemeDownloader`: Managed download and extraction of map assets from `renderthemes.zip`.

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
