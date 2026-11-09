#!/usr/bin/env bash
# Build both images locally for linux/amd64, ship them over SSH and restart Bolo on the shared box.
# Usage: scripts/deploy.sh   Deploys the current HEAD, built from a clean detached copy of that commit (so work in
#        progress in this tree can never leak into an image, and the tag always means exactly that commit).
#        To roll back, don't pass an old sha here — see docs/RUNBOOK.md → Rollback (it reuses the image on the box).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/box.sh

SHA="$(git rev-parse --short HEAD)"
if [[ $# -gt 0 && "$1" != "$SHA" ]]; then
  echo "Refusing: this builds HEAD ($SHA), so it can't be tagged '$1'. For a rollback see docs/RUNBOOK.md." >&2; exit 1
fi
echo "==> Building $SHA (clean copy of the commit)"
BUILD="$(mktemp -d)/bolo-$SHA"
git worktree add --detach -q "$BUILD" "$SHA"
trap 'git worktree remove --force "$BUILD" >/dev/null 2>&1 || true' EXIT
# Its own node_modules from pnpm's local store (a symlink would make Next trace files outside the copy).
(cd "$BUILD/web" && pnpm install --frozen-lockfile --prefer-offline --silent)
(cd "$BUILD/api" && ./gradlew bootJar -q --console=plain)
(cd "$BUILD/web" && pnpm build > /dev/null)
docker buildx build --platform linux/amd64 -t "bolo-api:$SHA" --load "$BUILD/api"
docker buildx build --platform linux/amd64 -t "bolo-web:$SHA" --load "$BUILD/web"

echo "==> Shipping images"
docker save "bolo-api:$SHA" "bolo-web:$SHA" | gzip -1 | box 'gunzip | docker load'
box 'mkdir -p ~/bolo'
scp -q -o BatchMode=yes -i "$BOX_KEY" "$BUILD/deploy/compose.prod.yml" "$BOX_HOST:bolo/compose.yml"

echo "==> Starting"
box "set -e; cd ~/bolo; test -f bolo.env || { echo 'missing ~/bolo/bolo.env (see deploy/bolo.env.example)'; exit 1; }
  PREV=\$(cat TAG 2>/dev/null || true)
  IMAGE_TAG=$SHA docker-compose --env-file bolo.env -p bolo -f compose.yml up -d --remove-orphans
  for i in \$(seq 1 60); do
    if docker exec n8n-caddy-1 wget -qO- http://bolo-api:8080/api/actuator/health 2>/dev/null | grep -q UP \
       && docker exec n8n-caddy-1 wget -qO- http://bolo-web:3000/ >/dev/null 2>&1; then break; fi
    sleep 2
    if [ \$i = 60 ]; then echo 'Bolo did not become healthy'; docker logs --tail 40 bolo-api; exit 1; fi
  done
  echo $SHA > TAG
  # Keep the current and previous tags for rollback; remove older Bolo images only, never another tenant's.
  # A redeploy of the same sha keeps the true previous tag (PREV is only advanced when the sha changes).
  if [ \"\$PREV\" = '$SHA' ]; then PREV=\$(cat PREV_TAG 2>/dev/null || true); else echo \"\$PREV\" > PREV_TAG; fi
  for repo in bolo-api bolo-web; do
    docker images \"\$repo\" --format '{{.Tag}}' | grep -v -x -e '$SHA' -e \"\$PREV\" -e '<none>' | xargs -r -I{} docker rmi \"\$repo:{}\" >/dev/null || true
  done
  free -m | awk '/Mem:/ {print \"box memory available: \" \$7 \" MB\"}'"
echo "==> Deployed $SHA"
scripts/smoke.sh
