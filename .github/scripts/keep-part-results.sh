#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Moves what one part of the screens' tests left (results, reports, screenshots) to a directory of
# its own, before the next part's run of the same task would overwrite it.
set -euo pipefail
part="app/build/screens-parts/$1"
mkdir -p "$part"
for dir in outputs/androidTest-results outputs/connected_android_test_additional_output reports/androidTests; do
  if [ -d "app/build/$dir" ]; then
    mkdir -p "$part/$(dirname "$dir")"
    mv "app/build/$dir" "$part/$dir"
  fi
done
