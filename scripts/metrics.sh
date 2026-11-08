#!/usr/bin/env bash
# The onboarding funnel and time to first word (PRD §1, §10, R4), from the box's database. Read-only.
# Usage: scripts/metrics.sh [days]   (default 30)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DAYS="${1:-30}"
[[ "$DAYS" =~ ^[0-9]+$ ]] || { echo "days must be a number" >&2; exit 2; }
source "$ROOT/scripts/box.sh"
box "docker exec -i bolo-db psql -U bolo -d bolo -P pager=off -q" <<SQL
\\pset footer off
with w as (select * from event where at > now() - interval '$DAYS days')
select 'People who opened Bolo' as step, count(distinct learner_id)::text as value from w where name = 'app_first_open'
union all select 'Allowed the microphone', count(distinct learner_id)::text from w where name = 'mic_permission_result' and props->>'result' = 'granted'
union all select 'Finished onboarding (Day 1 clip)', count(distinct learner_id)::text from w where name = 'onboarding_step_completed' and props->>'step' = 'day1'
union all select 'Spoke a first word', count(distinct learner_id)::text from w where name = 'first_word_spoken'
union all select 'Time to first word, median (s) — target 60',
    coalesce(round(percentile_cont(0.5) within group (order by (props->>'seconds_since_first_open')::numeric)::numeric)::text, '–')
    from w where name = 'first_word_spoken'
union all select 'Time to first word, slowest 1 in 10 (s)',
    coalesce(round(percentile_cont(0.9) within group (order by (props->>'seconds_since_first_open')::numeric)::numeric)::text, '–')
    from w where name = 'first_word_spoken'
union all select 'Talks started', count(*)::text from w where name = 'session_started'
union all select 'Talks ended', count(*)::text from w where name = 'session_ended'
union all select 'Feedback viewed', count(*)::text from w where name = 'debrief_viewed';
SQL
