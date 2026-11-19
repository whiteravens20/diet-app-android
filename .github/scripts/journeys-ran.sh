#!/usr/bin/env bash
# An emulator lost along the way can leave Gradle green with part of the journeys run:
# what the reports counted is held against what is written.
set -euo pipefail

written=$(grep -rhE '^\s*@Test\b' app/src/androidTest/java | wc -l)
# The reports of one run, or of the parts a run was split into: those of the directories named, or
# of the job's own build, whichever of them are there.
if [ "$#" -eq 0 ]; then
  set -- app/build/outputs/androidTest-results/connected app/build/screens-parts
fi
dirs=()
for dir in "$@"; do
  if [ -d "$dir" ]; then dirs+=("$dir"); fi
done
ran=0
if [ "${#dirs[@]}" -gt 0 ]; then
  ran=$(find "${dirs[@]}" -name 'TEST-*.xml' -print0 |
    xargs -0 -r grep -ho '<testsuite [^>]*' |
    sed -E 's/.* tests="([0-9]+)".*/\1/' |
    awk '{ sum += $1 } END { print sum + 0 }')
fi

echo "Written: $written. Ran: $ran."
if [ "$ran" -ne "$written" ]; then
  echo "::error::$written journeys are written and $ran ran."
  exit 1
fi
