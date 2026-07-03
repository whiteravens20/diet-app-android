# Offline & Sync Strategy

## Principle

The phone is a **cache + view** of the backend. The backend owns all state and all
nutrition computation. The app must be useful with no connection, and reconcile cleanly
when one returns.

## Read sync (implemented)

- Every API read is mirrored into the Room cache (`CachedMealPlan`, `CachedShoppingList`),
  stored as the raw response JSON with a `syncedAt` timestamp.
- Repositories serve the cache first, then refresh from the API in the background.
- Offline → the cached copy is shown with an "offline / last synced …" indicator.
- A pull-to-refresh forces a network fetch.

## Write sync (next)

Most actions (generate a plan, swap a meal, edit a shopping item) are backend operations.
Offline writes are queued and replayed:

1. The user action is applied **optimistically** to the local cache and an entry is
   appended to an outbox table (`operation`, `payload`, `createdAt`).
2. A `WorkManager` job drains the outbox when connectivity returns, calling the API.
3. Conflicts are resolved **server-wins** — the backend is authoritative; on a rejected
   replay the local optimistic change is rolled back and the user is notified.
4. Checkbox-style state (shopping item `checked`, `alreadyHaveQuantity`) uses
   last-write-wins, which is acceptable for single-user data.

## Auth

Access + refresh tokens live in `DataStore`. An OkHttp interceptor attaches the access
token and transparently refreshes it on a 401 — the same contract as the web client.
Sign-out clears tokens and the cache.

## What is never synced

Nutrition math. The app never computes calories or macros — it only renders values the
backend returns. This preserves the platform's determinism guarantee.
