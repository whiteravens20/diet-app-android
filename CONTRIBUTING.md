# Contributing to Diet App — Android

Thank you for considering a contribution. Read this before opening a PR.

## Status

Every screen the README lists under Features is built, with tests on the JVM and journeys
on emulators. There is no release yet; what is still to come is under Roadmap in
[docs/architecture.md](docs/architecture.md).

## Setup

**Requirements:** Android Studio (latest stable) or the Android SDK + JDK 17. A running
[Diet App backend](https://github.com/whiteravens20/diet-app) for anything beyond UI work.

```bash
git clone https://github.com/whiteravens20/diet-app-android
cd diet-app-android
./gradlew assembleDebug
./gradlew testDebugUnitTest    # the unit tests, on the JVM
```

The screens' tests (`app/src/androidTest/`) open the app on an emulator or a phone and
sign in to the diet-app instance at the app's address, which they need with its
catalogue loaded; each test makes the accounts it uses there, but for the notifications'
test, which reads an account made before the instance's worker starts.
[`start-diet-app.sh`](.github/scripts/start-diet-app.sh) starts such an instance of the
version in `contract/diet-app.env`, as CI does, on a machine with Docker, makes that
account and prints the Gradle arguments that name it. With an emulator running beside it:

```bash
./gradlew <the arguments it printed> connectedDebugAndroidTest
```

Without the arguments the notifications' test is skipped.

## Guidelines

- **Kotlin**, official code style; Jetpack Compose for UI; Material 3 for theming.
- **Architecture** — MVVM and repositories. New data access goes through a repository; a
  screen that has to stay readable without a network keeps a copy in the Room cache, and a
  change that makes sense without one goes through the queue of `data/sync/`
  ([docs/sync-strategy.md](docs/sync-strategy.md)).
- **The backend is the authority.** The app renders API data; it never computes
  nutrition itself.
- **API DTOs** are checked against a copy of the contract the
  [`diet-app`](https://github.com/whiteravens20/diet-app) API publishes; moving to another
  version of it is described in [docs/contracts/api-contract.md](docs/contracts/api-contract.md).
- **Texts of refusals** are written by the build from `contract/errors.json`, the API's own list
  of codes with their sentences in English and Polish. A code's text is changed in diet-app
  and copied here with the contract, not written in `strings.xml`.
- **AI-assisted code** — most of this project is written with AI coding tools, as the
  [README](README.md#how-the-code-is-written-and-checked) describes, and contributions may
  be too. Do not submit AI output that you cannot explain and defend in review: read it,
  test it and take responsibility for it.
- **Commits** — [Conventional Commits](https://www.conventionalcommits.org/), signed.
- **Branches** — branch off `dev`; PR into `dev`.

## Dependencies

The build checks every dependency and plugin it downloads, and resolves only the versions
written down in the repository:

- `gradle/verification-metadata.xml` names, for each group, the keys whose signatures the
  build accepts, and holds a SHA-256 checksum for every file that is not signed by one of
  them. The keys are in `gradle/verification-keyring.keys`, so no key server is asked
  during a build. A file that matches neither stops the build.
- `app/gradle.lockfile`, `buildscript-gradle.lockfile` (the plugins) and
  `settings-gradle.lockfile` hold every version the build resolves, transitive ones
  included.
- `gradle/wrapper/gradle-wrapper.properties` carries the checksum of the Gradle
  distribution.
- A library a build tool brings with a known vulnerability is lifted to the release that
  fixes it: in `build.gradle.kts` for the plugins, in `app/build.gradle.kts` for the
  Android plugin's test platform and Lint. A lift is removed once the tool brings that
  release itself.

Changing a dependency, or adding one, means writing the metadata and the lockfiles again:

```bash
./gradlew --write-verification-metadata sha256,pgp --export-keys --write-locks \
  assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
./gradlew -PtestBuildType=minified --write-verification-metadata sha256,pgp --export-keys --write-locks \
  assembleMinifiedAndroidTest :app:dependencies --configuration minifiedAndroidTestLintChecksClasspath
```

Read the diff before you commit it. A new entry under `trusted-keys`, or a new key in the
keyring, means the build will accept whatever that key signs for those groups: find out
who holds it, from its fingerprint and the project's own pages, first. Entries no longer
used stay in the file; to drop them, delete the `trusted-keys` and `components` elements
and run the command again.

A Dependabot update gets both written by the
[Dependency metadata](.github/workflows/dependency-metadata.yml) workflow, which commits
them to the pull request's branch. It does not commit a change to the signing keys: that
run fails and shows the change, and the workflow commits it when it is started by hand
with the pull request's number and `accept-keys`.

## Security

Never commit keystores or signing config. Report vulnerabilities privately — see
[SECURITY.md](SECURITY.md).
