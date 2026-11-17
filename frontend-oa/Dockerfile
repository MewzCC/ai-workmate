# 多阶段构建：Node 构建 Vite 静态产物 → nginx 托管 /oa/ 路由并反代 /api
# OA 的 vite base 为 /oa/，所有静态资源与路由都挂在 /oa/ 前缀下
# 生产部署：由外部 nginx 用 location /oa/ 转发到本容器 80 端口

# ---------- 构建阶段 ----------
FROM node:20-alpine AS builder
WORKDIR /build

# 先复制 package 元数据，利用 Docker 层缓存加速依赖安装
COPY package.json package-lock.json* ./
RUN npm ci || npm install

# 复制源码
COPY . .

RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# 清空 nginx 默认静态目录，复制构建产物
# vite base=/oa/，产物中 index.html 引用 /oa/assets/...，无需改写
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /build/dist /usr/share/nginx/html

# OA 专用 nginx 配置：托管 /oa/ + 反代 /api 到后端
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
