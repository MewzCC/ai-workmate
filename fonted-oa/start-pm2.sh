#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
pm2 delete fonted-oa 2>/dev/null || true
PORT=3001 HOSTNAME=0.0.0.0 NODE_ENV=production pm2 start ./server.js --name fonted-oa --update-env
pm2 save
pm2 status
