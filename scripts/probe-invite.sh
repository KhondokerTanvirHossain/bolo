#!/usr/bin/env bash
# Prints the invite code the smoke test and live probes use (closed beta), creating it on the box the first time.
# Kept in ~/bolo/probe-invite on the box; it isn't secret (it only lets the checks create test guests).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/scripts/box.sh"
code="$(box 'cat ~/bolo/probe-invite 2>/dev/null' || true)"
if [[ -z "$code" ]]; then
  code="probes$(LC_ALL=C tr -dc 'a-z0-9' < /dev/urandom | head -c 12)"
  box "docker exec -i bolo-db psql -U bolo -d bolo -q -v ON_ERROR_STOP=1" >/dev/null <<SQL
insert into invite (code, label, max_uses, created_at) values ('$code', 'smoke and live probes', 1000000, now());
SQL
  box "umask 077 && printf '%s' '$code' > ~/bolo/probe-invite"
fi
printf '%s' "$code"
