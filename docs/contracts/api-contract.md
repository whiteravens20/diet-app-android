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
| POST | `/auth/register`, `/auth/login`, `/auth/refresh` | Authentication. |
| GET | `/profiles`, `/profiles/:id` | Profiles. |
| GET | `/profiles/:id/calories` | Deterministic calorie target. |
| GET | `/meal-plans?profileId=`, `/meal-plans/:id` | Meal plans (cached offline). |
| POST | `/meal-plans/generate`, `/meal-plans/swap-meal` | Plan operations. |
| GET | `/recipes`, `/recipes/:id` | Recipe library. |
| GET | `/shopping-lists/:id` | Shopping lists (cached offline). |
| PATCH | `/shopping-lists/:id/items/:itemId` | Tick / "already have". |
| GET·PUT·DELETE | `/ai/providers` | BYOK AI configuration. |

## Keeping DTOs in sync

When a schema changes in `packages/shared`:

1. Update the matching `@Serializable` class in `Dtos.kt`.
2. Update any repository mapping into `domain/`.
3. Bump this document.

Full endpoint and field detail: the platform's
[`docs/architecture/api.md`](https://github.com/whiteravens20/diet-app/blob/main/docs/architecture/api.md)
and the live OpenAPI at `<backend>/api/docs`.
