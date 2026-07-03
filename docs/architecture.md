# Android Architecture

## Goal

An offline-first companion to the Diet App platform. The app **renders** data the backend
produces — it never computes nutrition itself. The backend is the single source of truth.

## Layers

```
ui/        Jetpack Compose screens + Material 3 theme + components.
           Each screen has a Hilt ViewModel exposing UI state as a StateFlow.
domain/    Plain app models, decoupled from the wire format.
data/
  remote/  Retrofit `DietApiService` (authenticated) + `AuthApiService`
           (token-free) + DTOs — the Kotlin mirror of the platform's
           `packages/shared` contract. `ApiException` types the error envelope.
  auth/    `TokenStore` (DataStore) + OkHttp interceptor/authenticator pair —
           attaches the bearer token and transparently refreshes it on 401.
  local/   Room `CacheDatabase` — offline copies of meal plans / shopping lists.
  repository/  Offline-first repositories: serve the cache, refresh from the API.
di/        Hilt modules wiring networking, JSON, auth plumbing, the cache DB.
```

## Offline-first flow

1. A screen's ViewModel asks a repository for data.
2. The repository returns the **Room cache** immediately (instant render, works offline).
3. In the background it calls the API; on success it updates the cache and re-emits.
4. On network failure the cached copy stands; the UI shows an "offline" indicator.

See [sync-strategy.md](sync-strategy.md) for write-sync (Phase 3+).

## Tech choices

- **Kotlin + Jetpack Compose + Material 3** — modern, declarative, themable. The Compose
  theme mirrors the web design system (emerald-forward palette).
- **Hilt** — compile-time DI.
- **Retrofit + OkHttp + kotlinx.serialization** — typed networking; DTOs mirror the
  platform contract.
- **Room** — the offline cache; plans/lists stored as the raw API JSON keyed by id.
- **DataStore** — auth tokens (wrap with the Android Keystore on capable devices).

## Module boundaries

`ui` depends on `domain`; `data` depends on `domain`; `domain` depends on nothing. DTOs
never leak into `ui` — repositories map `data.remote.*Dto` to `domain` models.

## Roadmap

The API binding, DTO mirror, auth plumbing (interceptor + token refresh) and
offline-first repositories are wired. Phase 3 builds the screen set (dashboard,
profile, meal plans, recipes, shopping list) over these repositories, then adds
the write-sync outbox and push notifications. The structural CI check is
replaced by a real Gradle build then.
