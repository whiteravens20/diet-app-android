# API contract

The application talks to the REST API of [diet-app](https://github.com/whiteravens20/diet-app),
the same API its web application uses. The API describes itself in `docs/api/` of that
repository: `openapi.json` (every route, what it takes and what it answers), `routes.md`,
`errors.md` and `errors.json` (every code a refusal can carry, to read and to copy) and
`README.md`, whose section "Asking" has the rules a client that keeps its own tokens follows.

## The copy this client is checked against

[`contract/openapi.json`](../../contract/openapi.json) is a copy of that description, at the
version in `contract/API_VERSION` ([contract/README.md](../../contract/README.md)). The unit
tests compare every call of the client with it (`ContractTest`): the route exists, the query
and the body are ones the API takes, and the class an answer is read into can read what the
API answers. A call that does not fit yet is listed in that test with the reason; the change
that fixes it takes it off the list.

[`contract/errors.json`](../../contract/errors.json) is a copy of the list of codes, from the
same version. The build writes the app's texts for refusals from it, in English and in Polish, and
a unit test (`ErrorTextsTest`) checks that every code of the list has its text. Every day CI
compares both copies with diet-app's `dev` ([contract.yml](../../.github/workflows/contract.yml))
and fails when they differ. Every night that diet-app's `dev` has moved, the screens' tests also
run against an instance of that commit
([diet-app-dev.yml](../../.github/workflows/diet-app-dev.yml)).

## Moving to another version of the API

1. Copy `docs/api/openapi.json` and `docs/api/errors.json` of the diet-app version the client
   is to work with into `contract/`, and write that version into `contract/API_VERSION`. Write its commit and
   the digest of its image into `contract/diet-app.env`, read from the registry
   (`docker buildx imagetools inspect ghcr.io/whiteravens20/diet-app:<tag>`), never typed.
2. Run `./gradlew testDebugUnitTest`: `ContractTest` names every call that no longer fits,
   and the texts of a code that is new or gone change with the build.
3. Change the classes in `data/remote/`, and their mapping into `domain/`, until it passes.

## What the client sends

- Every request goes to the address the user chose, which ends in `/api/`.
- Registering, signing in, refreshing and signing out go through a client without the access
  token, and so does `GET /config`. Every other call carries `Authorization: Bearer <access
  token>` and `Accept-Language` with the device's language.
- A call is given half a minute, and an answer that stops coming ten seconds. The routes on
  which the API waits for an AI provider before it answers, a minute at most for a model, are
  given two minutes and as long for the answer to begin (`ModelApiService`).
- `GET /config` is asked when the app starts and whenever the server's address changes. A
  server that answers another version than `contract/API_VERSION` is named on the screen,
  and one whose answer the app cannot read, or that has no such route, is shown as running
  a version the app does not support; the app talks to the server either way.

## What a person reads when a request fails

A refusal carries a stable code in `error` (`INVALID_CREDENTIALS`, `EMAIL_TAKEN`, …), and the
app words it as the web application does, from the code and never from the English `message`:

- a 429 with `Retry-After` says how long to wait, in seconds, or in minutes past a minute and
  a half;
- `VALIDATION_FAILED` names the fields its `issues` name, and the sign-in form marks them;
- `NO_ELIGIBLE_RECIPE` names the meal and the diet its `details` name, and tells a catalogue
  without a recipe for them from a profile that rules out every one there is;
- any other code reads as its sentence in `contract/errors.json`, in the language of the app;
- a code the copy does not have yet, from a newer server, is shown in the words the API sent,
  which are English;
- an answer that is not the API's, such as a proxy's error page, is named by its status, and no
  answer at all is a connection that failed.
