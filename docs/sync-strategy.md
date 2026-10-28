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
  when: "Offline — as of 14:32", and how many changes made on the phone wait to be sent.
  Plans are read-only offline; a shopping list's rows are changed offline as well (below).
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

## Changes made without a network (being built)

Some changes are made without a network as well, as they are in a shop or a kitchen without
a signal: so far a shopping list's row, its tick and its amounts. Everything else that
changes something asks the server first, and is off while there is no network.

1. A change goes to the server at once when nothing waits before it. When the server cannot
   be reached, or another change waits, it is kept in the cache's database
   (`pending_change`: the change as JSON, in the order it was made) and shown at once over
   what the screen read, marked "Waiting to be sent". A screen whose change met no network
   says it is offline, and how many changes wait.
2. The changes waiting are sent in the order they were made, one at a time, by a
   `WorkManager` job as soon as there is a network, and whenever the app comes to the
   front. Sent, the copy the server answers is kept, and the screen reads it and is no
   longer offline.
3. A value set, such as a tick or an amount, is sent again as it is. A change another write
   overlapped on the server (`PLAN_CHANGED`, `WRITE_CONFLICT`) is sent again. A server that
   cannot be reached, asks to wait (429), fails (5xx) or has no session for it keeps the
   changes for later.
4. A change the server refuses otherwise is not sent again: it stays, with the server's
   words for why, in a notice over every screen until it is seen.
5. Signing out, or going to another server, while changes wait asks first: send them and
   sign out, or drop them. A session the server ends takes them with it, as it takes every
   copy.
6. The cache's tables are migrated from one version to the next, the schema of each kept in
   `app/schemas/`, so that an update keeps the changes waiting.

## Auth

Access + refresh tokens live in `DataStore`, sealed with AES-GCM under a key the Android
Keystore holds. An OkHttp interceptor attaches the access token and transparently refreshes
it on a 401 — the same contract as the web client. Sign-out clears tokens and the cache.

## What is never synced

Nutrition math. The app never computes calories or macros — it only renders values the
backend returns. This preserves the platform's determinism guarantee.
