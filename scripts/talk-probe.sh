#!/usr/bin/env bash
# One real turn with the deployed Bolo (real Azure + Claude): prints Bolo's opening, its reply, and turn end → first audio.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/box.sh
cd api && BOLO_LIVE_URL="$BOLO_URL" ./gradlew liveProbe --console=plain -q
