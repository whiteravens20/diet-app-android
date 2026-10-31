# Security — Diet App Android Companion

## Reporting a vulnerability

Report vulnerabilities privately through GitHub's [private vulnerability reporting](https://github.com/whiteravens20/diet-app-android/security/advisories/new). Please do not open a public issue, discussion or pull request for a security bug.

Include the version or commit you tested, the steps that reproduce the problem and the impact you expect. You will get a first reply within a week. A confirmed issue is fixed on the `dev` branch, and the advisory credits you unless you ask otherwise.

A vulnerability of the server, its API or its web application belongs to [diet-app](https://github.com/whiteravens20/diet-app/security/advisories/new).

## Supported versions

The app has no release yet and is not production ready. Until the first release, security fixes land on the `dev` branch only. Diet App Edge, the build the testers try, is replaced by the next edge build, which carries the fixes made by then.

## Security checklist

What the code guarantees today, and what it deliberately does not protect against. The app holds a session with the user's own Diet App server and copies of the account's data: meal plans, shopping lists, days of the diary, the profiles' names and weights, and the changes made on the phone that wait to be sent.

### Session and tokens

- [x] The access and refresh tokens are kept in DataStore sealed with AES-GCM under a key that the Android Keystore holds and never lets out, so the file holds none of them. A key that is gone, after a restore or a reset of the keystore, opens nothing, and the user signs in again
- [x] Only a 401 that says the access token is no good is answered with a refresh; any other refusal is the request's own answer. Only a refusal of the refresh itself ends the session
- [x] A session that ends takes with it everything the phone keeps of it: the tokens, every copy in the cache, the changes waiting to be sent and what the phone's notifications showed
- [x] Signing out revokes the refresh token on the server. A sign-out that cannot reach the server keeps the token, sealed as the session's were, and revokes it when the app next starts
- [x] A page of the web application opens signed in through a one-time code. The code goes in the address's fragment, which a browser does not send to a server, to the phone's default browser alone, and the page asks whether the account it names is yours before it signs in

### Transport

- [x] A release build talks HTTPS only: an address typed with `http://` is refused, and the network security configuration allows no plain text. It trusts the system's certificate authorities and those the user installed on the phone, since a self-hosted server often has a certificate from its owner's own authority
- [x] A debug build also talks plain `http`, for a backend on the developer's own network
- [x] An address with a user name or a password in it is refused: it would be kept in plain storage and shown on the screen
- [x] A build handed out asks GitHub for the file that names its newest version: a plain request on a client of its own, with none of the session's tokens and none of the app's headers, so it carries nothing of the account. It asks on every start and return, at most every six hours, until turned off in the account's settings; a debug build asks nowhere
- [x] A newer build is downloaded only from the channel's own addresses, the edge page or the repository's Releases, whatever the version file names. It is checked against the size and SHA-256 the version file gives, then against the app's own package name and signing certificate, and deleted when it fails either; what passes goes to Android's installer, which always asks the user before it installs
- [x] On Android 17 and later the app asks for the permission for nearby devices before it reaches a server on the phone's local network, which the system does not otherwise let it reach

### Data on the phone

- [x] The Room cache holds copies of meal plans, shopping lists and the days of the diary read in the last fourteen days, of each profile its name, diet, number of meals and weights, and the changes made on the phone that wait to be sent, and no credentials
- [x] Nothing the app keeps goes into a backup or onto another device: the manifest allows no backup, and the data extraction rules of Android 12 and later exclude every file from both the cloud backup and a device transfer
- [x] The phone's notifications are off until the user turns them on, when Android 13 and later ask for the permission to show them. The background work reads the account's unread notifications over the same session as the screens, and the app keeps whether they are on and the keys of those it showed
- [x] A key typed for one of the account's own AI providers is shown as dots, held in memory while its form is open and kept out of the screen's saved state, which the system may write to disk. It goes to the server alone, which tries it against the provider when a test is asked for and seals it when it is saved; the server never sends it back, and the app keeps none of it
- [x] The key and the recovery codes of two-step sign-in are put on the clipboard marked sensitive, so that the system's preview of what was copied does not show them
- [x] Where a server asks for Cloudflare Turnstile, its script runs in a WebView that reaches no file and no content of the phone and has no bridge into the app: the answer comes back as a navigation to an address of the app's own scheme, which the app reads and never loads
- [x] A release build exports one activity, the launcher's, and answers no link from other apps

### Supply chain

- [x] Every dependency and plugin the build downloads is checked against the signing keys and checksums in `gradle/verification-metadata.xml`, the Gradle distribution against its checksum, and only the versions in the lockfiles resolve ([CONTRIBUTING.md](CONTRIBUTING.md#dependencies))
- [x] Trivy fails a run on a high or critical vulnerability in a version the release build ships; on a pull request, a dependency review refuses a library the release build would ship with such a vulnerability or under a licence that does not combine with AGPL-3.0
- [x] CodeQL reads the code on every push and pull request, and every action a workflow uses is pinned to a commit, checked against its version's tag and looked up in GitHub's advisory database
- [x] No keystore or signing configuration is kept in the repository
- [x] An edge build is signed with a key kept as a secret of the `edge` environment, which only `main` can use, and is published with its SHA-256, its signer and a provenance attestation of the workflow run that built it ([edge-apk.yml](.github/workflows/edge-apk.yml))

### What this does not protect against

| Threat vector | Why it is out of scope |
| --- | --- |
| A rooted or compromised phone | Code that runs as the app can use the Keystore's key, and the cache's database is protected by the app's private storage alone |
| A certificate authority installed on the phone | A release build trusts the authorities the user installed, so whoever controls one, such as a device management profile, can read the traffic |
| The server and its operator | The server holds the account and computes everything the app shows; a compromised server sees what the app sends and can show anything |
| The lock screen | The app sets no visibility of its own for its notifications: the phone's settings decide whether their text shows on the lock screen |
| The web application in the browser | The pages the app opens are the web application's, protected by it and by the browser |
| Whoever controls the channel of new versions | They can hold new versions back, or name a build that is not newer; a file that is not this app signed with its key is refused by the app, and Android refuses an older version or another key |
| A build from somebody else | There is no release yet. An edge APK can be checked against the SHA-256 and the signer on its page and with `gh attestation verify`; a debug APK from the "Build debug APK" workflow is signed with a key made for that run and cannot be checked as this project's |
