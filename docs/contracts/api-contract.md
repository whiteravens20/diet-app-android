# API Contract

The Android app consumes the **same REST API** as the web platform. The authoritative
contract is [`packages/shared`](https://github.com/whiteravens20/diet-app/tree/main/packages/shared)
in the `diet-app` repo — Zod schemas with inferred TypeScript types.

The Kotlin DTOs in `app/src/main/java/.../data/remote/Dtos.kt` **mirror** those schemas.
A contract change in the platform is a coordinated change here.

## Base & auth

- Base URL: `<backend>/api` (emulator default `http://10.0.2.2:4000/api/`).
- `Authorization: Bearer <accessToken>`; refresh via `POST /auth/refresh`.
- Errors use the shared `ApiError` envelope: `{ statusCode, error, message, issues? }`.

## Endpoints consumed by the app

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` | Authentication + refresh-token revocation. |
| GET·PATCH | `/users/me` | Session user incl. `locale` (`en` \| `pl` …) and `theme` (`light` \| `dark` \| `system`); patch settings. |
| POST | `/users/me/password` | Change password (requires current). |
| DELETE | `/users/me` | Hard-delete account + cascade. Body: `{ currentPassword }`. |
| GET | `/profiles`, `/profiles/:id` | Profiles. |
| GET | `/profiles/:id/calories` | Deterministic calorie target. |
| GET | `/meal-plans?profileId=`, `/meal-plans/:id` | Meal plans (cached offline). |
| POST | `/meal-plans/generate`, `/meal-plans/swap-meal` | Plan operations. |
| GET | `/recipes`, `/recipes/:id` | Recipe library. |
| GET | `/shopping-lists/:id` | Shopping lists (cached offline). |
| PATCH | `/shopping-lists/:id/items/:itemId` | Tick / "already have". |
| GET·PUT·DELETE | `/ai/providers` | BYOK AI configuration. |

## i18n (F14)

The platform serves one resolved label per locale on every response — the API
itself returns one string per ingredient / recipe name and the Android client
just renders it. The client tells the server which locale to serve via:

- The signed-in `user.locale` (the API resolves it from `/users/me`).
- A `NEXT_LOCALE` cookie (web) or an `Accept-Language` header — Android sends
  the device locale on every authenticated request (`AuthInterceptor`).

Backend translation tables (`IngredientTranslation`, `RecipeTranslation`) are an
implementation detail — Android never queries them directly. The `Locale` enum
(`en | pl` today) lives in
[`packages/shared/src/settings.ts`](https://github.com/whiteravens20/diet-app/blob/main/packages/shared/src/settings.ts)
and is the contract for "which locales the platform speaks." Adding a new
locale on the platform side requires no Android change.

Backend errors carry stable `error:` codes (e.g. `EMAIL_TAKEN`,
`INVALID_CREDENTIALS`, `PROFILE_LIMIT_REACHED`); Android should translate from
the code, not the English `message`. See the
[F14 ADR](https://github.com/whiteravens20/diet-app/blob/main/docs/adr/0007-curated-vs-ai-translations.md)
for the curated vs AI translation lifecycle.

## Keeping DTOs in sync

When a schema changes in `packages/shared`:

1. Update the matching `@Serializable` class in `Dtos.kt`.
2. Update any repository mapping into `domain/`.
3. Bump this document.

Full endpoint and field detail: the platform's
[`docs/architecture/api.md`](https://github.com/whiteravens20/diet-app/blob/main/docs/architecture/api.md)
and the live OpenAPI at `<backend>/api/docs`.
