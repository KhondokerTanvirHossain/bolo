#!/usr/bin/env bash
# Ship HEAD: wait for its CI run to pass, then deploy, smoke, and the 4 live probes. Never deploys a red commit.
# Usage: scripts/ship.sh   (after pushing p0-foundations)
set -uo pipefail
cd "$(dirname "$0")/.."
sha="$(git rev-parse HEAD)"
run=""
for _ in $(seq 1 30); do
  run="$(gh run list --branch p0-foundations --limit 5 --json databaseId,headSha -q ".[] | select(.headSha==\"$sha\") | .databaseId" 2>/dev/null | head -1)"
  [[ -n "$run" ]] && break; sleep 10
done
[[ -n "$run" ]] || { echo "no CI run for $sha"; exit 1; }
until s=$(gh run view "$run" --json status,conclusion -q '"\(.status) \(.conclusion)"' 2>/dev/null) && [[ "$s" == completed* ]]; do sleep 20; done
echo "ci $run: $s"
if [[ "$s" != "completed success" ]]; then gh run view "$run" --log-failed 2>/dev/null | grep -E "✘|\) \[|Error:|error" | head -15; exit 1; fi
scripts/deploy.sh "$sha" > /tmp/bolo-deploy.log 2>&1; d=$?; echo "deploy exit $d"
grep -E "Deployed|FAIL|ok " /tmp/bolo-deploy.log | head -16
export BOLO_LIVE_INVITE="$(scripts/probe-invite.sh 2>/dev/null)"
# The probes run from their own copy of the commit, so they never clash with builds in this working tree.
PROBES="$(mktemp -d)/probes"
git worktree add --detach -q "$PROBES" "$sha"
trap 'git worktree remove --force "$PROBES" >/dev/null 2>&1 || true' EXIT
cd "$PROBES/api"
for p in LiveTalkProbe LiveCoachProbe LivePracticeProbe LiveCrisisProbe; do
  ok=1
  for try in 1 2; do  # this machine's network sometimes drops a real-time upload; one retry
    BOLO_LIVE_URL=https://bolo.eliooo.org ./gradlew liveProbe --tests "*$p*" --console=plain -q > "/tmp/bolo-$p.log" 2>&1 && { ok=0; break; }
  done
  echo "$p exit $ok"
done
