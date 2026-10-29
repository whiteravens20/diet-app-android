# Answers of a running backend

Each file is what a running diet-app answered one call of this client: `config.json` from API
0.147.0, captured on 2026-10-29 from an install of 0.139.0 upgraded to the candidate `edge-c924d67`;
`ingredients-search-egg.json`, `ingredients-search-milk.json`, `ingredients-by-ids.json`,
`pantry.json` (six eggs best before in five days, a litre of milk with no day, three cloves of
garlic in twenty days and 212.4 g of oats in two hundred), `pantry-row-added.json`,
`pantry-row-changed.json`, `weights-days.json` (a weight every other day at noon in UTC for
three weeks, by day in Europe/Warsaw from ten days back), `reroll-preview.json`,
`reroll-preview-replace-eaten.json` and `reroll-preview-skipped-day.json` (a plan of three days
from today, the first day's breakfast eaten and the second skipped with its breakfast eaten: the
whole plan, the same with the eaten meals replaced, and the skipped day alone),
`calorie-preview-refused.json`, `calorie-preview-split-refused.json`,
`calorie-preview-split-sum-refused.json`, `profile-refused.json`, `profile-name-too-long.json`,
`preferences-refused.json`, `plan-refused.json`, `custom-meal-refused.json`,
`day-settings-preview-refused.json`, `weight-refused.json` and `recipes-refused.json` (each form's
fields sent out of their bounds), `ai-provider-refused.json` (a model, a key and a place past their
bounds), `auth-register-weak-password.json` (a password refused for three things) and
`preferences-not-whole-and-unknown.json` (a number that is not whole beside a key the
route does not know) from the same version, captured the same day from an instance of diet-app's
`dev` at `3b70f02`; `own-recipe-preview.json` and `own-recipe-preview-refused.json` (what a
serving of oats, milk and eggs holds, and a serving asked about with thirteen servings and a
hundred kilograms of oats), `own-recipe-created.json`, `own-recipe-already-yours.json` (the same
recipe saved again), `own-recipe-updated.json` (the recipe edited into three servings and a third
step), `own-recipe-invalid.json` (a name with markup), `own-recipe-invalid-places.json` (an empty
name, an empty step before one with markup, more oats than one serving can hold and a note with
markup), `own-recipe-no-steps.json` (steps that are all empty), `own-recipe-refused.json` (fields
past their bounds) and `ingredients-of-own-recipe.json` (the recipe's ingredients asked for by
their ids) from the same version, captured the same day from the same instance;
`weights.json`, `weight-logged.json`, `weight-entry-unknown.json` and `users-me-in-pounds.json`
from API 0.19.0, captured on 2026-10-16;
`account-sessions.json`, `account-session-unknown.json`, `second-factor-off.json`,
`second-factor-setup.json`, `second-factor-code-wrong.json`, `second-factor-on.json`,
`second-factor-state-on.json`, `second-factor-new-codes.json`, `pantry-quantity-too-large.json`,
`pantry-unit-not-convertible.json` and `pantry-row-unknown.json` from API 0.19.0, captured on
2026-10-17; `ai-providers.json`, `ai-providers-none.json`,
`ai-providers-key-unreadable.json` (the stored key damaged in the instance's database first),
`ai-provider-saved.json`, `ai-provider-ollama-saved.json`, `ai-provider-replaced.json`,
`ai-provider-host-not-allowed.json`, `ai-provider-unknown.json`, `ai-test-connected.json` (an Ollama
server standing in for a model), `ai-test-host-not-allowed.json`,
`ai-test-anthropic-without-a-model.json`, `ai-quota-off.json`, `ai-quota-admin.json`,
`ai-quota-byok.json`, `ai-quota-instance-used-up.json` (the instance's monthly limit lowered under
what it had used, for the one call), `users-me-ai-admin.json` and
`ai-mode-byok-without-a-provider.json` from API 0.77.0, captured on 2026-10-18 from an instance of
diet-app's `dev`, whose AI routes are those of `0fb1cef`; `profile-created-as-the-web-does.json`,
`profile.json` and `profiles.json` from API 0.77.0, captured on 2026-10-25 from an instance of
diet-app's `dev` at `6a055f3`; `recipes-mine.json` (a recipe an Ollama server standing in for the
instance's model wrote, and one made by swapping an ingredient) from API 0.108.0, captured on
2026-10-26 from a local build of diet-app's `dev` at `b84affc`;
`profile-calories-preview-minor-losing.json`,
`profile-calories-preview-minor-losing-acknowledged.json` and
`profile-calories-preview-minor-keeping.json` (a girl of 16 losing weight, the same with the risk
acknowledged, and keeping her weight) from the same build, captured on 2026-10-27;
`notifications-unread.json`, `notification-unknown.json`, `notifications-read-all.json` and
`notifications-none.json` from API 0.108.0, captured on 2026-10-27 from a local build of
diet-app's `dev` at `a1c7df4`: the worker's first pass reminded two profiles of their weights,
one with none logged and one reminded daily whose last weight was logged three days before, and
the catalogue update asked the first to review its preferences, which favoured an ingredient no
catalogue has; `meal-plans-of-an-unknown-profile.json`, `shopping-lists-of-an-unknown-plan.json` and
`shopping-list-unknown.json` (each asked by an id the account does not have) from API 0.127.0,
captured on 2026-10-28 from a local build of diet-app's API at `a1c7df4`, whose code the candidate
`13beb18` left as it was; the others from API 0.19.0, captured on 2026-10-13. The accounts they were captured with were test accounts, deleted afterwards. Tokens, the
sign-in challenge and the enrolment of a second step are replaced by `<removed>`. The files are
refreshed from a running backend and never edited by hand: a test that needs another answer gets it
captured.
