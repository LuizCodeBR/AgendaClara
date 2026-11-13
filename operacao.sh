#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
mkdir -p .cache/tmp
export TMPDIR="$ROOT/.cache/tmp"
if [[ $# -eq 0 ]]; then set -- --operations=report; fi
exec java -Xmx512m "-Djava.io.tmpdir=$ROOT/.cache/tmp" -jar "$ROOT/target/agenda-clara-0.8.0.jar" "$@"
