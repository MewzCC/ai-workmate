@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo   AI WorkMate 一键打包双前端
echo ========================================
echo.

cd /d "%~dp0"

echo [1/5] 打包官网 fronted-main...
cd fronted-main
call npm install
if %errorlevel% neq 0 exit /b %errorlevel%
call npm run build
if %errorlevel% neq 0 exit /b %errorlevel%
cd ..

echo.
echo [2/5] 打包 OA fonted-oa...
cd fonted-oa
call npm install
if %errorlevel% neq 0 exit /b %errorlevel%
call npm run build
if %errorlevel% neq 0 exit /b %errorlevel%
cd ..

echo.
echo [3/5] 清理旧产物...
if not exist deploy mkdir deploy
if exist deploy\fronted-main.zip del /q deploy\fronted-main.zip
if exist deploy\fonted-oa.zip del /q deploy\fonted-oa.zip

echo.
echo [4/5] 生成官网 zip...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path 'fronted-main\.next','fronted-main\public','fronted-main\package.json','fronted-main\package-lock.json','fronted-main\next.config.js' -DestinationPath 'deploy\fronted-main.zip' -Force"
if %errorlevel% neq 0 exit /b %errorlevel%

echo.
echo [5/5] 生成 OA zip...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path 'fonted-oa\.next','fonted-oa\public','fonted-oa\package.json','fonted-oa\package-lock.json','fonted-oa\next.config.js' -DestinationPath 'deploy\fonted-oa.zip' -Force"
if %errorlevel% neq 0 exit /b %errorlevel%

echo.
echo ========================================
echo   打包完成
echo   deploy\fronted-main.zip
echo   deploy\fonted-oa.zip
echo ========================================
echo.
pause
