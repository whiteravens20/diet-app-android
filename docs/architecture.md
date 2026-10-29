# Android Architecture

## Goal

An offline-first companion to the Diet App platform. The app **renders** data the backend
produces — it never computes nutrition itself. The backend is the single source of truth.

## Layers

- `ui/` holds the Jetpack Compose screens, the Material 3 theme and the shared components.
  Each screen has a Hilt ViewModel that exposes its state as a `StateFlow`.
- `domain/` holds the app's own models, which do not depend on the wire format.
- `data/remote/` holds the Retrofit services: `DietApiService` for the signed-in calls,
  `ModelApiService` for the routes on which the server waits for an AI provider, given two
  minutes, and `AuthApiService` for the calls without a token. Its DTOs are held by the
  unit tests to a copy of the contract the API publishes, in `contract/`, and
  `ApiException` types the API's envelope of a refusal.
- `data/config/` keeps the server's address, and the address of its web application, where
  the app opens in the browser what it leaves to it, and the colour scheme the app is drawn in:
  one chosen on the phone, else the account's, which every answer about the account says.
- `data/auth/` keeps the tokens in `TokenStore` (DataStore, sealed under a key of the
  Android Keystore), and an OkHttp interceptor and authenticator attach the access token and
  refresh it on a 401 that says it is no good.
- `data/local/` is the Room `CacheDatabase`: copies of meal plans and shopping lists, and the
  changes made on the phone that wait to be sent.
- `data/sync/` is `ChangeQueue`: a change goes to the server at once, or is kept and sent in
  its turn by `ChangesWorker` once there is a network.
- `data/repository/` joins them: a repository asks the API, and for meal plans and shopping
  lists keeps a copy in the cache that is shown first.
- `data/notify/` keeps whether the phone's notifications are on and which were shown, and
  `notify/` holds the background work that reads them and what the phone shows.
- `di/` holds the Hilt modules that wire the network, JSON, the tokens and the cache's
  database together.

## Offline-first flow

1. A screen's ViewModel asks a repository for data when the screen opens, when it is pulled
   down, and when it is shown again (from another screen at once, on coming back to the front
   at most every thirty seconds).
2. For plans and shopping lists the repository returns the **Room cache** first (instant
   render, works offline), then calls the API and, on success, updates the cache.
3. On network failure what the screen read last stands, and the UI says it is offline and
   since what time; a "not found" answer drops the copy of what was deleted elsewhere.

See [sync-strategy.md](sync-strategy.md) for the whole of reading, the phone's notifications,
and the changes made without a network.

## Tech choices

- **Kotlin + Jetpack Compose + Material 3** — modern, declarative, themable. The Compose
  theme mirrors the web design system (emerald-forward palette).
- **Hilt** — compile-time DI.
- **Retrofit + OkHttp + kotlinx.serialization** — typed networking; the DTOs are held to
  the copy of the API's contract.
- **Room** — the offline cache: each copy of a plan or a list is the app's own wire class
  as JSON under its id, and each change waiting to be sent is JSON as well.
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
| The phone's notifications | `data/notify/` (on or off, what was shown, the schedule) and `notify/` (the background work, what the phone shows) |
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

CI builds the debug APK and the release variant, its code and resources shrunk by R8 with
the rules in `app/proguard-rules.pro`, runs the unit tests and Android Lint on every push
and pull request to `main` and `dev`, and the screens' tests on Android 14 and 17 emulators
and on the release build as R8 shrinks it, against a diet-app instance it starts for them.
Running the app against a backend is described in [running-locally.md](running-locally.md).

## Roadmap

The API binding, the DTO mirror, the auth plumbing and the offline-first repositories are
in place, and the screens for signing in, the dashboard with the account's notifications,
meal plans, recipes, the
favourites, the shopping list, the pantry, the profiles with their preferences, the
account and the About screen are built on them, with tests on the JVM and journeys on
emulators. What the app
leaves to the web application (the calculator, a plan or a list as a table or printed,
favourite sets made and changed, and the account's password, address, name, language of
its mails, data in a file and deletion) it opens there, in the browser, signed in with a
one-time code that the page uses once its reader says the account is theirs. The changes
that make sense without a network (a shopping list's rows, meals eaten, a day's own meals
and meals taken off it, weights, the pantry, recipes' marks and notifications read) wait for
one in a queue, described in [sync-strategy.md](sync-strategy.md).

Still to come is the first release: a signing key, a release workflow and the round with
testers before it.
