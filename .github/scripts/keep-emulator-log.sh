#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Writes what the emulator logs, its crashes and the system's own messages included, to
# app/build/emulator-logcat.txt until the emulator stops, for the artifact of the run. adbd
# starting again ends the log's connection: run again then, it goes on from the last line written.
set -euo pipefail

log=app/build/emulator-logcat.txt
mkdir -p app/build
since=()
if [ -s "$log" ]; then
  last="$(grep -E '^[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}' "$log" | tail -n 1 | cut -c1-18 || true)"
  if [ -n "$last" ]; then since=(-T "$last"); else since=(-T 1); fi
fi
nohup adb logcat "${since[@]}" -v threadtime -b main,system,crash,events >> "$log" 2>&1 &
echo "The emulator's log goes to $log."
