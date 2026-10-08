# Diet App — Android Companion

> Offline-first Android companion for the Diet App platform.

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![CI](https://github.com/whiteravens20/diet-app-android/actions/workflows/test.yml/badge.svg)](https://github.com/whiteravens20/diet-app-android/actions)

The companion app for [**Diet App**](https://github.com/whiteravens20/diet-app) — the
self-hostable diet & meal-planning platform. It reuses the platform's authentication,
profiles, meal plans and shopping lists, and adds **offline viewing** of cached data with
a sync layer.

> **Status: data layer wired, screens next.** The API binding, DTO mirror, auth
> plumbing (token store + refresh) and offline-first repositories are in place and
> the app compiles to an installable APK. Feature screens are not yet built — see
> [docs/architecture.md](docs/architecture.md) and the platform roadmap.

> [!WARNING]
> **Early development — not production ready.** This app and the Diet App
> platform are under active development. The API contract, data model and
> module structure may change without notice, and the project has not had a
> security review. Build it to experiment, not for anything you depend on yet.

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

## Build & run

```bash
# Requires Android Studio (latest) or the Android SDK + JDK 17+.
./gradlew assembleDebug            # -> app/build/outputs/apk/debug/app-debug.apk
```

The app talks to a running Diet App backend. The base URL is a build config
value defaulting to `http://10.0.2.2:4000/api/` (the emulator's host loopback).
Override it without editing sources:

```bash
./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.20:4000/api/   # a LAN IP for a real device
```

That's only the default — the server address is also editable in the app itself
(the **Server** button on the login screen, or Profile → Server) and persists on
the device, so one APK can point at any instance.

To build the APK on demand in the cloud, run the **Build APK** workflow
(`workflow_dispatch`) and download the `diet-app-debug-apk` artifact.

See [docs/running-locally.md](docs/running-locally.md) for the full local loop —
running the backend, installing on a physical device, and why the Android
emulator needs hardware virtualization (KVM).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

See [SECURITY.md](SECURITY.md).

## How the code is written and checked

This app is built by one maintainer using AI coding tools. The tools write most of the
code and documentation; the maintainer decides what gets built and is responsible for
everything that lands here. The app is in early development: there is no release yet and
no independent security review.

**What a change goes through**

- Every push and pull request has to compile into a debug APK
  ([test.yml](.github/workflows/test.yml)). There are no automated tests yet, so that is
  all CI proves.
- Commits are signed.

**What the maintainer decided and read**

- The backend is the source of truth. The app shows and caches what the API returns and
  never computes nutrition, calorie targets or meal plans itself.
- The offline-first MVVM structure and the split between wire DTOs and `domain` models
  were specified by the maintainer.
- `data/remote/Dtos.kt` mirrors the platform's shared contract and is compared with it
  by hand.

**Before a release**

- There is no release yet. Until the app has tests of its own, the maintainer checks
  each build by installing it and using it against a running Diet App backend.

If something looks wrong, open an issue. For a vulnerability, follow
[SECURITY.md](SECURITY.md).

## License

[GNU Affero General Public License, version 3](LICENSE) (AGPL-3.0-only), with one
additional term in [NOTICE](NOTICE) — © 2026 White Ravens.

You are free to use, change and share the app. Anyone who distributes it, or a version
based on it, has to do so under the same licence with the source code available, and has
to keep the attribution "Diet App Android Companion by White Ravens" together with the
address of this repository.
