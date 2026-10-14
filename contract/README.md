# The contract this client was checked against

`openapi.json` is the description diet-app publishes of its API (`docs/api/openapi.json`),
copied from diet-app at the version in `API_VERSION` (from commit `4289e7f`, the build of the
`ghcr.io/whiteravens20/diet-app:edge-4289e7f` image). The unit tests compare every call of the
client with it: the route exists, the body is one the API takes, and the class an answer is
read into can read what the API answers.

It is moved on purpose, never by itself: copy the file from the version of diet-app the
client is to work with, change `API_VERSION`, and make the tests pass. Once a week CI
compares this copy with diet-app's `dev` and fails when they differ, so that a change of
the backend is seen within a week.
