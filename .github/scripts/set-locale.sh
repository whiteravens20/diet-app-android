#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Puts the emulator in the language $1 (such as pl-PL), so that the app and its journeys run in it.
# The system reads its language only as it starts: the property is set while adb runs as root, and
# the emulator is started again and waited for. Nothing to do without a language.
set -euo pipefail

language=${1:-}
[ -n "$language" ] || exit 0

# adb as root, asked again until it is: right after a boot adbd can close the first request
# ("unable to connect for root: closed") while it restarts as root.
as_root() {
  for _ in $(seq 1 20); do
    adb root > /dev/null 2>&1 || true
    adb wait-for-device
    if [ "$(adb shell id -u 2> /dev/null | tr -d '\r')" = 0 ]; then
      return 0
    fi
    sleep 3
  done
  echo "::error::adb did not run as root on the emulator"
  return 1
}

as_root
adb shell setprop persist.sys.locale "$language"
adb reboot
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
as_root
echo "The emulator speaks $(adb shell getprop persist.sys.locale | tr -d '\r')."
