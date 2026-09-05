# Mapsforge Compose

A modern Android application for outdoor tracking, routing, and map visualization, built with **Jetpack Compose** and **Mapsforge**. This project demonstrates a robust architecture for offline map rendering, GPS tracking, and tour management with a focus on battery efficiency and user customization.

## 🚀 Features

- **Offline Map Rendering**: High-performance offline vector map rendering using the Mapsforge engine.
- **GPS Tracking**: Real-time recording of outdoor activities with background execution support via a Foreground Service.
- **Routing & Navigation**: Integrated **GraphHopper** engine for offline route calculation (Pedestrian, Bicycle, Car).
- **Import & Export**: Support for **GPX** and **KML** formats, allowing users to import existing tracks or export their recorded tours.
- **Weather Integration**: Real-time weather forecasts powered by **Ktor** and the Open-Meteo API.
- **Dynamic Render Themes**: 
  - Support for multiple built-in render themes (Cruiser, Mapsforge, OutdoorActive, etc.).
  - **Custom Theme Import**: Users can import their own `.xml` render themes from external storage.
- **Interactive Charts**: Detailed **Elevation and Speed profiles** for recorded and loaded tracks.
- **POI Management**: Save and organize Points of Interest with distance calculation and navigation support.
- **Tour History**: Persistent storage for all recorded tours using **Room Database**, including route visualization and detailed statistics.
- **Adaptive UI**: Built with Material 3 and Jetpack Compose, including full support for **Dark Mode** and **Localization** (English, German).

## 🛠️ Tech Stack

- **UI**: Jetpack Compose (Material 3)
- **Language**: Kotlin (utilizing idiomatic `resolve` for path handling and structured concurrency)
- **Map Engine**: Mapsforge (Vector map support)
- **Routing**: GraphHopper (Local routing engine)
- **Architecture**: MVVM with `StateFlow` and `collectAsStateWithLifecycle`
- **Persistence**: Room Database (SQLite)
- **Networking**: **Ktor** (for weather data) & OkHttp (for asset management)
- **Service**: Foreground Service for reliable location tracking
- **Charts**: Custom Compose-based charting library
- **Logging**: Timber for structured diagnostic logging
- **Serialization**: Kotlinx Serialization

## 📦 Project Architecture

- **`:app`**: Main application module containing the UI and business logic.
- **`:graphhopper`**: Routing library integration.
- **`:composecharts`**: Modular charting component for elevation and speed profiles.
- **`MainViewModel`**: Orchestrates UI state, manages screen navigation, and handles dynamic theme loading.
- **`WeatherViewModel`**: Manages weather data fetching and state using Ktor.
- **`TrackingService`**: A lifecycle-aware Foreground Service that handles location updates, statistics calculation, and database persistence.
- **`MapsforgeMapView`**: A custom Compose wrapper for the Mapsforge map view.
- **`TourDatabase`**: Room-based persistence layer for storing tour entities and route points.

## 🏁 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 26+ (Android 8.0 "Oreo")

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
