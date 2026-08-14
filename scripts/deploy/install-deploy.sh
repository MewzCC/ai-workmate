#!/usr/bin/env bash
# =====================================================================
# AI WorkMate 服务器部署一键安装脚本
# 用法：sudo bash scripts/deploy/install-deploy.sh [--install|--uninstall|--status]
#
# 动作：
#   install    （默认）安装 systemd service + timer 并启用
#   uninstall  停止并删除 systemd 单元
#   status     查看定时器与最近一次部署结果
#
# 说明：
#   - 安装前会把 systemd 单元复制到 /etc/systemd/system/，使用仓库中的
#     User/WorkingDirectory/ExecStart 配置（路径固定为 ~/galangel/ai-workmate）
#   - 执行前会先手动跑一次 auto-deploy.sh 验证脚本可用
# =====================================================================

set -euo pipefail

ACTION="${1:-install}"
ACTION="${ACTION#--}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE="$SCRIPT_DIR/ai-workmate-deploy.service"
TIMER="$SCRIPT_DIR/ai-workmate-deploy.timer"
UNIT_PREFIX="ai-workmate-deploy"

install() {
  if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: install 需要 sudo 权限" >&2
    exit 1
  fi

  echo "==> 复制 systemd 单元到 /etc/systemd/system/"
  cp "$SERVICE" "/etc/systemd/system/${UNIT_PREFIX}.service"
  cp "$TIMER" "/etc/systemd/system/${UNIT_PREFIX}.timer"

  echo "==> 校验并手动验证 auto-deploy.sh"
  SCRIPT_PATH="$(sed -n 's|^ExecStart=||p' "$SERVICE")"
  RUN_USER="$(sed -n 's/^User=//p' "$SERVICE")"
  RUN_USER="${RUN_USER:-$(whoami)}"

  # 1. 校验目标用户存在
  if ! id "$RUN_USER" >/dev/null 2>&1; then
    echo "ERROR: 用户 $RUN_USER 不存在，请检查 .service 中的 User=" >&2
    exit 1
  fi

  # 2. 校验脚本存在且有可执行权限（systemd 203/EXEC 的根因）
  if [ ! -x "$SCRIPT_PATH" ]; then
    echo "ERROR: 脚本不可执行：$SCRIPT_PATH" >&2
    echo "        请先执行: chmod +x $SCRIPT_PATH 并提交（git 会记录可执行位）" >&2
    exit 1
  fi

  # 3. 校验 bash 语法
  if ! bash -n "$SCRIPT_PATH"; then
    echo "ERROR: $SCRIPT_PATH 存在 bash 语法错误" >&2
    exit 1
  fi

  # 4. 以目标用户身份试运行一次（无更新则立即退出，有更新则执行真实部署）
  if ! sudo -u "$RUN_USER" "$SCRIPT_PATH"; then
    echo "ERROR: 试运行失败，中止安装。请查看 $SCRIPT_PATH 日志定位原因。" >&2
    exit 1
  fi

  echo "==> 重载并启用 timer"
  systemctl daemon-reload
  systemctl enable --now "${UNIT_PREFIX}.timer"
  echo "==> 安装完成"
  status
}

uninstall() {
  if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: uninstall 需要 sudo 权限" >&2
    exit 1
  fi
  systemctl disable --now "${UNIT_PREFIX}.timer" 2>/dev/null || true
  rm -f "/etc/systemd/system/${UNIT_PREFIX}.service" "/etc/systemd/system/${UNIT_PREFIX}.timer"
  systemctl daemon-reload
  echo "==> 已卸载 systemd 单元"
}

status() {
  echo "==> timer 列表"
  systemctl list-timers "${UNIT_PREFIX}.timer" || true
  echo
  echo "==> 最近一次部署结果"
  systemctl status "${UNIT_PREFIX}.service" --no-pager 2>/dev/null | grep -E "Active:|status=" || true
  echo
  echo "==> 部署日志尾部"
  tail -n 10 "/home/galangel/galangel/ai-workmate/logs/auto-deploy.log" 2>/dev/null || echo "（暂无日志）"
}

case "$ACTION" in
  install)   install ;;
  uninstall) uninstall ;;
  status)    status ;;
  *)
    echo "用法: $0 [--install|--uninstall|--status]" >&2
    exit 1
    ;;
esac
