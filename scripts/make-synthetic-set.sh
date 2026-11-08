#!/usr/bin/env bash
# Builds golden-sets/synthetic: clear AI voices reading sentences dense in the six patterns (labelled: no issues),
# plus the two practice clips from the API's test fixtures. It measures false flags on clear speech, not accuracy
# on Bangla-accented speech. macOS only (say + afconvert).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/golden-sets/synthetic"
mkdir -p "$OUT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SENTENCES=(
  "vans|Very few vans visit the village on Wednesday."
  "think|I think these three thick books are worth it."
  "fish|She sells fresh fish near the zoo station."
)
for voice in Samantha Daniel Karen Rishi; do
  for entry in "${SENTENCES[@]}"; do
    id="${entry%%|*}"; text="${entry#*|}"
    v="$(echo "$voice" | tr '[:upper:]' '[:lower:]')"
    say -v "$voice" -o "$TMP/$id-$v.aiff" "$text"
    afconvert -f WAVE -d LEI16@16000 -c 1 "$TMP/$id-$v.aiff" "$OUT/$id-$v.wav"
  done
done
cp "$ROOT/api/src/test/resources/audio/practice-ischool-16k.wav" "$OUT/practice-ischool.wav"
cp "$ROOT/api/src/test/resources/audio/practice-very-16k.wav" "$OUT/practice-very.wav"
echo "wrote $(ls "$OUT"/*.wav | wc -l | tr -d ' ') clips to $OUT"
