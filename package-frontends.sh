#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

echo "=== Build frontend-main ==="
cd "$ROOT_DIR/frontend-main"
npm install
npm run build

echo "=== Build frontend-oa ==="
cd "$ROOT_DIR/frontend-oa"
npm install
npm run build

echo "=== Prepare standalone folders ==="
cd "$ROOT_DIR"
rm -rf frontend-main/.next/standalone/.next/static frontend-main/.next/standalone/public
rm -rf frontend-oa/.next/standalone/.next/static frontend-oa/.next/standalone/public
mkdir -p frontend-main/.next/standalone/.next
mkdir -p frontend-oa/.next/standalone/.next
cp -R frontend-main/.next/static frontend-main/.next/standalone/.next/static
cp -R frontend-main/public frontend-main/.next/standalone/public
cp frontend-main/ecosystem.config.js frontend-main/.next/standalone/ecosystem.config.js
cp frontend-main/start-pm2.sh frontend-main/.next/standalone/start-pm2.sh
cp frontend-main/DEPLOY.md frontend-main/.next/standalone/DEPLOY.md
cp -R frontend-oa/.next/static frontend-oa/.next/standalone/.next/static
cp -R frontend-oa/public frontend-oa/.next/standalone/public
cp frontend-oa/ecosystem.config.js frontend-oa/.next/standalone/ecosystem.config.js
cp frontend-oa/start-pm2.sh frontend-oa/.next/standalone/start-pm2.sh
cp frontend-oa/DEPLOY.md frontend-oa/.next/standalone/DEPLOY.md
chmod +x frontend-main/.next/standalone/start-pm2.sh frontend-oa/.next/standalone/start-pm2.sh

echo "=== Package ==="
mkdir -p deploy
rm -f deploy/frontend-main.zip deploy/frontend-oa.zip
cp deploy-frontends-server.sh deploy/deploy-frontends-server.sh
chmod +x deploy/deploy-frontends-server.sh

if command -v powershell.exe >/dev/null 2>&1; then
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/create-standalone-zip.ps1 -SourceDir frontend-main/.next/standalone -DestinationZip deploy/frontend-main.zip
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/create-standalone-zip.ps1 -SourceDir frontend-oa/.next/standalone -DestinationZip deploy/frontend-oa.zip
else
  (cd frontend-main/.next/standalone && zip -qr "$ROOT_DIR/deploy/frontend-main.zip" .)
  (cd frontend-oa/.next/standalone && zip -qr "$ROOT_DIR/deploy/frontend-oa.zip" .)
fi

echo "Done:"
ls -lh deploy/frontend-main.zip deploy/frontend-oa.zip deploy/deploy-frontends-server.sh
echo "Server start:"
echo "  Upload deploy folder files to server, then run:"
echo "  bash deploy-frontends-server.sh"
