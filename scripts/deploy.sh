#!/usr/bin/env bash
# Build both images locally for linux/amd64, ship them over SSH and restart Bolo on the shared box.
# Usage: scripts/deploy.sh [sha]   (default: current HEAD; refuses a dirty tree so the tag means something)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/box.sh

if [[ -n "$(git status --porcelain -- api web deploy)" ]]; then
  echo "Refusing to deploy: api/, web/ or deploy/ has uncommitted changes." >&2; exit 1
fi
SHA="${1:-$(git rev-parse --short HEAD)}"
echo "==> Building $SHA"
(cd api && ./gradlew bootJar -q --console=plain)
(cd web && pnpm build > /dev/null)
docker buildx build --platform linux/amd64 -t "bolo-api:$SHA" --load api
docker buildx build --platform linux/amd64 -t "bolo-web:$SHA" --load web

echo "==> Shipping images"
docker save "bolo-api:$SHA" "bolo-web:$SHA" | gzip -1 | box 'gunzip | docker load'
box 'mkdir -p ~/bolo'
scp -q -o BatchMode=yes -i "$BOX_KEY" deploy/compose.prod.yml "$BOX_HOST:bolo/compose.yml"

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
  for repo in bolo-api bolo-web; do
    docker images \"\$repo\" --format '{{.Tag}}' | grep -v -x -e '$SHA' -e \"\$PREV\" | xargs -r -I{} docker rmi \"\$repo:{}\" >/dev/null || true
  done
  free -m | awk '/Mem:/ {print \"box memory available: \" \$7 \" MB\"}'"
echo "==> Deployed $SHA"
scripts/smoke.sh
