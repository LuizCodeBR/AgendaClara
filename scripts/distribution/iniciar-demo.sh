#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
mkdir -p tmp data
export TMPDIR="$ROOT/tmp"
echo 'Abra http://localhost:8080 no Chrome ou Edge. Use Ctrl+C para encerrar.'
exec java "-Djava.io.tmpdir=$ROOT/tmp" -jar agenda-clara-0.1.0.jar --spring.profiles.active=demo "$@"
