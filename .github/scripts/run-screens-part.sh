#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# One part of the screens' tests ($PART of 3) on the emulator the workflow's action booted: the emulator made
# ready, the part's journeys run on the build $BUILD_TYPE (debug by default), and the emulator stopped here,
# however the part ends. The action stops it too, with `adb emu kill`, and its step lasts until the emulator's
# process ends: an emulator that answered "bye bye" and went on running held the step until the job's limit, which
# ended the steps that keep the part's results and logs as well. Here it is asked, given half a minute, and ended
# if it is still there; the action's own stop then finds no emulator and passes on.
set -euo pipefail

build_type=${BUILD_TYPE:-debug}
variant=${VARIANT:-Debug}

stop_emulator() {
  adb emu kill > /dev/null 2>&1 || true
  for _ in $(seq 1 15); do
    pgrep -f 'qemu-syste[m]' > /dev/null || return 0
    sleep 2
  done
  echo "::warning::The emulator went on running after it was asked to stop: it is ended."
  pkill -9 -f 'qemu-syste[m]' || true
}
trap stop_emulator EXIT

.github/scripts/set-locale.sh "${LOCALE:-}"
.github/scripts/keep-emulator-log.sh
.github/scripts/settle-emulator.sh
.github/scripts/install-test-authority.sh
.github/scripts/wait-for-installs.sh "app/build/outputs/apk/$build_type/app-$build_type.apk" net.whiteravens.dietapp
.github/scripts/wait-for-play-services.sh
free -m
# The tests stop after 20 minutes (a part's take 7 to 15), so that a part that hangs ends within the job's limit.
# shellcheck disable=SC2086 # each holds Gradle's arguments, one or more, or nothing.
timeout -k 1m 20m ./gradlew -PtestBuildType="$build_type" ${AUTHORITY_ARGUMENT:-} ${NOTIFIED_ACCOUNT:-} \
  -Pandroid.testInstrumentationRunnerArguments.numShards=3 \
  -Pandroid.testInstrumentationRunnerArguments.shardIndex=$((PART - 1)) "connected${variant}AndroidTest"
