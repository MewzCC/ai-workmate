@echo off
chcp 65001 >nul
echo ========================================
echo   配置 SSH 公钥免密登录
echo ========================================
echo.

setlocal

set "SERVER=8.138.46.183"
set "PORT=22"
set "USER=root"

where ssh >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ 未找到 ssh 命令，请先安装 OpenSSH 客户端
    pause
    exit /b 1
)

REM 检查是否已有公钥
if not exist "%USERPROFILE%\.ssh\id_rsa.pub" (
    echo [1/3] 生成 SSH 密钥对...
    ssh-keygen -t rsa -b 4096 -N "" -f "%USERPROFILE%\.ssh\id_rsa"
) else (
    echo [1/3] 已存在 SSH 密钥，跳过生成
)

echo [2/3] 上传公钥到服务器（首次需要输入密码）...
set /p SSHPASS="请输入服务器密码: "

REM 用 ssh-copy-id（如果存在）
where sshpass >nul 2>nul
if %errorlevel% neq 0 (
    echo ⚠️  未安装 sshpass，将手动输入密码 3 次
    type "%USERPROFILE%\.ssh\id_rsa.pub" | ssh -p %PORT% %USER%@%SERVER% "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
) else (
    type "%USERPROFILE%\.ssh\id_rsa.pub" | sshpass -p %SSHPASS% ssh -p %PORT% %USER%@%SERVER% "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
)

echo [3/3] 测试免密登录...
ssh -p %PORT% %USER%@%SERVER% "echo '✅ 免密登录配置成功！'"

echo.
echo ========================================
echo   ✅ 完成！以后 deploy-frontend.bat
echo      不再需要输入密码
echo ========================================
pause
