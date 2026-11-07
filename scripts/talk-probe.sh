#!/usr/bin/env bash
# One real turn with the deployed Bolo (real Azure + Claude): prints Bolo's opening, its reply, and turn end → first audio.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/box.sh
cd api && BOLO_LIVE_URL="$BOLO_URL" ./gradlew liveProbe --tests '*LiveTalkProbe*' --console=plain \
  | grep -E "opening caption|reply caption|first audio|ended:|PASSED|FAILED|Exception|Error" || true
grep -q 'failures="0" errors="0"' build/test-results/liveProbe/TEST-app.bolo.talk.LiveTalkProbe.xml
