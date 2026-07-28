@echo off
setlocal EnableDelayedExpansion

REM ============================================================
REM  AI WorkMate One-Click Launcher (Windows Terminal tabs)
REM  - Backend Spring Boot : http://localhost:8080
REM  - Marketing Site      : http://localhost:3000
REM  - OA Workbench        : http://localhost:3001
REM  - Three services run in separate tabs of the same window
REM  - Requires Windows Terminal (wt.exe). Win11 has it built-in;
REM    Win10 install from Microsoft Store: https://aka.ms/terminal
REM ============================================================

title AI WorkMate Launcher

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTED_MAIN=%ROOT%fronted-main"
set "FONTED_OA=%ROOT%fonted-oa"

echo.
echo ============================================================
echo   AI WorkMate Launcher (Windows Terminal tabs)
echo ============================================================
echo   Backend : %BACKEND%
echo   Main    : %FRONTED_MAIN%
echo   OA      : %FONTED_OA%
echo ============================================================
echo.

REM ---------- Check Windows Terminal ----------
echo [CHECK] Windows Terminal...
where wt.exe >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Windows Terminal ^(wt.exe^) not found.
    echo         Win11 has it built-in. Win10 install from Microsoft Store:
    echo         https://aka.ms/terminal
    echo         Or use start.bat for the classic multi-window mode.
    echo.
    pause
    exit /b 1
)
echo [CHECK] Windows Terminal detected.
echo.

REM ---------- Check and release ports ----------
echo [CHECK] Scanning service ports...
call :release_port 8080 "Backend"
if errorlevel 1 goto port_release_failed
call :release_port 3000 "Marketing Site"
if errorlevel 1 goto port_release_failed
call :release_port 3001 "OA Workbench"
if errorlevel 1 goto port_release_failed
echo [CHECK] All service ports are ready.
echo.

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
echo [START] Launching Windows Terminal with 3 tabs...
echo.

REM ---------- Launch wt with 3 tabs in one window ----------
wt -d "%BACKEND%" --title "Backend (8080)" cmd /k "cd /d %BACKEND% && %MVN_CMD% spring-boot:run" ; new-tab -d "%FRONTED_MAIN%" --title "Main (3000)" cmd /k "cd /d %FRONTED_MAIN% && npm run dev" ; new-tab -d "%FONTED_OA%" --title "OA (3001)" cmd /k "cd /d %FONTED_OA% && npm run dev"

echo ============================================================
echo   All services launched in Windows Terminal tabs
echo ============================================================
echo   Backend : http://localhost:8080  (tab: Backend)
echo   Main    : http://localhost:3000  (tab: Main)
echo   OA      : http://localhost:3001  (tab: OA)
echo.
echo   Close a tab to stop the corresponding service.
echo   Close the whole window to stop all services.
echo ============================================================
echo.
echo This launcher window will close in 5 seconds...
timeout /t 5 /nobreak > nul
exit /b 0

REM ---------- Port release failure ----------
:port_release_failed
echo.
echo [ERROR] A required port could not be released. Startup cancelled.
echo         Run this script as Administrator if the process cannot be terminated.
echo.
pause
exit /b 1

REM ---------- Function: Scan and release one TCP port ----------
:release_port
REM  arg1: port number  arg2: service display name
set "PORT_NUMBER=%~1"
set "SERVICE_NAME=%~2"
set "PORT_OCCUPIED=0"

for /f "tokens=5" %%p in ('netstat -ano -p tcp ^| findstr /R /C:":%PORT_NUMBER% .*LISTENING"') do (
    set "PORT_OCCUPIED=1"
    set "PORT_PID=%%p"
    if not "!PORT_PID!"=="0" (
        echo [PORT] %SERVICE_NAME% port %PORT_NUMBER% is occupied by PID !PORT_PID!. Stopping process tree...
        taskkill /PID !PORT_PID! /T /F >nul 2>&1
    )
)

if "%PORT_OCCUPIED%"=="0" (
    echo [PORT] %SERVICE_NAME% port %PORT_NUMBER% is available.
    exit /b 0
)

timeout /t 1 /nobreak >nul
netstat -ano -p tcp | findstr /R /C:":%PORT_NUMBER% .*LISTENING" >nul
if not errorlevel 1 (
    echo [ERROR] %SERVICE_NAME% port %PORT_NUMBER% is still occupied.
    exit /b 1
)

echo [PORT] %SERVICE_NAME% port %PORT_NUMBER% has been released.
exit /b 0
