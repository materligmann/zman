#!/usr/bin/env bash
# Génère les exemples réels de la page /api en interrogeant un serveur local.
# Usage : scripts/gen-examples.sh [base_url]   (défaut : lance le serveur sur :8099)
set -euo pipefail
cd "$(dirname "$0")/.."
BASE=${1:-}
PID=""
if [ -z "$BASE" ]; then
  PORT=8099 RATE_LIMIT_RPS=0 go run ./cmd/server >/dev/null 2>&1 &
  PID=$!
  BASE=http://localhost:8099
  for i in $(seq 1 90); do curl -sf "$BASE/api/health" >/dev/null 2>&1 && break; sleep 1; done
fi
out=web/examples
mkdir -p "$out"
curl -sf "$BASE/api/now"                                   > "$out/now.txt"
curl -sf -H 'Accept: text/plain' "$BASE/api/now"           > "$out/now-text.txt"
curl -sf "$BASE/api/rega/2395824"                          > "$out/rega.txt"
curl -sf "$BASE/api/date/5787/7/1"                         > "$out/date.txt"
curl -sf "$BASE/api/molad/5787/7"                          > "$out/molad.txt"
curl -sf "$BASE/api/year/5787"                             > "$out/year.txt"
curl -sf "$BASE/api/health"                                > "$out/health.txt" || true
# Indentation lisible du JSON.
for f in now rega date molad year health; do
  python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); json.dump(d, open(sys.argv[1],"w"), ensure_ascii=False, indent=2); open(sys.argv[1],"a").write("\n")' "$out/$f.txt"
done
[ -n "$PID" ] && kill "$PID" 2>/dev/null || true
ls -la "$out"
