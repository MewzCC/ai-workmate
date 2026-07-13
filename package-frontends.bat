@echo off
setlocal

echo ========================================
echo   AI WorkMate standalone package
echo ========================================
echo.

cd /d "%~dp0"

echo [1/6] Build fronted-main...
cd fronted-main
call npm install
if errorlevel 1 exit /b 1
call npm run build
if errorlevel 1 exit /b 1
cd ..

echo.
echo [2/6] Build fonted-oa...
cd fonted-oa
call npm install
if errorlevel 1 exit /b 1
call npm run build
if errorlevel 1 exit /b 1
cd ..

echo.
echo [3/6] Prepare standalone folders...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item -Recurse -Force 'fronted-main\.next\standalone\.next\static','fronted-main\.next\standalone\public','fonted-oa\.next\standalone\.next\static','fonted-oa\.next\standalone\public' -ErrorAction SilentlyContinue; New-Item -ItemType Directory -Force 'fronted-main\.next\standalone\.next' | Out-Null; New-Item -ItemType Directory -Force 'fonted-oa\.next\standalone\.next' | Out-Null; Copy-Item -Recurse -Force 'fronted-main\.next\static' 'fronted-main\.next\standalone\.next\static'; Copy-Item -Recurse -Force 'fronted-main\public' 'fronted-main\.next\standalone\public'; Copy-Item -Force 'fronted-main\ecosystem.config.js' 'fronted-main\.next\standalone\ecosystem.config.js'; Copy-Item -Force 'fronted-main\start-pm2.sh' 'fronted-main\.next\standalone\start-pm2.sh'; Copy-Item -Force 'fronted-main\DEPLOY.md' 'fronted-main\.next\standalone\DEPLOY.md'; Copy-Item -Recurse -Force 'fonted-oa\.next\static' 'fonted-oa\.next\standalone\.next\static'; Copy-Item -Recurse -Force 'fonted-oa\public' 'fonted-oa\.next\standalone\public'; Copy-Item -Force 'fonted-oa\ecosystem.config.js' 'fonted-oa\.next\standalone\ecosystem.config.js'; Copy-Item -Force 'fonted-oa\start-pm2.sh' 'fonted-oa\.next\standalone\start-pm2.sh'; Copy-Item -Force 'fonted-oa\DEPLOY.md' 'fonted-oa\.next\standalone\DEPLOY.md'"
if errorlevel 1 exit /b 1

echo.
echo [4/6] Clean old packages...
if not exist deploy mkdir deploy
if exist deploy\fronted-main.zip del /q deploy\fronted-main.zip
if exist deploy\fonted-oa.zip del /q deploy\fonted-oa.zip
copy /y deploy-frontends-server.sh deploy\deploy-frontends-server.sh >nul

echo.
echo [5/6] Create fronted-main.zip...
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\create-standalone-zip.ps1 -SourceDir fronted-main\.next\standalone -DestinationZip deploy\fronted-main.zip
if errorlevel 1 exit /b 1

echo.
echo [6/6] Create fonted-oa.zip...
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\create-standalone-zip.ps1 -SourceDir fonted-oa\.next\standalone -DestinationZip deploy\fonted-oa.zip
if errorlevel 1 exit /b 1

echo.
echo ========================================
echo   Package completed
echo   deploy\fronted-main.zip
echo   deploy\fonted-oa.zip
echo   deploy\deploy-frontends-server.sh
echo.
echo   Server start commands:
echo   Upload deploy folder files to server, then run:
echo   bash deploy-frontends-server.sh
echo ========================================
echo.
pause
