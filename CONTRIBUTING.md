# Contributing to Diet App — Android

Thank you for considering a contribution. Read this before opening a PR.

## Status

This is a **Phase 3 scaffold**. The current focus is the project structure, the
offline-first architecture and the integration contract — not feature screens.

## Setup

**Requirements:** Android Studio (latest stable) or the Android SDK + JDK 17. A running
[Diet App backend](https://github.com/whiteravens20/diet-app) for anything beyond UI work.

```bash
git clone https://github.com/whiteravens20/diet-app-android
cd diet-app-android
./gradlew assembleDebug
```

## Guidelines

- **Kotlin**, official code style; Jetpack Compose for UI; Material 3 for theming.
- **Architecture** — MVVM + repository, offline-first. New data access goes through a
  repository that reads the Room cache first and refreshes from the API.
- **The backend is the authority.** The app renders API data; it never computes
  nutrition itself.
- **API DTOs** mirror the platform contract — see
  [docs/contracts/api-contract.md](docs/contracts/api-contract.md). A contract change is
  coordinated with the [`diet-app`](https://github.com/whiteravens20/diet-app) repo.
- **AI-assisted code** — most of this project is written with AI coding tools, as the
  [README](README.md#how-the-code-is-written-and-checked) describes, and contributions may
  be too. Do not submit AI output that you cannot explain and defend in review: read it,
  test it and take responsibility for it.
- **Commits** — [Conventional Commits](https://www.conventionalcommits.org/), signed.
- **Branches** — branch off `dev`; PR into `dev`.

## Security

Never commit keystores or signing config. Report vulnerabilities privately — see
[SECURITY.md](SECURITY.md).
