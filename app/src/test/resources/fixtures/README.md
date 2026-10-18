# Answers of a running backend

Each file is what a running diet-app answered one call of this client: `config.json` from API
0.77.0, captured on 2026-10-16 from a fresh install; `weights.json`, `weight-logged.json`,
`weight-entry-unknown.json` and `users-me-in-pounds.json` from API 0.19.0, captured on 2026-10-16;
`account-sessions.json`, `account-session-unknown.json`, `users-me-renamed-in-polish.json`,
`password-change-wrong-password.json`, `email-change-taken.json`, `second-factor-off.json`,
`second-factor-setup.json`, `second-factor-code-wrong.json`, `second-factor-on.json`,
`second-factor-state-on.json`, `second-factor-new-codes.json`, `account-export.json` (the file as
the API writes it), `account-deletion-wrong-password.json`, `ingredients-search-egg.json`,
`ingredients-by-ids.json`, `pantry.json`, `pantry-row-added.json`, `pantry-row-changed.json`,
`pantry-quantity-too-large.json`, `pantry-unit-not-convertible.json` and `pantry-row-unknown.json`
from API 0.19.0, captured on 2026-10-17; `ai-providers.json`, `ai-providers-none.json`,
`ai-providers-key-unreadable.json` (the stored key damaged in the instance's database first),
`ai-provider-saved.json`, `ai-provider-ollama-saved.json`, `ai-provider-replaced.json`,
`ai-provider-host-not-allowed.json`, `ai-provider-unknown.json`, `ai-test-connected.json` (an Ollama
server standing in for a model), `ai-test-host-not-allowed.json`,
`ai-test-anthropic-without-a-model.json`, `ai-quota-off.json`, `ai-quota-admin.json`,
`ai-quota-byok.json`, `ai-quota-instance-used-up.json` (the instance's monthly limit lowered under
what it had used, for the one call), `users-me-ai-admin.json` and
`ai-mode-byok-without-a-provider.json` from API 0.77.0, captured on 2026-10-18 from an instance of
diet-app's `dev`, whose AI routes are those of `0fb1cef`; the others from API 0.19.0, captured on
2026-10-13. The accounts they were captured with were test accounts, deleted afterwards. Tokens, the
sign-in challenge and the enrolment of a second step are replaced by `<removed>`. The files are
refreshed from a running backend and never edited by hand: a test that needs another answer gets it
captured.
