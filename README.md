# Diet App — Android Companion

> Offline-first Android companion for the Diet App platform.

[![License](https://img.shields.io/badge/license-PolyForm--NC--1.0.0-blue)](LICENSE)
[![CI](https://github.com/whiteravens20/diet-app-android/actions/workflows/test.yml/badge.svg)](https://github.com/whiteravens20/diet-app-android/actions)

The companion app for [**Diet App**](https://github.com/whiteravens20/diet-app) — the
self-hostable diet & meal-planning platform. It reuses the platform's authentication,
profiles, meal plans and shopping lists, and adds **offline viewing** of cached data with
a sync layer.

> **Status: Phase 3 scaffold.** This repository contains the project structure,
> architecture and integration contract. Feature screens are not yet implemented — see
> [docs/architecture.md](docs/architecture.md) and the platform roadmap.

## Stack

- **Kotlin** + **Jetpack Compose** + **Material 3**
- **Offline-first** — MVVM + repository, Room cache, Retrofit/OkHttp, Hilt DI
- Targets Android 8.0 (API 26)+; built with the latest stable AGP/Kotlin

## Architecture

```
ui/        Compose screens, theme, components — Hilt ViewModels
domain/    App models, decoupled from the wire format
data/
  remote/  Retrofit API binding + DTOs (mirror of the platform contract)
  local/   Room offline cache
  repository/  Offline-first repositories: cache first, refresh in background
di/        Hilt modules
```

See [docs/architecture.md](docs/architecture.md), the offline
[sync strategy](docs/sync-strategy.md), and the [API contract](docs/contracts/api-contract.md).

## Build

```bash
# Requires Android Studio (latest) or the Android SDK + JDK 17.
./gradlew assembleDebug
```

The app talks to a running Diet App backend — set `API_BASE_URL` in
`app/build.gradle.kts` (defaults to `http://10.0.2.2:4000/api/` for the emulator).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

See [SECURITY.md](SECURITY.md).

## License

[PolyForm Noncommercial 1.0.0](LICENSE) — © 2026 White Ravens.
