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

echo "=== Prepare standalone folders ==="
cd "$ROOT_DIR"
rm -rf fronted-main/.next/standalone/.next/static fronted-main/.next/standalone/public
rm -rf fonted-oa/.next/standalone/.next/static fonted-oa/.next/standalone/public
mkdir -p fronted-main/.next/standalone/.next
mkdir -p fonted-oa/.next/standalone/.next
cp -R fronted-main/.next/static fronted-main/.next/standalone/.next/static
cp -R fronted-main/public fronted-main/.next/standalone/public
cp fronted-main/ecosystem.config.js fronted-main/.next/standalone/ecosystem.config.js
cp fronted-main/start-pm2.sh fronted-main/.next/standalone/start-pm2.sh
cp fronted-main/DEPLOY.md fronted-main/.next/standalone/DEPLOY.md
cp -R fonted-oa/.next/static fonted-oa/.next/standalone/.next/static
cp -R fonted-oa/public fonted-oa/.next/standalone/public
cp fonted-oa/ecosystem.config.js fonted-oa/.next/standalone/ecosystem.config.js
cp fonted-oa/start-pm2.sh fonted-oa/.next/standalone/start-pm2.sh
cp fonted-oa/DEPLOY.md fonted-oa/.next/standalone/DEPLOY.md
chmod +x fronted-main/.next/standalone/start-pm2.sh fonted-oa/.next/standalone/start-pm2.sh

echo "=== Package ==="
mkdir -p deploy
rm -f deploy/fronted-main.zip deploy/fonted-oa.zip
cp deploy-frontends-server.sh deploy/deploy-frontends-server.sh
chmod +x deploy/deploy-frontends-server.sh

if command -v powershell.exe >/dev/null 2>&1; then
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/create-standalone-zip.ps1 -SourceDir fronted-main/.next/standalone -DestinationZip deploy/fronted-main.zip
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/create-standalone-zip.ps1 -SourceDir fonted-oa/.next/standalone -DestinationZip deploy/fonted-oa.zip
else
  (cd fronted-main/.next/standalone && zip -qr "$ROOT_DIR/deploy/fronted-main.zip" .)
  (cd fonted-oa/.next/standalone && zip -qr "$ROOT_DIR/deploy/fonted-oa.zip" .)
fi

echo "Done:"
ls -lh deploy/fronted-main.zip deploy/fonted-oa.zip deploy/deploy-frontends-server.sh
echo "Server start:"
echo "  Upload deploy folder files to server, then run:"
echo "  bash deploy-frontends-server.sh"
