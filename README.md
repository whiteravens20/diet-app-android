# Diet App — Android Companion

> Offline-first Android companion for the Diet App platform.

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![CI](https://github.com/whiteravens20/diet-app-android/actions/workflows/test.yml/badge.svg)](https://github.com/whiteravens20/diet-app-android/actions)

The companion app for [**Diet App**](https://github.com/whiteravens20/diet-app), the
self-hostable diet and meal-planning platform. It signs in to your own Diet App server,
shows what the platform computed and lets you do the day-to-day things on a phone:
generate a plan, swap a meal, tick off the shopping list.

> **Status: the main screens work; nothing is released yet.** Signing in, the dashboard,
> meal plans, recipes, the shopping list and the profile are built and talk to the
> backend. Meal plans and shopping lists stay readable offline. Changes made offline are
> not queued yet, and the app has no automated tests.

> [!WARNING]
> **Early development — not production ready.** This app and the Diet App
> platform are under active development. The API contract, data model and
> module structure may change without notice, and the project has not had an
> independent security review. Build it to experiment, not for anything you depend on yet.

## Features

- **Your own server.** Sign in or create an account on the Diet App instance you run. The
  server address can be changed in the app, so one build works with any instance.
- **Dashboard.** A profile's daily calorie target, with maintenance, the daily deficit,
  meals per day and the target macro split.
- **Meal plans.** The plans of a profile, a new plan generated from the phone, each plan
  day by day with its totals, and a meal swapped for another recipe.
- **Recipes.** Search in the recipe library; a recipe with its nutrition per serving,
  ingredients and steps.
- **Shopping list.** Generated from a plan, grouped by store section, with what the
  pantry already covers, and items ticked off as you shop.
- **Profile.** The account's e-mail status, language and theme, the app's version, the
  server address and sign-out. Account settings and diet profiles are edited in the web
  app.
- **Offline.** Meal plans and shopping lists you have opened stay readable without a
  connection, marked with the time they were last synced. Offline they are read-only.
- **English and Polish.**

The app never computes nutrition, calorie targets or meal plans. Every number on the
screen comes from the backend.

## Install

There is no published build yet, so the APK is built from source. You need Android
Studio, or the Android SDK with JDK 17 or newer, and a device or emulator with Android
8.0 (API 26) or newer.

```bash
git clone https://github.com/whiteravens20/diet-app-android
cd diet-app-android
./gradlew assembleDebug            # -> app/build/outputs/apk/debug/app-debug.apk
```

Without a local toolchain, run the **Build APK** workflow in your fork and download the
`diet-app-debug-apk` artifact.

## Run

The app needs a running [Diet App](https://github.com/whiteravens20/diet-app) backend.
Its address is `http://10.0.2.2:4000/api/` by default, which is the host machine as the
Android emulator sees it. For a phone on your network, set another one at build time:

```bash
./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.20:4000/api/
```

or change it in the app, with the **Server** button on the sign-in screen or under
Profile. Changing the server signs you out and clears the offline cache.

A debug build accepts a plain `http://` address, which a backend on your own network
usually has. A release build accepts HTTPS only.

[docs/running-locally.md](docs/running-locally.md) covers the whole local loop: starting
the backend, installing on a physical device and what the emulator needs.

## Architecture

The phone is a cache and a view of the backend: the backend owns all state and all
nutrition maths. The app is written in Kotlin with Jetpack Compose and Material 3, and
its code is split by what each part may know:

- `ui/` holds the Compose screens, the theme and the shared components. Each screen has
  a Hilt ViewModel that exposes its state as a `StateFlow`.
- `domain/` holds the app's own models, which do not depend on the wire format.
- `data/remote/` holds the Retrofit services and the DTOs, a Kotlin mirror of the
  platform's shared contract.
- `data/auth/` keeps the access and refresh tokens in DataStore and attaches them to
  requests; a request answered with 401 is retried with a refreshed token.
- `data/config/` keeps the server address chosen in the app.
- `data/local/` is the Room cache of meal plans and shopping lists.
- `data/repository/` joins them: a repository serves the cache first and refreshes it
  from the API.
- `di/` wires it together with Hilt.

`ui` and `data` depend on `domain`, and `domain` on nothing; a DTO never reaches a
screen. More in [docs/architecture.md](docs/architecture.md), the
[sync strategy](docs/sync-strategy.md) and the
[API contract](docs/contracts/api-contract.md).

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
