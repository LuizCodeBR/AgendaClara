#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
if [[ "${1:-}" != "--skip-build" ]]; then bash scripts/build.sh; fi
mkdir -p .cache/tmp data
echo 'Abra http://localhost:8080. Use Ctrl+C para encerrar.'
exec java "-Djava.io.tmpdir=$ROOT/.cache/tmp" -jar target/agenda-clara-0.3.0.jar --spring.profiles.active=demo
