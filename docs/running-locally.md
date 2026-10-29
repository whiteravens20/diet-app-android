# Running Locally

How to build the app, point it at a backend, and run it on a device.

## Prerequisites

- **JDK 17 or newer.** The code is compiled for Java 17 by whichever JDK runs Gradle; no JDK is downloaded.
- **Android SDK** with `platform-tools`, `platforms;android-37`, `build-tools;36.0.0`.
  Point Gradle at it via `local.properties` (`sdk.dir=/path/to/Android/Sdk`) or the
  `ANDROID_HOME` env var. `local.properties` is gitignored.
- The committed **Gradle wrapper** (`./gradlew`) pins the Gradle version — no separate
  install needed.
- Every dependency Gradle downloads is checked against `gradle/verification-metadata.xml`.
  A build that stops on a failed dependency verification after a dependency was changed
  needs the metadata written again ([CONTRIBUTING.md](../CONTRIBUTING.md#dependencies)).

## 1. Run the backend

The app is a client: it needs a running Diet App instance with its catalogue loaded, since
without a catalogue no plan can be made.

**For an emulator**, the quickest is the instance the screens' tests use. On a machine with
Docker, `curl`, `jq` and `openssl`:

```bash
.github/scripts/start-diet-app.sh /tmp/diet-app-instance   # an empty directory of its own
```

It starts the API and the worker of the diet-app version that `contract/diet-app.env` names,
the version this app is checked against, publishes the API on `127.0.0.1:4000`, which the
emulator reaches as `10.0.2.2:4000`, and loads the catalogue. It also makes the two accounts
the notifications' tests read and prints the Gradle arguments that name them. In that
directory, `docker compose --env-file .env -f docker-compose.yml -f api-port.yml down`
stops it.

**For a phone on your network**, or for another version of the backend, run diet-app from
its own checkout as its README says: [Install](https://github.com/whiteravens20/diet-app#install),
[Run](https://github.com/whiteravens20/diet-app#run) (with Docker, or with Node for the
local loop), and [load the catalogue](https://github.com/whiteravens20/diet-app#first-run-populate-the-curated-database).
Its API listens on port 4000 of every address of the machine.

## 2. Build the APK

```bash
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

The backend base URL is baked in as a build config value: in a debug build it defaults to
`http://10.0.2.2:4000/api/` (an emulator's alias for the host loopback), and a release build
has none, so its first screen asks for the address. For a
**physical device** the phone must reach the backend over the network, so pass your
host's LAN IP:

```bash
./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.20:4000/api/
```

(Or set `apiBaseUrl` in `~/.gradle/gradle.properties` to avoid repeating the flag.)
The debug build allows cleartext HTTP so a plain `http://…:4000` backend works during
development; release builds do not.

The baked-in value is only the default: the server address is editable **in the app**
— the **Server:** button under the sign-in form, which shows the address, or More →
Account settings → Server once signed in. The app asks `GET /api/health` at the address
before it keeps it, and names what answers there when it is not a Diet App API. The value
persists on the device; changing it while signed in signs you out, since tokens and the
offline cache belong to the old instance.

## 3. Install on a device

```bash
adb devices                    # confirm the device is listed & authorised
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable **USB debugging** on the phone (Developer options). Make sure the phone and the
backend host are on the same network and any host firewall allows port 4000.

## About the Android emulator

The Android emulator is QEMU-based and needs **hardware virtualization** to be usable:
`/dev/kvm` on Linux (Intel VT-x / AMD-V), HAXM/Hypervisor.Framework on macOS, or WHPX on
Windows. Check on Linux with:

```bash
ls /dev/kvm && grep -oE 'vmx|svm' /proc/cpuinfo | sort -u
```

If `/dev/kvm` is absent and the CPU exposes no `vmx`/`svm` flags — typical inside a
**nested VM** that doesn't pass through virtualization — the emulator can only fall back
to full software emulation (`-no-accel`), which is far too slow to boot a modern system
image in practice. On such a host, **use a physical device** (steps above) or a cloud
device farm (e.g. Firebase Test Lab). Enabling nested virtualization on the hypervisor,
or building on bare metal, restores emulator support.

## Emulating on a Windows machine

Any bare-metal Windows 10/11 PC with virtualization enabled in the BIOS/UEFI can run
the emulator:

1. Install [Android Studio](https://developer.android.com/studio) (bundles the SDK,
   a JDK and the emulator).
2. Make sure virtualization is on: Task Manager → Performance → CPU →
   *Virtualization: Enabled*. If it's off, enable Intel VT-x / AMD-V in the BIOS.
   Android Studio uses **WHPX** (Windows Hypervisor Platform) or AEHD automatically;
   enable "Windows Hypervisor Platform" under *Turn Windows features on or off* if
   the emulator complains.
3. Clone the repo and open it in Android Studio — the Gradle sync uses the committed
   wrapper; no other setup.
4. Device Manager → **Create virtual device** → any Pixel profile → a recent system
   image (API 34+) → finish, then press ▶ to run the `app` configuration on it.
5. Backend reachability from the emulator: `http://10.0.2.2:4000/api/` (the default)
   reaches the Windows host's `localhost`. If the backend runs on another machine on
   the LAN, set that machine's address in the app, with the **Server:** button under the
   sign-in form.

Command-line equivalent (PowerShell, from the repo root):

```powershell
.\gradlew.bat assembleDebug          # -> app\build\outputs\apk\debug\app-debug.apk
```

## Without a local toolchain

Trigger the **Build debug APK** workflow (Actions tab → *Build debug APK* → *Run workflow*,
`.github/workflows/build-apk.yml`) and download the `diet-app-debug-apk` artifact, a debug
APK ready to install. Install it on a phone with `adb install -r app-debug.apk` (or copy it
over and open it), then point it at your backend from the **Server:** button under the
sign-in form. Each run signs the APK with a debug key of its own, so a copy from another run
does not install over it: uninstall the app first.
