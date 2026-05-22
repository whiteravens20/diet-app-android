# Onboarding — for Developers & LLM Agents

Read this first if you are about to work on the Diet App Android companion.

## What this is

The Android companion to the [Diet App platform](https://github.com/whiteravens20/diet-app).
Offline-first; consumes the platform's REST API. **Phase 3 scaffold** — structure and
contracts are in place; feature screens are not yet built.

## The rule

The backend is the source of truth. The app **renders** API data and **caches** it for
offline use. It never computes nutrition, calorie targets or plans itself — that logic
lives in the platform's deterministic engine.

## Repository map

```
app/src/main/java/net/whiteravens/dietapp/
  ui/        Compose screens, theme, components (Hilt ViewModels)
  domain/    App models, decoupled from the wire format
  data/remote/      Retrofit binding + DTOs (mirror of the platform contract)
  data/local/       Room offline cache
  data/repository/  Offline-first repositories
  di/        Hilt modules
gradle/libs.versions.toml   Version catalogue
docs/        Architecture, sync strategy, API contract
```

## Conventions

- **Kotlin**, official style; **Jetpack Compose** + **Material 3** for UI.
- **MVVM + repository**, offline-first. New data access → a repository that reads the
  Room cache first, then refreshes from the API.
- **DTOs mirror** `packages/shared` in the platform repo (see
  [../contracts/api-contract.md](../contracts/api-contract.md)); never let DTOs leak into
  `ui` — map them to `domain` models.
- **Hilt** for DI; add bindings in `di/AppModule.kt`.
- **Commits** — Conventional Commits, signed, no `Co-Authored-By`. Branch off `dev`.

## Where to start a task

| Task | Start here |
|---|---|
| New screen | `ui/screens/` + a Hilt ViewModel + a repository |
| New API call | `data/remote/DietApiService.kt` + `Dtos.kt` |
| Caching / offline | `data/local/` + `data/repository/` |
| DI wiring | `di/AppModule.kt` |
| Contract change | `Dtos.kt` + `docs/contracts/api-contract.md` (coordinate with the platform repo) |

## Build & verify

```bash
./gradlew assembleDebug      # needs the Android SDK + JDK 17
./gradlew lintDebug
```

CI currently runs a structural check (`.github/workflows/test.yml`); it becomes a real
Gradle build when the app is built out in Phase 3.
