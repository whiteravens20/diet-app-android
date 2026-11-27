#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Puts the test authority's certificate on the running emulator as one the user installed, in
# the store an app's network security configuration reads for `src="user"`. Needs an emulator
# image with root (google_apis). Does nothing unless TEST_AUTHORITY names the directory
# serve-with-test-authority.sh wrote.
set -euo pipefail

[ -n "${TEST_AUTHORITY:-}" ] || exit 0

name="$(openssl x509 -subject_hash_old -noout -in "$TEST_AUTHORITY/authority.pem").0"
store=/data/misc/user/0/cacerts-added

# adbd answers `root` only once it is up, which a freshly booted emulator does not promise: it is
# asked again a few times before the run gives up.
for attempt in $(seq 1 10); do
  adb root > /dev/null 2>&1 && break
  if [ "$attempt" = 10 ]; then
    echo "::error::adbd did not restart as root"
    exit 1
  fi
  sleep 3
  timeout 60 adb wait-for-device || true
done
if ! timeout 120 adb wait-for-device; then
  echo "::error::The emulator was not back within two minutes of adbd starting again as root"
  exit 1
fi
adb shell mkdir -p "$store"
adb push "$TEST_AUTHORITY/authority.pem" "$store/$name" > /dev/null
adb shell chown -R system:system "$store"
adb shell chmod 755 "$store"
adb shell chmod 644 "$store/$name"
adb shell restorecon -R "$store"
echo "The test authority is on the emulator as one the user installed: $store/$name"

# adbd started again as root: the log is taken up again, and the tests start once the system
# says it has booted and its package manager answers.
if ! timeout 120 adb wait-for-device; then
  echo "::error::The emulator was not back within two minutes of adbd starting again as root"
  exit 1
fi
"$(dirname "$0")/keep-emulator-log.sh"
for _ in $(seq 1 60); do
  if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && adb shell pm path android > /dev/null 2>&1; then
    exit 0
  fi
  sleep 5
done
echo "::error::The emulator did not come back after adbd started as root"
exit 1
