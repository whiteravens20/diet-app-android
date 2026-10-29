#!/usr/bin/env bash
# Writes the GitHub Pages site of an edge build into <directory>: the APK under a name that stays the
# same from build to build, its SHA-256 in the form `sha256sum -c` reads, and a page that says what
# the build is and how to check it. Used by .github/workflows/edge-apk.yml.
#
# Usage: .github/scripts/edge-page.sh <apk> <directory> <version name> <commit> <signer's SHA-256>
set -euo pipefail

apk=$1
site=$2
version=$3
commit=$4
signer=$5

mkdir -p "$site"
cp "$apk" "$site/diet-app-edge.apk"
(cd "$site" && sha256sum diet-app-edge.apk > diet-app-edge.apk.sha256)
sha256=$(cut -d' ' -f1 "$site/diet-app-edge.apk.sha256")
size=$(du -m "$site/diet-app-edge.apk" | cut -f1)
api=$(tr -d '[:space:]' < contract/API_VERSION)
built=$(date -u +%Y-%m-%d)
repo=https://github.com/whiteravens20/diet-app-android

cat > "$site/index.html" <<HTML
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Diet App Edge</title>
<style>
:root { --bg: #fbfcf9; --fg: #17201b; --muted: #55665c; --line: #dce5de; --accent: #1c7a54; --on-accent: #ffffff; }
@media (prefers-color-scheme: dark) { :root { --bg: #0c140f; --fg: #e3ebe6; --muted: #9db0a5; --line: #26332b; --accent: #5cc493; --on-accent: #08130d; color-scheme: dark; } }
body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.55 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
main { max-width: 640px; margin: 0 auto; padding: 32px 20px 48px; display: grid; gap: 22px; }
h1 { margin: 0; font-size: 28px; line-height: 1.2; }
h2 { margin: 0 0 6px; font-size: 18px; }
p { margin: 0; }
.muted { color: var(--muted); }
.download { display: block; text-align: center; padding: 16px; border-radius: 12px; background: var(--accent); color: var(--on-accent); font-weight: 600; font-size: 18px; text-decoration: none; }
.download:focus-visible, a:focus-visible { outline: 3px solid var(--fg); outline-offset: 3px; }
a { color: var(--accent); }
dl { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 6px 14px; margin: 0; }
dt { color: var(--muted); }
dd { margin: 0; overflow-wrap: anywhere; }
code { font: 13px/1.5 ui-monospace, "SFMono-Regular", Menlo, Consolas, monospace; overflow-wrap: anywhere; }
section { display: grid; gap: 8px; border-top: 1px solid var(--line); padding-top: 18px; }
</style>
</head>
<body>
<main>
  <header>
    <h1>Diet App Edge</h1>
    <p class="muted">A test build of the <a href="$repo">Diet App Android companion</a>, for the testers' round before the first release. It changes during the tests, which is expected.</p>
  </header>

  <a class="download" href="diet-app-edge.apk">Download the APK (${size} MB)</a>

  <dl>
    <dt>Version</dt><dd><code>$version</code></dd>
    <dt>Built</dt><dd>$built, from <a href="$repo/commit/$commit"><code>${commit:0:7}</code></a> of <code>main</code></dd>
    <dt>Checked against</dt><dd>diet-app $api</dd>
    <dt>SHA-256</dt><dd><code>$sha256</code></dd>
    <dt>Signer</dt><dd><code>$signer</code></dd>
  </dl>

  <section lang="en">
    <h2>What it is</h2>
    <p>Diet App Edge is an application of its own: it installs beside a later release of Diet App, and each edge build installs over the one before it. It is the release build, which talks to a server over HTTPS only; on its first screen it asks for the address of your Diet App server.</p>
    <p>To check a download: <code>sha256sum -c diet-app-edge.apk.sha256</code>, or with the GitHub CLI <code>gh attestation verify diet-app-edge.apk --repo whiteravens20/diet-app-android</code>, which shows the workflow run that built it.</p>
  </section>

  <section lang="pl">
    <h2>Po polsku</h2>
    <p>Diet App Edge to wersja testowa aplikacji na Androida, do rundy testów przed pierwszym wydaniem. W trakcie testów aplikacja się zmienia i tak ma być. Instaluje się obok przyszłego wydania jako osobna aplikacja, a każda kolejna wersja edge instaluje się na poprzedniej. Przy pierwszym uruchomieniu pyta o adres serwera Diet App.</p>
  </section>
</main>
</body>
</html>
HTML
echo "edge page written: $version, ${size} MB"
