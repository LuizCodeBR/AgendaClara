#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
mkdir -p tmp
export TMPDIR="$ROOT/tmp"
if [[ $# -eq 0 ]]; then set -- --backup=create; fi
exec java -Xmx512m "-Djava.io.tmpdir=$ROOT/tmp" -jar "$ROOT/agenda-clara.jar" "$@"
