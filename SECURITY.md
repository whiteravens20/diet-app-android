# Security Policy

## Reporting a Vulnerability

Report security issues **privately** via
[GitHub Security Advisories](https://github.com/whiteravens20/diet-app-android/security/advisories/new),
or email the maintainer (`@pavlojs` on GitHub). Do not open a public issue.

## Security model

The companion app holds an authenticated session and cached personal data.

- **Tokens** — access/refresh tokens are stored in `DataStore`; on devices with a
  hardware keystore, wrap them with `EncryptedSharedPreferences` / the Android Keystore.
- **Transport** — HTTPS for any non-local backend; cleartext is allowed only for
  `10.0.2.2` / LAN development hosts.
- **Cache** — the Room cache holds meal plans and shopping lists, not credentials.
  It is cleared on sign-out.
- **Backups** — nothing the app keeps goes into a backup or onto another device: the
  manifest allows no backup, and the data extraction rules of Android 12 and later
  exclude every file from both the cloud backup and a device transfer.
- **No secrets in the repo** — keystores and signing config are git-ignored.
- **Build** — every dependency and plugin is checked against the signing keys and
  checksums in `gradle/verification-metadata.xml`, and the versions are locked
  ([CONTRIBUTING.md](CONTRIBUTING.md#dependencies)).

The backend remains the security authority; the app never performs nutrition
calculations itself — it renders what the API returns.
