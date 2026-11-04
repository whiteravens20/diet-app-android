#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# The emulator starts only with 1.2 times its data partition free on the disk of its AVD (7.2 GB
# for a 6 GB partition), and some runners have less than that left once the instance and the app
# are built. Takes off the runner the toolchains these jobs never use: .NET, Haskell, Swift, Julia
# and CodeQL's bundle.
set -euo pipefail
df -h /
for dir in /usr/share/dotnet /usr/local/.ghcup /opt/ghc /usr/share/swift /opt/hostedtoolcache/CodeQL /usr/local/julia*; do
  if [ -e "$dir" ]; then
    sudo rm -rf "$dir"
  fi
done
df -h /
