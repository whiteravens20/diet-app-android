# Offline & Sync Strategy

## Principle

The phone is a **cache + view** of the backend. The backend owns all state and all
nutrition computation. The app must be useful with no connection, and reconcile cleanly
when one returns.

## Read sync (implemented)

- A screen reads what it shows anew when it opens, when it is pulled down, and when it is
  shown again: at once after another screen of the app, which may have changed what it
  shows, and at most every thirty seconds when the app comes back to the front, so that
  switching between apps does not spend the API's limit of requests. There are no change
  cursors: one account's data is a few small pages.
- The first page of each profile's plans, every plan opened and the shopping lists are
  mirrored into the Room cache (`CachedPlanList`, `CachedMealPlan`, `CachedShoppingList`)
  as the raw response JSON with the time they were read. Those screens show the copy first
  and the answer of the network when it comes.
- A screen whose reading anew fails for want of a network keeps what it read and says
  when: "Offline — as of 14:32". Plans and lists are read-only offline.
- A read answered "not found" (the plan, the list or the profile was deleted elsewhere, or
  is not the account's) drops the copy of it; a list read anew replaces the copies of its
  lists whole; signing out drops every copy.
- An answer read from before a change made on the same screen is left aside, so that a
  row ticked or a mark set while the screen was being read stays as it is.

## Notifications on the phone (implemented)

- Off until the user turns them on in the account's settings. Turned on, a periodic job of
  WorkManager reads `GET /notifications/unread` every fifteen minutes, the shortest period
  Android runs, when there is a network; on a phone at rest Android may put it off for hours.
  The same read runs at once whenever the application comes to the front, at most every
  thirty seconds, as the screens read anew. The app reads the route also whenever the
  dashboard is shown again, for its bell.
- Each notification is shown on the phone once, known by its id and the time it was made;
  the phone keeps the keys of the unread ones alone. Nothing is marked read from the
  phone's notification: a tap opens the application's list.
- No device registers anywhere and no service of a third party takes part: the server keeps
  the notifications for the app to read. Signing out turns them off and forgets what was
  shown.

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

Access + refresh tokens live in `DataStore`, sealed with AES-GCM under a key the Android
Keystore holds. An OkHttp interceptor attaches the access token and transparently refreshes
it on a 401 — the same contract as the web client. Sign-out clears tokens and the cache.

## What is never synced

Nutrition math. The app never computes calories or macros — it only renders values the
backend returns. This preserves the platform's determinism guarantee.
