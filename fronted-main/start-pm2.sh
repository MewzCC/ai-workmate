#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# 缺失构建产物时才本地构建（服务器部署包若已含 dist/ 则跳过）
if [ ! -d dist ]; then
  npm run build
fi

# 用 PM2 启动 Vite 预览服务器监听 3000
pm2 delete fronted-main 2>/dev/null || true
pm2 start ecosystem.config.cjs --update-env
pm2 save
pm2 status
