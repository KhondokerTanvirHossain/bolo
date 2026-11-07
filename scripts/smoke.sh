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
headers=$(curl -s -D - -o /dev/null --max-time 15 -X POST "$BOLO_URL/api/session" \
  -H 'Content-Type: application/json' -H 'X-Bolo: 1' -H "Origin: $BOLO_URL" -d '{}')
check "session sets a Secure HttpOnly cookie" yes "$(grep -i '^set-cookie: __Host-bolo_session=' <<<"$headers" | grep -qi 'secure' && grep -i '^set-cookie: __Host-bolo_session=' <<<"$headers" | grep -qi 'httponly' && echo yes || echo no)"
check "write without X-Bolo is refused" 403 "$(code -X POST "$BOLO_URL/api/session" -H 'Content-Type: application/json' -H "Origin: $BOLO_URL" -d '{}')"
check "HSTS header" yes "$(curl -s -I --max-time 15 "$BOLO_URL/" | grep -qi '^strict-transport-security' && echo yes || echo no)"

echo "Other tenants (must be unchanged by any Bolo deploy)"
for url in https://medioo.eliooo.org/ https://drugdb.eliooo.org/ https://n8n.eliooo.org/; do
  c=$(code "$url"); [[ "$c" =~ ^(2|3)[0-9][0-9]$ ]] && check "$url" "$c" "$c" || check "$url" "2xx/3xx" "$c"
done
exit $fail
