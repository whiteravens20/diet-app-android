#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# On a fresh emulator Google Play services restarts its processes once, in the system's first
# three minutes, to take the configuration it fetched, and Android ends with them every app process
# that holds one of their providers at that moment: the app's was ended so in the middle of a test,
# holding their provider of fonts. Waits for that restart before the tests start, or until the
# system has run for 170 seconds without one.
set -euo pipefail

restarted='am_proc_died: \[[0-9]+,[0-9]+,com\.google\.android\.gms\.persistent,'
for _ in $(seq 1 100); do
  events="$(adb logcat -b events -d 2> /dev/null || true)"
  if grep -qE "$restarted" <<< "$events"; then
    echo "Google Play services has restarted; the tests start."
    exit 0
  fi
  uptime="$(adb shell cut -d. -f1 /proc/uptime 2> /dev/null | tr -d '\r' || true)"
  if [[ "$uptime" =~ ^[0-9]+$ ]] && [ "$uptime" -ge 170 ]; then
    echo "Google Play services has not restarted in the system's first 170 seconds; the tests start."
    exit 0
  fi
  sleep 3
done
echo "Google Play services' restart was not seen; the tests start."
