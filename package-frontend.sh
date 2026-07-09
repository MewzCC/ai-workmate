#!/bin/bash
# AI WorkMate 前端打包脚本
# 用法: bash package-frontend.sh
# 产出: deploy/ai-workmate-frontend.zip

set -e

echo "=== 1. 安装依赖 ==="
cd frontend
npm install

echo "=== 2. 构建项目 ==="
npm run build

echo "=== 3. 整理部署文件 ==="
cd ..
rm -rf deploy/ai-workmate-frontend deploy/ai-workmate-frontend.zip
mkdir -p deploy/ai-workmate-frontend

# 复制 standalone 构建产物
cp -r frontend/.next/standalone/* deploy/ai-workmate-frontend/

# 复制 static 静态资源
cp -r frontend/.next/static deploy/ai-workmate-frontend/.next/static

# 修复 package.json 的 start 脚本（standalone 模式用 server.js 启动）
cat > deploy/ai-workmate-frontend/package.json << 'EOF'
{
  "name": "ai-workmate-frontend",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "start": "node server.js"
  }
}
EOF

echo "=== 4. 打包 zip ==="
cd deploy
zip -r ai-workmate-frontend.zip ai-workmate-frontend/

echo ""
echo "=== 打包完成 ==="
ls -lh deploy/ai-workmate-frontend.zip
