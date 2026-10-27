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
  remote/  Retrofit `DietApiService` (authenticated), `ModelApiService` (the
           routes the server waits for an AI provider on, given two minutes) +
           `AuthApiService` (token-free) + DTOs — the Kotlin mirror of the platform's
           `packages/shared` contract. `ApiException` types the error envelope.
  config/  The server's address, and the address of its web application, where the
           app opens in the browser what it leaves to it.
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

See [sync-strategy.md](sync-strategy.md) for write-sync (next).

## Tech choices

- **Kotlin + Jetpack Compose + Material 3** — modern, declarative, themable. The Compose
  theme mirrors the web design system (emerald-forward palette).
- **Hilt** — compile-time DI.
- **Retrofit + OkHttp + kotlinx.serialization** — typed networking; DTOs mirror the
  platform contract.
- **Room** — the offline cache; plans/lists stored as the raw API JSON keyed by id.
- **DataStore** — auth tokens, sealed under a key of the Android Keystore.

## Module boundaries

`ui` depends on `domain`; `data` depends on `domain`; `domain` depends on nothing. DTOs
never leak into `ui` — repositories map `data.remote.*Dto` to `domain` models.

## Where a change starts

| Change | Start here |
|---|---|
| New screen | `ui/screens/`, with a Hilt ViewModel and a repository behind it |
| New API call | `data/remote/DietApiService.kt`, or `ModelApiService.kt` where the server waits for an AI provider, and `Dtos.kt` |
| Caching or offline behaviour | `data/local/` and `data/repository/` |
| Dependency wiring | `di/AppModule.kt` |
| A library or plugin, or its version | `gradle/libs.versions.toml`, then the verification metadata and the lockfiles ([CONTRIBUTING.md](../CONTRIBUTING.md#dependencies)) |
| Contract change | `contract/`, then `Dtos.kt` until `ContractTest` passes ([contracts/api-contract.md](contracts/api-contract.md)) |

Check a change before sending it:

```bash
./gradlew assembleDebug      # needs the Android SDK and JDK 17+
./gradlew testDebugUnitTest  # the unit tests, on the JVM
./gradlew lintDebug
./gradlew connectedDebugAndroidTest  # the screens, on an emulator or a phone
./gradlew -PtestBuildType=minified connectedMinifiedAndroidTest  # the same, on the release build as R8 shrinks it
```

CI builds the debug APK and the release variant, shrunk by R8 with the rules in
`app/proguard-rules.pro`, runs the unit tests and Android Lint on every push and pull
request to `main` and `dev`, and the screens' tests on an emulator against a diet-app
instance it starts for them. Running the app against a backend is described in
[running-locally.md](running-locally.md).

## Roadmap

The API binding, the DTO mirror, the auth plumbing and the offline-first repositories are
in place, and the screens for signing in, the dashboard, meal plans, recipes, the
favourites, the shopping list, the pantry, the profiles with their preferences, the
account and the About screen are built on them, with tests on the JVM and journeys on
emulators. What the app
leaves to the web application (the calculator, a plan or a list as a table or printed,
favourite sets made and changed, and the account's password, address, name, language of
its mails, data in a file and deletion) it opens there, in the browser, signed in with a
one-time code that the page uses once its reader says the account is theirs. Next are the
rules the app still works out itself taken
from the API instead, the write-sync outbox described in [sync-strategy.md](sync-strategy.md),
and notifications the app asks the server for.
