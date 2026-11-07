# Shared settings for scripts that reach the box. Source it; don't run it.
BOX_HOST="${BOX_HOST:-ec2-user@ec2-13-205-14-249.ap-south-1.compute.amazonaws.com}"
BOX_KEY="${BOX_KEY:-$HOME/Documents/000_office/umr/aws/bdemr-text-analysis.pem}"
BOLO_URL="${BOLO_URL:-https://bolo.eliooo.org}"
box() { ssh -o BatchMode=yes -o ConnectTimeout=15 -i "$BOX_KEY" "$BOX_HOST" "$@"; }
