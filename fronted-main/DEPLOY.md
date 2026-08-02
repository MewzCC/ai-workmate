# fronted-main deploy

本工程已从 Next.js 迁移为 Vite SPA，构建产物为纯静态 `dist/`。

## 本地开发

```bash
npm install
npm run dev      # http://localhost:3000，/api 代理到 http://localhost:8080
```

## 生产部署（PM2 + Vite preview）

```bash
bash start-pm2.sh
```

`start-pm2.sh` 会先 `npm run build` 产出 `dist/`，再用 PM2 启动 `vite preview` 监听 3000。
服务器上需要存在 `node_modules`（不再是 Next standalone，需先 `npm install`）。

## 替代：用 nginx 托管静态产物

如果不想在服务器跑 Node 进程，可直接托管 `dist/`：

```bash
npm run build
# 把 dist/ 部署到 nginx web 根目录，/api 反代到后端 8080
```

## 环境变量

构建期注入 OA 入口地址（生产部署在反代/域名下时必须配置）：

```bash
# .env 或 CI 构建环境
VITE_OA_URL=https://oa.example.com/oa
```

未配置时，"立即尝试" 回退到 `http://<当前host>:3001/oa`。

## 自动部署

```bash
# 把 fronted-main.zip、fonted-oa.zip 和 deploy-frontends-server.sh 放到服务器同一目录
bash deploy-frontends-server.sh
```

## 调试

```bash
pm2 logs fronted-main --lines 100
cat pm2-error.log
ss -lntp | grep 3000
curl -I http://127.0.0.1:3000/
```
