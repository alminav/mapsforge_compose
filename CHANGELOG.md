# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- **Weather Integration**:
    - Integrated **Ktor** client for asynchronous networking.
    - Added `WeatherService` using Open-Meteo API.
    - Added `WeatherViewModel` and `WeatherScreen` to display current, hourly, and daily forecasts.
    - Dynamic weather icon mapping based on WMO weather codes.
- **Localization**:
    - Added German (`values-de`) localization for all major UI components.
    - Refactored hardcoded strings in `PoiListDialog` and `WeatherScreen` into resource files.
- **Points of Interest (POI) Enhancements**:
    - Added sorting options (Name and Distance) to the POI list.
    - Added weather lookup functionality directly from the POI list items.
- **Build System**:
    - Configured **Kotlinx Serialization** plugin and dependencies.
    - Added Ktor Android client and Content Negotiation libraries.

### Fixed
- **Compose Preview Issues**:
    - Resolved `NoClassDefFoundError` and render crashes in `PoiListDialog` by implementing a `LocalInspectionMode` check.
    - Added a safe distance approximation for previews to avoid crashes with the `android.location.Location` API.
    - Fixed `AlertDialog` rendering in previews by providing a `Surface`-based fallback.
- **UI/UX**:
    - Improved layout of POI list items for better readability.
    - Fixed icon scaling and alignment in dialog overlays.

## [0.1.0] - 2026-09-01
### Added
- Initial project structure with **Mapsforge** integration.
- Offline map rendering support.
- Basic GPS tracking via Foreground Service.
- Room database for tour persistence.
- GPX/KML import and export utilities.
- Modular Charting component (`:composecharts`).
- GraphHopper routing integration (`:graphhopper`).
