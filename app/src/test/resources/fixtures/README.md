# Answers of a running backend

Each file is what a running diet-app answered one call of this client: `config.json` from API
0.77.0, captured on 2026-10-16 from a fresh install; `weights.json`, `weight-logged.json`,
`weight-entry-unknown.json` and `users-me-in-pounds.json` from API 0.19.0, captured on
2026-10-16; `account-sessions.json`, `account-session-unknown.json`,
`users-me-renamed-in-polish.json`, `password-change-wrong-password.json`,
`email-change-taken.json`, `second-factor-off.json`, `second-factor-setup.json`,
`second-factor-code-wrong.json`, `second-factor-on.json`, `second-factor-state-on.json`,
`second-factor-new-codes.json` and `account-export.json` (the file as the API writes it) from
API 0.19.0, captured on 2026-10-17; the others from API 0.19.0, captured on 2026-10-13. The
accounts they were captured with were test accounts, deleted afterwards. Tokens, the sign-in
challenge and the enrolment of a second step are replaced by `<removed>`. The files are
refreshed from a running backend and never edited by hand: a test that needs another answer gets
it captured.
