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
