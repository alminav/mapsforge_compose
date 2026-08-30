# Mapsforge Compose

A modern Android application for outdoor tracking and map visualization, built with **Jetpack Compose** and **Mapsforge**.

## 🚀 Features

- **Offline Maps**: Uses Mapsforge for efficient offline map rendering.
- **Region Management**: Download maps and themes for different regions (e.g., Niedersachsen).
- **GPS Tracking**: Record your tours with real-time statistics.
- **Tour Statistics**: Track distance, current speed, elevation gain, and altitude.
- **Tour History**: Archive and view past tours with route visualization.
- **MVVM Architecture**: Clean separation of concerns using ViewModels and StateFlow.
- **Material 3**: Modern UI following Android's latest design guidelines.
- **Eco Mode**: Battery-saving tracking optimizations.

## 🛠️ Tech Stack

- **UI**: Jetpack Compose
- **Language**: Kotlin
- **Map Engine**: Mapsforge
- **Architecture**: MVVM (ViewModel, Flow, collectAsStateWithLifecycle)
- **Database**: Room (for tour history)
- **Services**: Foreground Service for GPS tracking
- **Logging**: Timber

## 📦 Project Structure

- `MainViewModel`: Manages UI state and triggers background operations.
- `TrackingService`: Foreground service handling location updates and statistics.
- `MainUI`: Compose-based screens (Map, History, Settings).
- `MapDownloader` & `ThemeDownloader`: Handle downloading map files and render themes.
- `TourDatabase`: Room database for storing recorded tracks.

## 🏁 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 24+

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/mapsforge_compose.git
   ```
2. Open the project in Android Studio.
3. Sync Project with Gradle Files.
4. Run the app on an emulator or physical device.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
