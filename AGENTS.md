# AGENTS.md

Quick reference for AI coding agents (and humans). Full onboarding:
[docs/llm/onboarding.md](docs/llm/onboarding.md).

## What this repo is

The Android companion app for the [Diet App platform](https://github.com/whiteravens20/diet-app).
Kotlin + Jetpack Compose + Material 3, offline-first. **Data layer wired, screens next** —
API binding, DTOs, auth and offline-first repositories are in place; feature screens are
not yet implemented.

## The rule

The backend is the source of truth. This app renders and caches API data — it never
computes nutrition, calorie targets or meal plans itself.

## Conventions

- Kotlin (official style); Jetpack Compose + Material 3.
- MVVM + repository; offline-first (Room cache first, refresh from API).
- DTOs in `data/remote/Dtos.kt` mirror the platform's `packages/shared` contract — keep
  them in sync; map DTOs to `domain` models, never leak them into `ui`.
- Hilt for DI. Conventional Commits, signed, no `Co-Authored-By`. Branch off `dev`.

## Where things live

| Need | Path |
|---|---|
| Screens / UI | `app/src/main/java/net/whiteravens/dietapp/ui` |
| API binding + DTOs | `…/data/remote` |
| Offline cache | `…/data/local` |
| Repositories | `…/data/repository` |
| DI | `…/di/AppModule.kt` |
| Contracts & sync design | `docs/` |

## Build

```bash
./gradlew assembleDebug      # Android SDK + JDK 17+
```

Local loop, backend, on-device testing: [docs/running-locally.md](docs/running-locally.md).
CI runs a real `assembleDebug`; the **Build APK** workflow publishes the APK on demand.
