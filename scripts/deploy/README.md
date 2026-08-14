# AI WorkMate 轮询自动部署

无公网 IP 场景下的自动部署方案：服务器定时轮询 GitHub 远端，检测到 `main` 分支有新提交后自动 `git pull` 并重建 Docker 容器。

## 原理

```
cron/systemd timer（每 5 分钟）
   └─ auto-deploy.sh
        ├─ git fetch origin main          # 拉取远端引用（增量，很轻）
        ├─ 对比本地/远端 HEAD
        │    ├─ 无更新 → 退出
        │    └─ 有更新 → git pull --rebase → docker compose up -d --build → 健康检查
        └─ 日志写入 logs/auto-deploy.log
```

## 目录结构

```
scripts/
├── auto-deploy.sh                 # 核心部署脚本（单次执行）
└── deploy/
    ├── ai-workmate-deploy.service # systemd 服务单元
    ├── ai-workmate-deploy.timer   # 定时器（2 分钟 + 每 5 分钟）
    └── install-deploy.sh          # 一键安装 / 卸载 / 查看状态
```

## 快速开始

### 1. 首次手动验证

```bash
cd ~/galangel/ai-workmate
./scripts/auto-deploy.sh
```

正常情况显示 `无更新，退出`。查看日志：`cat logs/auto-deploy.log`

### 2. 安装自动定时（推荐，需要 sudo）

```bash
sudo bash scripts/deploy/install-deploy.sh
```

会自动完成：复制 systemd 单元 → 手动验证脚本 → 启用 timer。

### 3. 验证安装

```bash
bash scripts/deploy/install-deploy.sh --status
```

应能看到 timer 的 NEXT 时间与最近一次部署结果。

## 日常使用

### 部署流程（推代码即生效）

本地改代码 → `git push` 到 `main` → 服务器 5 分钟内自动拉取、构建并健康检查。

### 查看部署历史

```bash
tail -f logs/auto-deploy.log
```

### 手动触发一次部署

```bash
./scripts/auto-deploy.sh
```

## 配置项

可通过环境变量覆盖，示例如下：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `REPO_DIR` | 仓库根目录 | 仓库路径 |
| `REMOTE` / `BRANCH` | `origin` / `main` | 轮询的远端与分支 |
| `ENV_FILE` | `.env.docker` | docker compose 使用的 env 文件 |
| `DEPLOY_INCLUDE_OCR` | `0` | 设为 `1` 额外构建 `ocr-service`（`--profile ocr`） |
| `BUILD_TIMEOUT` | `1800` | 构建超时（秒） |
| `HEALTH_RETRIES` | `30` | 健康检查重试次数 |
| `HEALTH_INTERVAL` | `10` | 健康检查间隔（秒） |
| `LOG_FILE` | `logs/auto-deploy.log` | 日志路径 |

示例（systemd 场景，在 `.service` 里加）：

```ini
[Service]
Environment=DEPLOY_INCLUDE_OCR=1
```

## 备选：crontab

无 systemd 环境时，`crontab -e` 追加：

```
*/5 * * * * /home/galangel/galangel/ai-workmate/scripts/auto-deploy.sh
```

## 卸载

```bash
sudo bash scripts/deploy/install-deploy.sh --uninstall
```

## 常见问题

- **显示「工作区存在未提交改动」**：服务器上有未提交的本地修改，处理掉再部署。这是保护机制，防止覆盖本地改动。
- **显示「上一次构建仍在进行中」**：构建耗时超过轮询间隔，flock 锁自动跳过，无需处理。
- **健康检查失败（exit 1）**：容器起来了但不健康，查看 `docker compose ps` 和日志。
- **换机器/换路径**：修改 `scripts/deploy/ai-workmate-deploy.service` 里的 `User`、`WorkingDirectory`、`ExecStart` 路径后再安装。
