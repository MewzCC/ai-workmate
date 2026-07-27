@echo off
setlocal EnableDelayedExpansion

REM ============================================================
REM  AI WorkMate One-Click Launcher
REM  - Backend Spring Boot : http://localhost:8080
REM  - Marketing Site      : http://localhost:3000
REM  - OA Workbench        : http://localhost:3001
REM  Close each window to stop the corresponding service
REM ============================================================

title AI WorkMate Launcher

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTED_MAIN=%ROOT%fronted-main"
set "FONTED_OA=%ROOT%fonted-oa"

echo.
echo ============================================================
echo   AI WorkMate Launcher
echo ============================================================
echo   Backend : %BACKEND%
echo   Main    : %FRONTED_MAIN%
echo   OA      : %FONTED_OA%
echo ============================================================
echo.

REM ---------- Port check ----------
call :check_port 8080 BACKEND_PORT
call :check_port 3000 MAIN_PORT
call :check_port 3001 OA_PORT

if "%BACKEND_PORT%"=="1" (
    echo [WARN] Port 8080 is already in use. Backend may be running.
    set /p confirm="Start backend anyway? (y/N): "
    if /I not "!confirm!"=="y" goto skip_backend
)
:skip_backend

if "%MAIN_PORT%"=="1" (
    echo [WARN] Port 3000 is already in use. Main site may be running.
    set /p confirm="Start main site anyway? (y/N): "
    if /I not "!confirm!"=="y" goto skip_main
)
:skip_main

if "%OA_PORT%"=="1" (
    echo [WARN] Port 3001 is already in use. OA may be running.
    set /p confirm="Start OA anyway? (y/N): "
    if /I not "!confirm!"=="y" goto skip_oa
)
:skip_oa

REM ---------- Check Java 17 ----------
echo [CHECK] Java environment...
java -version 2>nul | findstr /R "version \"17" >nul
if errorlevel 1 (
    echo [WARN] Java 17 not detected. Backend may fail to start.
    echo        Make sure JAVA_HOME points to JDK 17 and java is in PATH.
    java -version 2>&1
    echo.
)

REM ---------- Check Maven ----------
where mvn >nul 2>nul
if errorlevel 1 (
    echo [WARN] mvn not found. Trying mvnw.cmd instead.
    set "MVN_CMD=mvnw.cmd"
) else (
    set "MVN_CMD=mvn"
)

REM ---------- Check Node ----------
where npm >nul 2>nul
if errorlevel 1 (
    echo [ERROR] npm not found. Please install Node.js first.
    pause
    exit /b 1
)

echo.
echo [START] Backend Spring Boot ...
start "AI WorkMate Backend (8080)" cmd /k "cd /d %BACKEND% && %MVN_CMD% spring-boot:run"

echo [START] Marketing Site fronted-main ...
start "AI WorkMate Main (3000)" cmd /k "cd /d %FRONTED_MAIN% && npm run dev"

echo [START] OA Workbench fonted-oa ...
start "AI WorkMate OA (3001)" cmd /k "cd /d %FONTED_OA% && npm run dev"

echo.
echo ============================================================
echo   All start commands dispatched
echo ============================================================
echo   Backend : http://localhost:8080  (window: AI WorkMate Backend)
echo   Main    : http://localhost:3000  (window: AI WorkMate Main)
echo   OA      : http://localhost:3001  (window: AI WorkMate OA)
echo.
echo   First compile may take 30-60 seconds. Please be patient.
echo   Close the corresponding window to stop the service.
echo ============================================================
echo.
echo This window will close in 5 seconds...
timeout /t 5 /nobreak > nul
exit /b 0

REM ---------- Function: Port check ----------
:check_port
REM  arg1: port number  arg2: output var name
set port=%~1
set "found=0"
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%port% " ^| findstr "LISTENING"') do (
    set "found=1"
)
set "%~2=%found%"
goto :eof
