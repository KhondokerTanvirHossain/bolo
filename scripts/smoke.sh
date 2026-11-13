#!/usr/bin/env bash
# Production smoke test: Bolo answers correctly, and the box's other tenants still answer.
set -uo pipefail
cd "$(dirname "$0")/.."
source scripts/box.sh
fail=0
check() { # name, expected, actual
  if [[ "$3" == "$2" ]]; then printf '  ok    %-44s %s\n' "$1" "$3"; else printf '  FAIL  %-44s want %s, got %s\n' "$1" "$2" "$3"; fail=1; fi
}
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$@"; }

echo "Bolo ($BOLO_URL)"
check "home page" 200 "$(code "$BOLO_URL/")"
check "title is just Bolo" yes "$(curl -s --max-time 15 "$BOLO_URL/" | grep -q '<title>Bolo</title>' && echo yes || echo no)"
check "api health" '{"status":"UP"}' "$(curl -s --max-time 15 "$BOLO_URL/api/actuator/health")"
check "pricing page" 200 "$(code "$BOLO_URL/pricing")"
check "invite status needs a session" 401 "$(code "$BOLO_URL/api/referral")"
check "what Bolo remembers needs a session" 401 "$(code "$BOLO_URL/api/memory")"
check "plans are public" yes "$(curl -s --max-time 15 "$BOLO_URL/api/plans" | grep -q '"plan":"basic"' && echo yes || echo no)"
# Open sign-up (P1d): anyone gets in without an invite, but a guest signs in with Google before the first talk.
# The live probes use their own unlimited probes invite (scripts/probe-invite.sh).
jar="$(mktemp)"; trap 'rm -f "$jar"' EXIT
post() { curl -s --max-time 15 -b "$jar" -c "$jar" -X POST "$BOLO_URL$1" -H 'Content-Type: application/json' -H 'X-Bolo: 1' \
  -H "Origin: $BOLO_URL" -d "$2"; }
check "a newcomer gets in without an invite" guest "$(post /api/session '{}' | grep -o '"kind":"guest"' | cut -d'"' -f4)"
post /api/consent '{"keepRecordings":false,"over13":true,"policyVersion":"2026-11-v1"}' >/dev/null
check "a guest signs in before the first talk" '{"code":"sign-in"}' "$(post /api/talk '{}')"
INVITE="$(scripts/probe-invite.sh 2>/dev/null || true)"
headers=$(curl -s -D - -o /dev/null --max-time 15 -X POST "$BOLO_URL/api/session" \
  -H 'Content-Type: application/json' -H 'X-Bolo: 1' -H "Origin: $BOLO_URL" -d "{\"invite\":\"$INVITE\"}")
check "session sets a Secure HttpOnly cookie" yes "$(grep -i '^set-cookie: __Host-bolo_session=' <<<"$headers" | grep -qi 'secure' && grep -i '^set-cookie: __Host-bolo_session=' <<<"$headers" | grep -qi 'httponly' && echo yes || echo no)"
check "write without X-Bolo is refused" 403 "$(code -X POST "$BOLO_URL/api/session" -H 'Content-Type: application/json' -H "Origin: $BOLO_URL" -d '{}')"
check "HSTS header" yes "$(curl -s -I --max-time 15 "$BOLO_URL/" | grep -qi '^strict-transport-security' && echo yes || echo no)"
check "talk socket refuses a visitor without a session" 403 "$(code --http1.1 -H 'Connection: Upgrade' -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' -H "Origin: $BOLO_URL" \
  "$BOLO_URL/ws/talk/00000000-0000-0000-0000-000000000000")"
check "old sslip.io address redirects here" "301 $BOLO_URL/x?y=1" "$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' --max-time 15 'https://bolo.13-205-14-249.sslip.io/x?y=1')"

echo "Other tenants (must be unchanged by any Bolo deploy)"
for url in https://medioo.eliooo.org/ https://drugdb.eliooo.org/ https://n8n.eliooo.org/; do
  c=$(code "$url"); [[ "$c" =~ ^(2|3)[0-9][0-9]$ ]] && check "$url" "$c" "$c" || check "$url" "2xx/3xx" "$c"
done
exit $fail
