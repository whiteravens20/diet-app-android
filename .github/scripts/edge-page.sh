#!/usr/bin/env bash
# Writes the GitHub Pages site of an edge build into <directory>: the APK under a name that stays the
# same from build to build, its SHA-256 in the form `sha256sum -c` reads, and a page that says what
# the build is and how to check it. Used by .github/workflows/edge-apk.yml.
#
# Usage: .github/scripts/edge-page.sh <apk> <directory> <version name> <commit> <signer's SHA-256> [preset]
# `preset` when the build starts with the testers' server set (EDGE_SERVER in the workflow).
set -euo pipefail

apk=$1
site=$2
version=$3
commit=$4
signer=$5
if [ "${6:-}" = preset ]; then
  server_en="It starts with the testers' server set; the <em>Server:</em> button under the sign-in form changes it."
  server_pl='Ma od razu ustawiony serwer testów, a przycisk „Serwer:” pod formularzem logowania pozwala go zmienić.'
else
  server_en="On its first screen it asks for the address of your Diet App server."
  server_pl="Przy pierwszym uruchomieniu pyta o adres serwera Diet App."
fi

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
ol { margin: 0; padding-left: 22px; display: grid; gap: 8px; }
</style>
</head>
<body>
<main>
  <header>
    <h1>Diet App Edge</h1>
    <p class="muted">A test build of the <a href="$repo">Diet App Android companion</a>, for the testers' round before the first release. It changes during the tests, which is expected.</p>
    <p lang="pl"><a href="#instalacja">Instrukcja instalacji po polsku</a></p>
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
    <h2>How to install (Android 16 and later)</h2>
    <ol>
      <li>Open this page on the phone and tap <em>Download the APK</em>. If the browser warns about the type of file, choose to download it anyway.</li>
      <li>Open the downloaded file, from the download notification or from the Downloads of the phone's file app.</li>
      <li>If Android says that for your security your phone isn't allowed to install unknown apps from this source, tap <em>Settings</em>, turn on <em>Allow from this source</em> and go back. The permission is for that one app (the browser or the file app) and can be turned off afterwards.</li>
      <li>On a Samsung phone, Auto Blocker may stop the install: turn it off in Settings &rarr; Security and privacy &rarr; Auto Blocker. While it is on, it stops every later edge build as well.</li>
      <li>Tap <em>Install</em>. If Google Play Protect offers to scan the app, you can let it: it sends the app's details to Google and shows the result.</li>
      <li>The app is called Diet App Edge. A later edge build installs the same way, over this one: Android asks whether you want to update the app.</li>
    </ol>
    <p class="muted">Menus can differ between makers and models of phones.</p>
  </section>

  <section lang="en">
    <h2>What it is</h2>
    <p>Diet App Edge is an application of its own: it installs beside a later release of Diet App, and each edge build installs over the one before it. It is the release build, which talks to a server over HTTPS only. $server_en</p>
    <p>To check a download: <code>sha256sum -c diet-app-edge.apk.sha256</code>, or with the GitHub CLI <code>gh attestation verify diet-app-edge.apk --repo whiteravens20/diet-app-android</code>, which shows the workflow run that built it.</p>
  </section>

  <section lang="pl">
    <h2>Po polsku</h2>
    <p>Diet App Edge to wersja testowa aplikacji na Androida, do rundy testów przed pierwszym wydaniem. W trakcie testów aplikacja się zmienia i tak ma być. Instaluje się obok przyszłego wydania jako osobna aplikacja, a każda kolejna wersja edge instaluje się na poprzedniej. $server_pl</p>
  </section>

  <section lang="pl" id="instalacja">
    <h2>Instalacja (Android 16 i nowsze)</h2>
    <ol>
      <li>Otwórz tę stronę na telefonie i stuknij przycisk pobierania. Jeśli przeglądarka ostrzeże przed tym typem pliku, wybierz pobranie mimo to.</li>
      <li>Otwórz pobrany plik z powiadomienia o pobraniu albo w aplikacji z plikami telefonu, w pobranych plikach (na Samsungu: Moje pliki &rarr; Pobrane).</li>
      <li>Jeśli telefon pokaże komunikat „Ze względów bezpieczeństwa na Twoim telefonie nie można obecnie instalować nieznanych aplikacji z tego źródła”, stuknij „Ustawienia”, włącz „Zezwalaj z tego źródła” i wróć. To zezwolenie dotyczy tylko tej jednej aplikacji (przeglądarki albo aplikacji z plikami) i można je potem wyłączyć.</li>
      <li>Na Samsungu instalację może zatrzymać Automatyczna blokada. Wyłącz ją w Ustawienia &rarr; Bezpieczeństwo i prywatność &rarr; Automatyczna blokada. Dopóki jest włączona, zatrzymuje też każdą kolejną wersję edge.</li>
      <li>Na pytanie „Zainstalować tę aplikację?” stuknij „Zainstaluj”. Jeśli Google Play Protect zaproponuje sprawdzenie aplikacji, można się zgodzić: wysyła dane aplikacji do Google i pokazuje wynik.</li>
      <li>Aplikacja nazywa się Diet App Edge. Kolejną wersję edge instaluje się tak samo, na tej: telefon zapyta „Zaktualizować tę aplikację?”.</li>
    </ol>
    <p class="muted">Nazwy menu mogą się różnić w zależności od producenta i modelu telefonu.</p>
  </section>
</main>
</body>
</html>
HTML
echo "edge page written: $version, ${size} MB"
