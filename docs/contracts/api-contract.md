# API contract

The application talks to the REST API of [diet-app](https://github.com/whiteravens20/diet-app),
the same API its web application uses. The API describes itself in `docs/api/` of that
repository: `openapi.json` (every route, what it takes and what it answers), `routes.md`,
`errors.md` (every code a refusal can carry) and `README.md`, whose section "Asking" has the
rules a client that keeps its own tokens follows.

## The copy this client is checked against

[`contract/openapi.json`](../../contract/openapi.json) is a copy of that description, at the
version in `contract/API_VERSION` ([contract/README.md](../../contract/README.md)). The unit
tests compare every call of the client with it (`ContractTest`): the route exists, the query
and the body are ones the API takes, and the class an answer is read into can read what the
API answers. A call that does not fit yet is listed in that test with the reason; the change
that fixes it takes it off the list. Once a week CI compares the copy with diet-app's `dev`
([contract.yml](../../.github/workflows/contract.yml)) and fails when the two differ.

## Moving to another version of the API

1. Copy `docs/api/openapi.json` of the diet-app version the client is to work with into
   `contract/`, and write that version into `contract/API_VERSION`.
2. Run `./gradlew testDebugUnitTest`: `ContractTest` names every call that no longer fits.
3. Change the classes in `data/remote/`, and their mapping into `domain/`, until it passes.

## What the client sends

- Every request goes to the address the user chose, which ends in `/api/`.
- Registering, signing in, refreshing and signing out go through a client without the access
  token. Every other call carries `Authorization: Bearer <access token>` and
  `Accept-Language` with the device's language.

A refusal carries a stable code in `error` (`INVALID_CREDENTIALS`, `EMAIL_TAKEN`, …), which the
client translates from, never from the English `message`; `issues` names the fields a request
failed on.
