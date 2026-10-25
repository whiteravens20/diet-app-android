# The contract this client was checked against

`openapi.json` is the description diet-app publishes of its API (`docs/api/openapi.json`),
copied from diet-app at the version in `API_VERSION` (from commit `6a055f3`, the build of the
`ghcr.io/whiteravens20/diet-app:dev-6a055f3` image). The unit tests compare every call of the
client with it: the route exists, the body is one the API takes, and the class an answer is
read into can read what the API answers.

`errors.json` is diet-app's list of every code a refusal can carry (`docs/api/errors.json`),
with its sentence in English and in Polish, from the same commit. The build writes the app's texts
for refusals from it, and a unit test checks that every code of the list has one.

`diet-app.env` names that version's commit and its image, by digest: the screens' tests run
against an instance of it.

It is moved on purpose, never by itself: copy both files from the version of diet-app the
client is to work with, change `API_VERSION` and `diet-app.env`, and make the tests pass. Every day CI
compares this copy with diet-app's `dev` and fails when they differ, so that a change of
the backend is seen within a day.
