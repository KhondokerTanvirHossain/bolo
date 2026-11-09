#!/usr/bin/env bash
# Takes a Google account out of the free plan (no counter, no limit): scripts/unlimited.sh <email>
# For the founder and staff test accounts. Prints how many accounts changed (0 = no account with that email yet:
# sign in once on the site first).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EMAIL="${1:?usage: scripts/unlimited.sh <email>}"
[[ "$EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || { echo "not an email address" >&2; exit 2; }
source "$ROOT/scripts/box.sh"
box "docker exec -i bolo-db psql -U bolo -d bolo -qtA -v ON_ERROR_STOP=1" <<SQL
with changed as (update learner set unlimited = true where id in (select learner_id from account where lower(email) = lower('$EMAIL')) returning 1)
select count(*) || ' account(s) now unlimited' from changed;
SQL
