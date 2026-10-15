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

adb root > /dev/null
adb wait-for-device
adb shell mkdir -p "$store"
adb push "$TEST_AUTHORITY/authority.pem" "$store/$name" > /dev/null
adb shell chown -R system:system "$store"
adb shell chmod 755 "$store"
adb shell chmod 644 "$store/$name"
adb shell restorecon -R "$store"
echo "The test authority is on the emulator as one the user installed: $store/$name"
