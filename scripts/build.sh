#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p .cache/{tmp,npm,maven,m2,browsers} data
export TMPDIR="$ROOT/.cache/tmp" npm_config_cache="$ROOT/.cache/npm"
export MAVEN_USER_HOME="$ROOT/.cache/maven" PLAYWRIGHT_BROWSERS_PATH="$ROOT/.cache/browsers"
node -e "const [a,b]=process.versions.node.split('.').map(Number);if(!((a===24&&b>=18)||a>=26)){console.error('Use Node.js 24.18+ (linha 24) ou 26+.');process.exit(1)}"
(cd frontend && npm ci --prefer-offline --no-audit --no-fund && npm test && npm run build)
./mvnw -Dmaven.repo.local=.cache/m2 -B -ntp clean verify
