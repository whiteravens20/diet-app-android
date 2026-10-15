#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Waits until the emulator's settings service answers, which on a fresh Android 17 image comes a
# while after the boot is reported done, and turns the system's animations off for the tests.
set -euo pipefail

for _ in $(seq 1 60); do
  if adb shell settings get global window_animation_scale > /dev/null 2>&1; then
    for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
      adb shell settings put global "$scale" 0
    done
    echo "The emulator's settings answer; animations are off."
    exit 0
  fi
  sleep 5
done
echo "::error::The emulator's settings service did not answer"
exit 1
