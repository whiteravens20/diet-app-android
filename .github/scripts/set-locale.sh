#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Puts the emulator in the language $1 (such as pl-PL), so that the app and its journeys run in it.
# The system reads its language only as it starts: the property is set while adb runs as root, and
# the emulator is started again and waited for. Nothing to do without a language.
set -euo pipefail

language=${1:-}
[ -n "$language" ] || exit 0
adb root > /dev/null
sleep 3
adb shell setprop persist.sys.locale "$language"
adb reboot
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
adb root > /dev/null
sleep 3
echo "The emulator speaks $(adb shell getprop persist.sys.locale | tr -d '\r')."
