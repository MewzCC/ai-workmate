@echo off
chcp 65001 >nul
echo ========================================
echo   AI WorkMate 前端打包
echo ========================================
echo.

cd /d "%~dp0"

echo [1/2] 构建项目...
cd frontend
call npm run build
if %errorlevel% neq 0 (
    echo ❌ 构建失败！
    pause
    exit /b 1
)
cd ..

echo [2/2] 打包 zip...
if exist "deploy\ai-workmate-frontend.zip" del /q "deploy\ai-workmate-frontend.zip"
powershell -NoProfile -Command "Compress-Archive -Path frontend\.next, frontend\node_modules, frontend\package.json, frontend\package-lock.json, frontend\next.config.js -DestinationPath deploy\ai-workmate-frontend.zip -Force"

echo.
echo ========================================
echo   ✅ 打包完成（含 node_modules，免安装）
echo   产物: deploy\ai-workmate-frontend.zip
echo ========================================
echo.
pause
