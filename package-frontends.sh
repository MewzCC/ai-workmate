#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "=== Build fronted-main ==="
cd "$ROOT_DIR/fronted-main"
npm install
npm run build

echo "=== Build fonted-oa ==="
cd "$ROOT_DIR/fonted-oa"
npm install
npm run build

echo "=== Package ==="
cd "$ROOT_DIR"
mkdir -p deploy
rm -f deploy/fronted-main.zip deploy/fonted-oa.zip

if command -v powershell.exe >/dev/null 2>&1; then
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path 'fronted-main/.next','fronted-main/public','fronted-main/package.json','fronted-main/package-lock.json','fronted-main/next.config.js' -DestinationPath 'deploy/fronted-main.zip' -Force"
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path 'fonted-oa/.next','fonted-oa/public','fonted-oa/package.json','fonted-oa/package-lock.json','fonted-oa/next.config.js' -DestinationPath 'deploy/fonted-oa.zip' -Force"
else
  (cd fronted-main && zip -qr ../deploy/fronted-main.zip .next public package.json package-lock.json next.config.js)
  (cd fonted-oa && zip -qr ../deploy/fonted-oa.zip .next public package.json package-lock.json next.config.js)
fi

echo "Done:"
ls -lh deploy/fronted-main.zip deploy/fonted-oa.zip
