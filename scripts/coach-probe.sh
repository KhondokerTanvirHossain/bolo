#!/usr/bin/env bash
# A real ~2-minute talk with the deployed Bolo (4 spoken turns), then the Coach's debrief: prints time-to-ready and the JSON.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/box.sh
cd api && BOLO_LIVE_URL="$BOLO_URL" ./gradlew liveProbe --tests '*LiveCoachProbe*' --console=plain \
  | grep -E "turn [0-9] done|debrief after|PASSED|FAILED|Exception|Error" || true
grep -q 'failures="0" errors="0"' build/test-results/liveProbe/TEST-app.bolo.coaching.LiveCoachProbe.xml
