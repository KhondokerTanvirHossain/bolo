#!/usr/bin/env bash
# Creates a closed-beta invite link: scripts/invite.sh <label> <max-uses>
# The label is for you (who it's for); the link works until it has been used max-uses times by new learners.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LABEL="${1:?usage: scripts/invite.sh <label> <max-uses>}"
MAX="${2:?usage: scripts/invite.sh <label> <max-uses>}"
[[ "$MAX" =~ ^[1-9][0-9]{0,3}$ ]] || { echo "max-uses must be 1-9999" >&2; exit 2; }
[[ "$LABEL" =~ ^[A-Za-z0-9\ _.@-]{1,60}$ ]] || { echo "label: letters, digits, space, _ . @ - (max 60)" >&2; exit 2; }
CODE="$(LC_ALL=C tr -dc 'a-z0-9' < /dev/urandom | head -c 10)"
source "$ROOT/scripts/box.sh"
box "docker exec -i bolo-db psql -U bolo -d bolo -q -v ON_ERROR_STOP=1" <<SQL
insert into invite (code, label, max_uses, created_at) values ('$CODE', '$LABEL', $MAX, now());
SQL
echo "${BOLO_URL}/?invite=${CODE}   ($LABEL, up to $MAX people)"
