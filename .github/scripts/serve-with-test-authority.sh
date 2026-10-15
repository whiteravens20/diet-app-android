#!/usr/bin/env bash
# Copyright (C) 2026 White Ravens. AGPL-3.0-only with an additional term; see LICENSE and NOTICE.
#
# Serves the test instance (http://localhost:4000) over HTTPS on port 4443 as well, with a
# certificate from an authority made here for the run, as a self-hosted server often has one
# from its owner's own authority. The authority's certificate is left in <dir>/authority.pem,
# for install-test-authority.sh to put on the emulator as one the user installed.
#
#   serve-with-test-authority.sh <dir>
set -euo pipefail

dir="$1"
mkdir -p "$dir"
cd "$dir"

openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=Diet App screens test authority" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign" \
  -keyout authority.key -out authority.pem 2>/dev/null
openssl req -newkey rsa:2048 -nodes -subj "/CN=10.0.2.2" -keyout server.key -out server.csr 2>/dev/null
printf 'subjectAltName=IP:10.0.2.2,DNS:localhost\nextendedKeyUsage=serverAuth\n' > server.ext
openssl x509 -req -in server.csr -CA authority.pem -CAkey authority.key -CAcreateserial -days 2 \
  -extfile server.ext -out server.pem 2>/dev/null

nohup python3 "$GITHUB_WORKSPACE/.github/scripts/tls-proxy.py" 4443 server.pem server.key 127.0.0.1 4000 \
  > tls-proxy.log 2>&1 &

for _ in $(seq 1 30); do
  if curl -fsS --cacert authority.pem https://localhost:4443/api/health > /dev/null; then
    echo "The test instance answers over HTTPS on port 4443, with a certificate of the test authority."
    exit 0
  fi
  sleep 1
done
echo "::error::The HTTPS proxy did not answer"
cat tls-proxy.log
exit 1
