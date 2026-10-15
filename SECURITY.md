# Security Policy

## Reporting a Vulnerability

Report security issues **privately** via
[GitHub Security Advisories](https://github.com/whiteravens20/diet-app-android/security/advisories/new),
or email the maintainer (`@pavlojs` on GitHub). Do not open a public issue.

## Security model

The companion app holds an authenticated session and cached personal data.

- **Tokens** — the access and refresh tokens are kept in `DataStore` sealed with AES-GCM
  under a key the Android Keystore holds and never lets out, so the file holds none of
  them. A key that is gone, after a restore or a reset of the keystore, opens nothing, and
  the user signs in again.
- **Transport** — a release build talks HTTPS only, and trusts the system's certificate
  authorities and those the user installed on the phone, since a self-hosted server often
  has a certificate from its owner's own authority. A debug build also talks plain `http`,
  for a backend on the developer's own network. On Android 17 and later the app asks for
  the permission for nearby devices before it sends anything to a server on the local
  network, which the system otherwise does not let it reach.
- **Cache** — the Room cache holds meal plans and shopping lists, not credentials.
  It is cleared on sign-out.
- **Anti-abuse check** — where a server asks for Cloudflare Turnstile, its script runs in
  a WebView that reaches no file and no content of the phone and has no bridge into the
  app: the answer comes back as a navigation to an address of the app's own scheme, which
  the app reads and never loads.
- **Backups** — nothing the app keeps goes into a backup or onto another device: the
  manifest allows no backup, and the data extraction rules of Android 12 and later
  exclude every file from both the cloud backup and a device transfer.
- **No secrets in the repo** — keystores and signing config are git-ignored.
- **Build** — every dependency and plugin is checked against the signing keys and
  checksums in `gradle/verification-metadata.xml`, and the versions are locked
  ([CONTRIBUTING.md](CONTRIBUTING.md#dependencies)).

The backend remains the security authority; the app never performs nutrition
calculations itself — it renders what the API returns.
