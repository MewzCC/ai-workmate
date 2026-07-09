@echo off
chcp 65001 >nul
echo ========================================
echo   AI WorkMate 一键部署前端
echo ========================================
echo.

setlocal

set "SERVER=8.138.46.183"
set "PORT=22"
set "USER=root"
set "DEPLOY_PATH=/www/wwwroot/project/ai-workmeta"

REM ============= 配置区结束 =============

cd /d "%~dp0"

where scp >nul 2>nul
if %errorlevel% neq 0 (
    echo ❌ 未找到 scp，请安装 OpenSSH 客户端
    echo 设置 → 应用 → 可选功能 → OpenSSH 客户端
    pause
    exit /b 1
)

REM 验证免密登录是否配置
echo [0/4] 测试服务器连接...
ssh -p %PORT% %USER%@%SERVER% "echo '连接成功'" >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 无法免密登录服务器！
    echo    请先双击运行 setup-ssh-key.bat 配置 SSH 公钥
    pause
    exit /b 1
)

echo [1/4] 构建项目...
cd frontend
call npm run build
if %errorlevel% neq 0 (
    echo ❌ 构建失败！
    pause
    exit /b 1
)
cd ..

echo [2/4] 打包 zip...
if exist "deploy\ai-workmate-frontend.zip" del /q "deploy\ai-workmate-frontend.zip"
powershell -NoProfile -Command "Compress-Archive -Path frontend\.next, frontend\node_modules, frontend\package.json, frontend\package-lock.json, frontend\next.config.js -DestinationPath deploy\ai-workmate-frontend.zip -Force"

echo [3/4] 上传到服务器 %USER%@%SERVER% ...
set BACKUP_NAME=ai-workmate-frontend.bak-%date:~0,4%%date:~5,2%%date:~8,2%-%time:~0,2%%time:~3,2%%time:~6,2%
set BACKUP_NAME=%BACKUP_NAME: =0%

ssh -p %PORT% %USER%@%SERVER% "mkdir -p %DEPLOY_PATH% && cp -r %DEPLOY_PATH% /www/wwwroot/project/%BACKUP_NAME% 2>/dev/null; rm -rf %DEPLOY_PATH% && mkdir -p %DEPLOY_PATH%"

scp -P %PORT% "deploy\ai-workmate-frontend.zip" %USER%@%SERVER%:/www/wwwroot/project/ai-workmate-frontend.zip
if %errorlevel% neq 0 (
    echo ❌ 上传失败！
    pause
    exit /b 1
)

echo [4/4] 在服务器解压并重启服务...
ssh -p %PORT% %USER%@%SERVER% "cd %DEPLOY_PATH% && unzip -o -q ../ai-workmate-frontend.zip -d . && rm -f ../ai-workmate-frontend.zip && (pm2 delete ai-workmate-frontend 2>/dev/null || true) && pm2 start npm --name ai-workmate-frontend -- start && pm2 save"

echo.
echo ========================================
echo   ✅ 部署完成！
echo   访问: http://%SERVER%:3000
echo ========================================
echo.
pause
