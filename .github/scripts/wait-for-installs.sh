#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Waits until the emulator installs an app, by installing the one given until it goes in, and
# takes it off again for the tests to install. A fresh Android 17 image says it has booted while
# its package manager still fails an install.
#
#   wait-for-installs.sh <apk> <package>
set -euo pipefail

apk="$1"
package="$2"

for _ in $(seq 1 30); do
  if adb install -r -t "$apk" > /dev/null 2>&1; then
    adb uninstall "$package" > /dev/null
    echo "The emulator installs apps."
    exit 0
  fi
  sleep 10
done
echo "::error::The emulator did not install the app in five minutes"
exit 1
