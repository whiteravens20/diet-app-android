# Diet App — Android Companion

> Offline-first Android companion for the Diet App platform.

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![CI](https://github.com/whiteravens20/diet-app-android/actions/workflows/test.yml/badge.svg)](https://github.com/whiteravens20/diet-app-android/actions)
[![CodeQL](https://github.com/whiteravens20/diet-app-android/actions/workflows/codeql.yml/badge.svg)](https://github.com/whiteravens20/diet-app-android/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/whiteravens20/diet-app-android/badge)](https://scorecard.dev/viewer/?uri=github.com/whiteravens20/diet-app-android)

The companion app for [**Diet App**](https://github.com/whiteravens20/diet-app), the
self-hostable diet and meal-planning platform. It signs in to your own Diet App server,
shows what the platform computed and lets you do the day-to-day things on a phone:
generate a plan, swap a meal, tick off the shopping list. What it leaves to the
platform's web application, it opens there, in the phone's browser, signed in to your
account once you say on the page that the account it names is yours.

> **Status: the main screens work; nothing is released yet.** Signing in, the dashboard,
> meal plans, recipes, the shopping list and the profile are built and talk to the
> backend. Meal plans and shopping lists stay readable offline, and a shopping list's rows
> are changed offline as well: each change waits to be sent.

> [!WARNING]
> **Early development — not production ready.** This app and the Diet App
> platform are under active development. The API contract, data model and
> module structure may change without notice, and the project has not had an
> independent security review. Build it to experiment, not for anything you depend on yet.

## Features

- **Your own server.** Sign in or create an account on the Diet App instance you run, or
  ask for a forgotten password by mail where the instance sends mail. The server address
  can be changed in the app, so one build works with any instance; it is checked before it
  is kept, and a server whose version the app was not checked against is named on the
  screen.
- **Dashboard.** A profile's daily calorie target, with maintenance, the daily deficit,
  meals per day and the target macro split, each macronutrient by its share of the energy
  and its grams; its weight log, today's weight logged in the
  account's unit and the last one taken back, with ninety days of weights drawn beside the
  mean of each seven days and the weight it works towards. A profile's favourite sets are
  made and changed in the web application, which its card opens in the browser. The bell
  in its header counts the notifications not read yet, each with the profile it is for: a
  reminder to log a weight, which leads to that profile's weights, and a request to review
  a profile's preferences after a catalogue update took away something it had marked,
  which leads to the profiles; each can be put away, or all read at once.
- **Notifications on the phone**, off until you turn them on in the account's settings
  (Android 13 and later ask for the permission then): the app asks the server for new
  notifications about every fifteen minutes and whenever you come back to it, and shows
  each once, with the profile it is for, and a tap opens the list. Android may put the checks off for hours on a phone at
  rest; the settings say when the system holds the app back to save battery, and open the
  page where that is changed. Signing out turns them off.
- **Meal plans.** The plans of a profile and a month of them as a calendar, a new plan
  generated from the phone, each plan day by day with its totals and today marked, with a
  way to it from any other day, a meal swapped for
  another recipe, and a favourite set put on days of the plan. A day under way can be set
  apart (a training or rest day, skipped, another number of meals, a target of its own),
  with what that does said before it is saved, and a meal taken off its day or replaced by
  one of your own. The plan as a table or printed is the web application's, which the
  plan's menu opens in the browser.
- **Recipes.** Search in the recipe library; a recipe with its nutrition per serving,
  ingredients and steps, put among a profile's favourites from its view, and the
  ingredients of its lines marked as a profile's favourites or as ones it avoids. Your own
  recipes in a tab of their own: written by hand from the catalogue's ingredients, with
  what a serving holds worked out as you write, or drafted by a model; each edited into a
  new version from its view, suggested for the shared library unless a swap made it, or
  deleted.
- **Shopping list.** Generated from a plan, grouped by store section, with what the
  pantry already covers, items ticked off as you shop, and what is left to buy sent as a
  text. The list as a table or printed is the web application's, which the list's menu
  opens in the browser.
- **Profiles.** The first profile set up in three steps from the empty dashboard, with
  what its figures come to as they are typed; profiles made, edited and deleted up to what
  the instance allows, each pace of loss with what it comes to in a day, and the risk of a
  loss for somebody under 18 or underweight to acknowledge; a profile's allergens,
  cuisines and lists of ingredients changed in place.
- **More.** What the bottom bar has no room for, as the web application's menu has it:
  the profiles, the favourites, the pantry and the account's settings: its e-mail status
  and the confirmation mail sent again, the unit body weights are shown in (the app itself
  follows the phone's language and theme), what AI it has (none, the instance's provider
  where the operator lets the account use it, with what is left of the month's requests
  and until when the account counts as new, or what it takes where the operator does not,
  or providers of its own, each tested before it is saved, its key shown as dots and never
  again), two-step sign-in set up with an
  authenticator app on the phone or on another device and its recovery codes, where it is
  signed in with this phone named and one session or all signed out; the server address
  and sign-out. Where AI is off, an action that asks a model says so and
  leads to these settings. An address not yet confirmed is said over every screen, on an
  instance that sends mail, with a button that sends the link again. The calculator, and the account's password, address, name, the
  language of its mails, its theme and palette, its data in a file and its deletion, are
  the web application's, which More and the account's settings open in the browser.
  Under About, what the instance and the app are: the server's version and what the
  instance allows, its operator's address, its help page and terms, the app's version, the
  legal notices the licence asks for and the licences of the libraries the app ships.
- **Favourites.** The recipes a profile marked as a favourite, as eaten often or to avoid,
  narrowed by words, meal, mark and tag and put in order; each recipe's mark and tags
  changed, or its mark taken off.
- **Pantry.** What a profile has at home, under the categories of the catalogue: an
  ingredient found by a part of its name and put in with how much of it there is, in a
  unit the server can count it in, and the day it is best eaten before; each row's amount
  and day changed, or the row taken out, and a row whose day is near or gone said so. The
  plans, swaps and shopping lists the server makes count on it.
- **Up to date, and offline.** Every screen reads what it shows anew when it opens, when you
  pull it down, and when you come back to it: at once from another screen of the app, and at
  most every thirty seconds when you return to the app. A screen that cannot reach the
  server keeps what it read last and says what time that was. Meal plans and shopping lists
  you have opened stay readable without a connection. A meal is marked eaten, a weight
  logged or the last one removed, and a shopping list's rows are ticked and their amounts
  told, without one as well: each change is marked as waiting, the screen says how many
  wait, and each goes by itself once there is a network; one the server refuses is said,
  with why. Signing out while changes wait asks first whether to send them or drop them.
- **English and Polish**, refusals of the server included: a request the server refuses is
  explained in the app's language, in the words the web application uses.

The app never computes nutrition, calorie targets or meal plans: every such figure on the
screen comes from the backend.

## Install

There is no published build yet, so the APK is built from source. You need Android
Studio, or the Android SDK with JDK 17 or newer, and a device or emulator with Android
10 (API 29) or newer.

```bash
git clone https://github.com/whiteravens20/diet-app-android
cd diet-app-android
./gradlew assembleDebug            # -> app/build/outputs/apk/debug/app-debug.apk
```

Without a local toolchain, run the **Build debug APK** workflow in your fork and download the
`diet-app-debug-apk` artifact.

## Run

The app needs a running [Diet App](https://github.com/whiteravens20/diet-app) backend.
A debug build's address is `http://10.0.2.2:4000/api/` by default, which is the host
machine as the Android emulator sees it; a release build starts with none and asks for one
on its first screen. For a phone on your network, set another one at build time:

```bash
./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.20:4000/api/
```

or change it in the app, with the **Server** button on the sign-in screen or under
More → Account settings. The app adds `/api/` and `https://` where you leave them out, refuses an address
with a user name or a password in it, and asks the server at once whether a Diet App API
answers there; one where none does is named, and kept only if you save it anyway.
Changing the server signs you out and clears the offline cache.

A debug build accepts a plain `http://` address, which a backend on your own network
usually has. A release build accepts HTTPS only, and trusts a certificate from an authority
you installed on the phone as well as the usual public ones. On Android 17 and later a
server on your own network also needs the permission for nearby devices, which the app asks
for before it talks to the server.

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
- `data/auth/` keeps the access and refresh tokens in DataStore, sealed under a key of the
  Android Keystore, and attaches them to requests; a request answered with 401 is retried
  with a refreshed token.
- `data/config/` keeps the server address chosen in the app.
- `data/local/` is the Room cache of meal plans and shopping lists.
- `data/notify/` keeps whether the phone's notifications are on and which were shown, and
  schedules the background work of `notify/`, which reads and shows them.
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

This app is built using AI coding tools. The tools write most of the code and
documentation; the maintainer decides what gets built and is responsible for everything
that lands here. The app is in early development: there is no release yet and no
independent security review. Before the first release is published, a group of human
testers will try the release candidate at the maintainer's request.

**What a change goes through**

- Every push and pull request has to compile into a debug APK and into the release
  variant, which R8 shrinks, pass Android Lint without an error and pass the unit tests,
  which run on the JVM without a device: every call of the client against a copy of the
  contract the API publishes ([contract/](contract/README.md)), a text for every code the
  API refuses a request with, the wire classes against answers a running backend gave, the
  network stack against a local server, and the screens' view models
  ([test.yml](.github/workflows/test.yml)). A defect the tests found that the code does
  not fix yet stays in the suite as a test that passes while the defect is there and fails
  once it is gone.
- The screens' tests run on Android 14 and Android 17 emulators on every push and pull
  request, and on the release build as R8 shrinks it, against a diet-app instance started
  in the same job from the image of the version `contract/` was copied from, pinned by its
  digest, with its catalogue loaded. Their report, the app's log of every test, the
  screenshots they take and the instance's log are kept with the run
  ([test.yml](.github/workflows/test.yml)).
- CodeQL and a Trivy scan of the repository run on every push and pull request, and again
  every week; Trivy fails the run on a high or critical vulnerability in a version the
  release build ships. Every action a workflow uses is pinned to a commit: a run fails
  when a pin is not the commit its version tag names, and the pinned versions are looked
  up in GitHub's advisory database ([codeql.yml](.github/workflows/codeql.yml),
  [security.yml](.github/workflows/security.yml)).
- The graph of every Gradle dependency, the build's own tools included, is handed to
  GitHub on every push and pull request, so that Dependabot's alerts cover them
  ([dependency-graph.yml](.github/workflows/dependency-graph.yml)). On a pull request, a
  dependency review fails when the change brings in a library the release build ships
  that has a known high-severity vulnerability, or whose licence is not on the list of
  licences that combine with AGPL-3.0 ([security.yml](.github/workflows/security.yml)).
- Every dependency and plugin the build downloads is checked against the signing keys and
  checksums in `gradle/verification-metadata.xml`, the Gradle distribution against its
  checksum, and only the versions in the lockfiles resolve. For a Dependabot update a
  workflow writes the metadata and the lockfiles again: it builds the update without write
  access and commits the result to the pull request, except a change to the signing keys,
  which it commits only when started by hand
  ([dependency-metadata.yml](.github/workflows/dependency-metadata.yml)).
- Every day the copy of the contract is compared with diet-app's `dev`, and the run fails
  when they differ ([contract.yml](.github/workflows/contract.yml)). Every night that
  diet-app's `dev` has moved, the screens' tests also run on an Android 14 emulator against
  an instance of that commit, its image built in the run from the commit's source
  ([diet-app-dev.yml](.github/workflows/diet-app-dev.yml)).
- `dev` and `main` are protected: a change has to pass the build, the screens' tests and
  the check of the pinned actions (on `main` also CodeQL and the Trivy scan), commits have
  to be signed, and nothing is force-pushed or deleted. Until the first release the
  maintainer can still push past the checks. The protection of `main` is checked for
  drift every day ([branch-protection-audit.yml](.github/workflows/branch-protection-audit.yml)).
- Dependabot's patch updates are merged by a workflow once the required checks pass
  ([dependabot-auto-merge.yml](.github/workflows/dependabot-auto-merge.yml)).
- Commits are signed, and the [OpenSSF
  Scorecard](https://scorecard.dev/viewer/?uri=github.com/whiteravens20/diet-app-android)
  results are public.

**What the maintainer decided and read**

- The backend is the source of truth. The app shows and caches what the API returns and
  never computes nutrition, calorie targets or meal plans itself.
- The offline-first MVVM structure and the split between wire DTOs and `domain` models
  were specified by the maintainer.

If something looks wrong, open an issue. For a vulnerability, follow
[SECURITY.md](SECURITY.md).

## License

[GNU Affero General Public License, version 3](LICENSE) (AGPL-3.0-only), with one
additional term in [NOTICE](NOTICE) — © 2026 White Ravens.

You are free to use, change and share the app. Anyone who distributes it, or a version
based on it, has to do so under the same licence with the source code available, and has
to keep the attribution "Diet App Android Companion by White Ravens" together with the
address of this repository.
