#!/usr/bin/env bash
# =====================================================================
# AI WorkMate 轮询自动部署脚本
# 用法：
#   ./scripts/auto-deploy.sh               # 单次执行（对比远端，有更新才部署）
#   DEPLOY_INCLUDE_OCR=1 ./scripts/auto-deploy.sh   # 额外构建 ocr-service
#
# 建议配合 systemd timer 或 crontab 每 2~5 分钟执行一次（见文件底部说明）。
# 依赖：git、docker、docker compose（v2），脚本自带 flock 互斥锁，不会并发构建。
# =====================================================================

set -euo pipefail

# ---------- 可配置项 ----------
REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
REMOTE="${REMOTE:-origin}"
BRANCH="${BRANCH:-main}"
LOG_FILE="${LOG_FILE:-$REPO_DIR/logs/auto-deploy.log}"
# 是否包含 ocr-service（docker-compose 中带 profiles: ["ocr"]，默认不构建）
DEPLOY_INCLUDE_OCR="${DEPLOY_INCLUDE_OCR:-0}"
# 构建超时（秒），防止卡死
BUILD_TIMEOUT="${BUILD_TIMEOUT:-1800}"
# 健康检查相关
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-10}"
HEALTH_BACKEND="${HEALTH_BACKEND:-http://127.0.0.1:8080/api/system/health}"
HEALTH_MAIN="${HEALTH_MAIN:-http://127.0.0.1:3000/}"
HEALTH_OA="${HEALTH_OA:-http://127.0.0.1:3001/oa}"

LOCK_FILE="${LOCK_FILE:-/tmp/ai-workmate-deploy.lock}"
# env 文件名（位于仓库根目录）；如需改用其他文件，设置 ENV_FILE 环境变量
ENV_FILE="${ENV_FILE:-.env.docker}"
# 用数组承载 compose 命令，避免字符串被当作单个命令名传给 timeout
COMPOSE_CMD=(docker compose --env-file "$ENV_FILE")

log() { printf '%s [%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1" "$2"; }

# 互斥锁：整轮「拉取+构建」期间阻止下一次轮询进入
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  log "INFO" "上一次构建仍在进行中，跳过本次轮询"
  exit 0
fi

mkdir -p "$(dirname "$LOG_FILE")"
exec >>"$LOG_FILE" 2>&1
log "INFO" "=== auto-deploy 开始（repo=$REPO_DIR $REMOTE/$BRANCH） ==="

cd "$REPO_DIR"

# ---------- 1. 拉取远端引用，比对本地 HEAD ----------
log "INFO" "git fetch $REMOTE $BRANCH"
git fetch "$REMOTE" "$BRANCH" --quiet

LOCAL_HEAD="$(git rev-parse HEAD)"
REMOTE_HEAD="$(git rev-parse FETCH_HEAD)"

if [ "$LOCAL_HEAD" = "$REMOTE_HEAD" ]; then
  log "INFO" "无更新（HEAD=$LOCAL_HEAD），退出"
  exit 0
fi
log "INFO" "检测到更新: $LOCAL_HEAD -> $REMOTE_HEAD"

# ---------- 2. 检查工作区是否干净 ----------
if [ -n "$(git status --porcelain)" ]; then
  log "WARN" "工作区存在未提交改动，跳过部署，请先处理："
  while IFS= read -r line; do
    log "WARN" "    $line"
  done < <(git status --porcelain)
  exit 1
fi

# ---------- 3. 更新代码 ----------
log "INFO" "git pull --rebase $REMOTE $BRANCH"
git pull --rebase "$REMOTE" "$BRANCH"

NEW_HEAD="$(git rev-parse HEAD)"
log "INFO" "代码已更新: HEAD=$NEW_HEAD"

# ---------- 4. 构建并启动 ----------
COMPOSE_PROFILE_ARGS=()
if [ "$DEPLOY_INCLUDE_OCR" = "1" ]; then
  COMPOSE_PROFILE_ARGS+=(--profile ocr)
fi

log "INFO" "docker compose 构建启动中（timeout=${BUILD_TIMEOUT}s, ocr=$DEPLOY_INCLUDE_OCR）"
timeout "$BUILD_TIMEOUT" "${COMPOSE_CMD[@]}" "${COMPOSE_PROFILE_ARGS[@]}" up -d --build

# ---------- 5. 健康检查 ----------
check_http() {
  local name="$1" url="$2"
  local ok=0
  for i in $(seq 1 "$HEALTH_RETRIES"); do
    if curl -fsS -o /dev/null --max-time 5 "$url"; then
      log "INFO" "健康检查通过: $name ($url)"
      return 0
    fi
    sleep "$HEALTH_INTERVAL"
  done
  log "ERROR" "健康检查失败: $name ($url)"
  return 1
}

FAILED=0
check_http "backend"  "$HEALTH_BACKEND" || FAILED=1
check_http "fronted-main" "$HEALTH_MAIN" || FAILED=1
check_http "fonted-oa"   "$HEALTH_OA"   || FAILED=1

if [ "$FAILED" -eq 0 ]; then
  log "INFO" "=== 部署完成（HEAD=$NEW_HEAD） ==="
  "${COMPOSE_CMD[@]}" ps
else
  log "ERROR" "=== 部署完成但健康检查未全部通过，请检查容器状态 ==="
  "${COMPOSE_CMD[@]}" ps
  exit 1
fi

# =====================================================================
# 定时执行方式（任选其一）
#
# A. systemd timer（推荐，可查日志，适合无 sudo 场景需管理员部署）
#    /etc/systemd/system/ai-workmate-deploy.service:
#      [Unit]
#      Description=AI WorkMate auto deploy
#      After=docker.service
#      Requires=docker.service
#
#      [Service]
#      Type=oneshot
#      ExecStart=/path/to/ai-workmate/scripts/auto-deploy.sh
#
#    /etc/systemd/system/ai-workmate-deploy.timer:
#      [Unit]
#      Description=AI WorkMate auto deploy poller
#
#      [Timer]
#      OnBootSec=2min
#      OnUnitActiveSec=5min
#
#      [Install]
#      WantedBy=timers.target
#
#    sudo systemctl daemon-reload
#    sudo systemctl enable --now ai-workmate-deploy.timer
#
# B. crontab（简单，无 systemd 环境）
#    crontab -e 追加：
#    */5 * * * * /path/to/ai-workmate/scripts/auto-deploy.sh
# =====================================================================
