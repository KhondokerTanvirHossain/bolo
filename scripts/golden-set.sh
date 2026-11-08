#!/usr/bin/env bash
# Runs the Gate 1 accuracy harness on a golden set: scripts/golden-set.sh <set dir> [--offline|--refresh]
# The Azure key is read from the box straight into this process's environment: never printed, saved or logged.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SET="${1:?usage: scripts/golden-set.sh <set dir> [--offline|--refresh]}"
MODE="${2:-}"
SET_ABS="$(cd "$SET" && pwd)"
source "$ROOT/scripts/box.sh"
if [[ "$MODE" != "--offline" ]]; then
  BOLO_AZURE_SPEECH_KEY="$(box 'grep -m1 "^BOLO_AZURE_SPEECH_KEY=" ~/bolo/bolo.env | cut -d= -f2-')"
  BOLO_AZURE_SPEECH_REGION="$(box 'grep -m1 "^BOLO_AZURE_SPEECH_REGION=" ~/bolo/bolo.env | cut -d= -f2- || true')"
  if [[ -z "$BOLO_AZURE_SPEECH_KEY" ]]; then echo "No Azure key found on the box." >&2; exit 2; fi
  export BOLO_AZURE_SPEECH_KEY BOLO_AZURE_SPEECH_REGION
fi
cd "$ROOT/api"
# --no-daemon: no long-lived Gradle process keeps the key in its environment.
exec ./gradlew --no-daemon -q goldenSet -Pset="$SET_ABS" ${MODE:+-Pmode=${MODE#--}}
