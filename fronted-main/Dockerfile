# 多阶段构建：Node 构建 Vite 静态产物 → nginx 托管并反代 /api 到后端
# 生产 nginx 同 IP 同端口反代场景：主站 / 与 OA /oa/ 共用一个 nginx，由本容器承载主站

# ---------- 构建阶段 ----------
FROM node:20-alpine AS builder
WORKDIR /build

# 先复制 package 元数据，利用 Docker 层缓存加速依赖安装
COPY package.json package-lock.json* ./
RUN npm ci || npm install

# 复制源码
COPY . .

# 构建期注入 OA 入口地址：留空时代码自动跳到 http://<host>:3001/oa/（主站 3000 / OA 3001 分离）
# 同端口反代场景：通过 --build-arg VITE_OA_URL=/oa/ 覆盖
# 独立域名部署：通过 --build-arg VITE_OA_URL=https://oa.example.com/oa/ 覆盖
ARG VITE_OA_URL=
ENV VITE_OA_URL=$VITE_OA_URL
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# 清空 nginx 默认静态目录，复制构建产物
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /build/dist /usr/share/nginx/html

# 主站专用 nginx 配置：托管 / + 反代 /api 到后端
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
