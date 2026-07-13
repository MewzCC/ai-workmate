#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
pm2 delete fronted-main 2>/dev/null || true
PORT=3000 HOSTNAME=0.0.0.0 NODE_ENV=production pm2 start ./server.js --name fronted-main --update-env
pm2 save
pm2 status
