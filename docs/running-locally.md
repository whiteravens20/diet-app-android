# Running Locally

How to build the app, point it at a backend, and run it on a device.

## Prerequisites

- **JDK 17+** (the build targets a JDK 17 toolchain; 21 is fine to run Gradle with).
- **Android SDK** with `platform-tools`, `platforms;android-37`, `build-tools;36.0.0`.
  Point Gradle at it via `local.properties` (`sdk.dir=/path/to/Android/Sdk`) or the
  `ANDROID_HOME` env var. `local.properties` is gitignored.
- The committed **Gradle wrapper** (`./gradlew`) pins the Gradle version — no separate
  install needed.

## 1. Run the backend

The app is a client; it needs a running Diet App platform instance. From the
[platform repo](https://github.com/whiteravens20/diet-app):

```bash
docker compose -f infra/docker-compose.dev.yml up -d   # postgres
npm run dev                                             # API on :4000
```

## 2. Build the APK

```bash
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
```

The backend base URL is baked in as a build config value, defaulting to
`http://10.0.2.2:4000/api/` (an emulator's alias for the host loopback). For a
**physical device** the phone must reach the backend over the network, so pass your
host's LAN IP:

```bash
./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.20:4000/api/
```

(Or set `apiBaseUrl` in `~/.gradle/gradle.properties` to avoid repeating the flag.)
The debug build allows cleartext HTTP so a plain `http://…:4000` backend works during
development; release builds do not.

The baked-in value is only the default: the server address is editable **in the app**
— the **Server** button on the login screen, or Profile → Server once signed in. The
value persists on the device; changing it while signed in signs you out, since tokens
and the offline cache belong to the old instance.

## 3. Install on a device

```bash
adb devices                    # confirm the device is listed & authorised
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable **USB debugging** on the phone (Developer options). Make sure the phone and the
backend host are on the same network and any host firewall allows port 4000.

## On-demand cloud build

No local toolchain? Trigger the **Build APK** workflow
(`.github/workflows/build-apk.yml`, `workflow_dispatch`) from the Actions tab and
download the `diet-app-debug-apk` artifact.

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
   the LAN, set that machine's address in-app (login screen → **Server**).

Command-line equivalent (PowerShell, from the repo root):

```powershell
.\gradlew.bat assembleDebug          # -> app\build\outputs\apk\debug\app-debug.apk
```

## Just build the APK (no local toolchain at all)

Trigger the **Build APK** workflow (Actions tab → *Build APK* → *Run workflow*) and
download the `diet-app-debug-apk` artifact — a ready-to-install debug APK. Install it
on a phone with `adb install -r app-debug.apk` (or copy it over and open it), then
point it at your backend from the login screen's **Server** button.
