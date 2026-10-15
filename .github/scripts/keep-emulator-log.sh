#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Writes what the emulator logs, its crashes and the system's own messages included, to
# app/build/emulator-logcat.txt from now until the emulator stops, for the artifact of the run.
set -euo pipefail

mkdir -p app/build
nohup adb logcat -v threadtime -b main,system,crash,events > app/build/emulator-logcat.txt 2>&1 &
echo "The emulator's log goes to app/build/emulator-logcat.txt."
