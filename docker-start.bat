@echo off
setlocal EnableDelayedExpansion

REM ============================================================
REM  AI WorkMate Docker One-Click Start Script
REM  - Uses .env.docker as the env file source
REM  - Rebuilds images if needed and starts all containers
REM  - Backend Spring Boot : http://localhost:8080
REM  - Marketing Site      : http://localhost:3000
REM  - OA Workbench        : http://localhost:3001
REM ============================================================

title AI WorkMate Docker Launcher

set "ROOT=%~dp0"
cd /d "%ROOT%"

echo.
echo ============================================================
echo   AI WorkMate Docker Launcher
echo ============================================================
echo   Root      : %ROOT%
echo   Env File  : .env.docker
echo ============================================================
echo.

REM ---------- Check .env.docker exists ----------
if not exist ".env.docker" (
    echo [ERROR] .env.docker not found in %ROOT%
    echo         Please copy .env.docker.example to .env.docker and fill in real values:
    echo           copy .env.docker.example .env.docker
    pause
    exit /b 1
)
echo [CHECK] .env.docker found.

REM ---------- Check docker command ----------
where docker >nul 2>nul
if errorlevel 1 (
    echo [ERROR] docker not found. Please install Docker Desktop first.
    pause
    exit /b 1
)
echo [CHECK] docker command available.

REM ---------- Check docker compose ----------
docker compose version >nul 2>nul
if errorlevel 1 (
    echo [ERROR] docker compose not available. Please use Docker Desktop with Compose v2.
    pause
    exit /b 1
)
echo [CHECK] docker compose available.

REM ---------- Check Docker daemon ----------
docker info >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Docker daemon not running. Please start Docker Desktop first.
    pause
    exit /b 1
)
echo [CHECK] Docker daemon is running.
echo.

REM ---------- Rebuild and start ----------
echo ============================================================
echo   [BUILD] Building images and starting containers...
echo   Using env file: .env.docker
echo   - Images will be rebuilt if Dockerfile or build context changed
echo   - Containers will be recreated to pick up latest .env.docker
echo ============================================================
echo.

docker compose --env-file .env.docker up -d --build
if errorlevel 1 (
    echo.
    echo [ERROR] docker compose up failed. See logs above.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   [DONE] All services started
echo ============================================================
echo   Backend : http://localhost:8080
echo   Main    : http://localhost:3000
echo   OA      : http://localhost:3001/oa
echo   MinIO   : http://localhost:9001  (console)
echo   OCR     : ocr-service container (internal :8686, no public port)
echo ============================================================
echo.
echo   Useful commands:
echo     View status : docker compose --env-file .env.docker ps
echo     View logs   : docker compose --env-file .env.docker logs -f
echo     Stop all    : docker compose --env-file .env.docker down
echo ============================================================
echo.
echo This window will close in 8 seconds...
timeout /t 8 /nobreak > nul
exit /b 0
