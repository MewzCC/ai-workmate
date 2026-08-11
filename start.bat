@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ---------- Paths and state ----------
title AI WorkMate Launcher
set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTED_MAIN=%ROOT%fronted-main"
set "FONTED_OA=%ROOT%fonted-oa"
set "OCR_PATH_FILE=%ROOT%deploy\.ocr-install-path"
set "OCR_INSTALLER=%ROOT%scripts\install-ocr.ps1"
set "OCR_SOURCE=%ROOT%docker\ocr-service"
set "OCR_STARTED=0"
set "OCR_BACKEND_ENABLED=false"

if /I "%~1"=="--install-ocr" goto install_ocr_only

REM ---------- Required tools ----------
where wt.exe >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Windows Terminal was not found: https://aka.ms/terminal
    pause
    exit /b 1
)

call :release_port 8080 "Backend"
if errorlevel 1 goto port_release_failed
call :release_port 3000 "Main"
if errorlevel 1 goto port_release_failed
call :release_port 3001 "OA"
if errorlevel 1 goto port_release_failed

java -version 2>&1 | findstr /R /C:"version.*17\." >nul
if errorlevel 1 echo [WARN] Java 17 was not detected. Backend startup may fail.

where mvn >nul 2>nul
if errorlevel 1 (
    if exist "%BACKEND%\mvnw.cmd" (
        set "MVN_CMD=mvnw.cmd"
    ) else (
        echo [ERROR] Maven and backend\mvnw.cmd were not found.
        pause
        exit /b 1
    )
) else (
    set "MVN_CMD=mvn"
)

where npm >nul 2>nul
if errorlevel 1 (
    echo [ERROR] npm was not found.
    pause
    exit /b 1
)

REM ---------- Optional OCR ----------
echo [CHECK] Checking optional OCR installation. Please wait...
call :prepare_optional_ocr

REM ---------- Service tabs ----------
if "%OCR_STARTED%"=="1" (
    wt -w new new-tab -d "%BACKEND%" --title "Backend (8080)" cmd /k "cd /d %BACKEND% && set OCR_ENABLED=%OCR_BACKEND_ENABLED%&& %MVN_CMD% spring-boot:run" ; new-tab -d "%FRONTED_MAIN%" --title "Main (3000)" cmd /k "cd /d %FRONTED_MAIN% && npm run dev" ; new-tab -d "%FONTED_OA%" --title "OA (3001)" cmd /k "cd /d %FONTED_OA% && npm run dev" ; new-tab -d "%OCR_SERVICE%" --title "OCR (8686)" cmd /k "cd /d %OCR_SERVICE% && set OCR_MODEL_DIR=%OCR_SERVICE%\models&& .venv\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 8686"
) else (
    wt -w new new-tab -d "%BACKEND%" --title "Backend (8080)" cmd /k "cd /d %BACKEND% && set OCR_ENABLED=%OCR_BACKEND_ENABLED%&& %MVN_CMD% spring-boot:run" ; new-tab -d "%FRONTED_MAIN%" --title "Main (3000)" cmd /k "cd /d %FRONTED_MAIN% && npm run dev" ; new-tab -d "%FONTED_OA%" --title "OA (3001)" cmd /k "cd /d %FONTED_OA% && npm run dev"
)

pause
exit /b 0

REM ---------- Standalone OCR installer ----------
:install_ocr_only
if not exist "%OCR_INSTALLER%" (
    echo [ERROR] OCR installer was not found: %OCR_INSTALLER%
    pause
    exit /b 1
)
call :open_ocr_installer ""
pause
exit /b 0

REM ---------- Optional OCR state ----------
:prepare_optional_ocr
set "OCR_SERVICE="
if exist "%OCR_PATH_FILE%" set /p "OCR_SERVICE="<"%OCR_PATH_FILE%"

if not defined OCR_SERVICE goto offer_ocr_install
echo [OCR] Found installation path: !OCR_SERVICE!
set "OCR_PY=%OCR_SERVICE%\.venv\Scripts\python.exe"
if not exist "%OCR_PY%" goto offer_ocr_repair
if not exist "%OCR_SERVICE%\app.py" goto offer_ocr_repair
if not exist "%OCR_SERVICE%\requirements.txt" goto offer_ocr_repair
echo [OCR] Checking installation files...
fc /b "%OCR_SOURCE%\app.py" "%OCR_SERVICE%\app.py" >nul 2>nul
if errorlevel 1 goto offer_ocr_repair
fc /b "%OCR_SOURCE%\requirements.txt" "%OCR_SERVICE%\requirements.txt" >nul 2>nul
if errorlevel 1 goto offer_ocr_repair

echo [OCR] Checking Python dependencies. This may take a moment...
"%OCR_PY%" -c "import fastapi, fitz, paddle, paddleocr, PIL, uvicorn" >nul 2>nul
if errorlevel 1 goto offer_ocr_repair

echo [OCR] OCR installation verified. Checking port 8686...
call :release_port 8686 "OCR"
if errorlevel 1 (
    echo [WARN] OCR port 8686 could not be released. OCR will be skipped.
    exit /b 0
)
set "OCR_STARTED=1"
set "OCR_BACKEND_ENABLED=true"
echo [OCR] OCR is ready and will be started on port 8686.
exit /b 0

:offer_ocr_install
echo [OCR] No existing installation was found.
set "OCR_CHOICE=Y"
set /p "OCR_CHOICE=OCR is not installed. Press Enter to install, or N to skip: "
if /I "%OCR_CHOICE%"=="N" (
    echo [OCR] OCR was skipped.
    exit /b 0
)
echo [OCR] Opening the OCR installer in a new window...
call :open_ocr_installer ""
set "OCR_BACKEND_ENABLED=true"
exit /b 0

:offer_ocr_repair
echo [OCR] The existing OCR installation is incomplete.
set "OCR_CHOICE=Y"
set /p "OCR_CHOICE=OCR installation is incomplete. Press Enter to repair, or N to skip: "
if /I "%OCR_CHOICE%"=="N" (
    echo [OCR] OCR was skipped.
    exit /b 0
)
echo [OCR] Opening the OCR repair installer in a new window...
call :open_ocr_installer "%OCR_SERVICE%"
set "OCR_BACKEND_ENABLED=true"
exit /b 0

REM ---------- Non-blocking OCR installer window ----------
:open_ocr_installer
if not exist "%OCR_INSTALLER%" (
    echo [WARN] OCR installer was not found. OCR was skipped.
    exit /b 0
)
if "%~1"=="" (
    start "OCR Installer" /d "%ROOT%" powershell.exe -NoExit -NoProfile -ExecutionPolicy Bypass -File "%OCR_INSTALLER%" -StartAfterInstall
) else (
    start "OCR Repair" /d "%ROOT%" powershell.exe -NoExit -NoProfile -ExecutionPolicy Bypass -File "%OCR_INSTALLER%" -InstallDir "%~1" -StartAfterInstall
)
if errorlevel 1 (
    echo [ERROR] Could not open the OCR installer window.
    pause
)
exit /b 0

REM ---------- Required port failure ----------
:port_release_failed
echo [ERROR] A required service port could not be released.
pause
exit /b 1

REM ---------- Port helper ----------
:release_port
set "PORT_NUMBER=%~1"
set "PORT_OCCUPIED=0"

for /f "tokens=5" %%p in ('netstat -ano -p tcp ^| findstr /R /C:":%PORT_NUMBER% .*LISTENING"') do (
    set "PORT_OCCUPIED=1"
    set "PORT_PID=%%p"
    if not "!PORT_PID!"=="0" taskkill /PID !PORT_PID! /T /F >nul 2>&1
)

if "%PORT_OCCUPIED%"=="0" exit /b 0

timeout /t 1 /nobreak >nul
netstat -ano -p tcp | findstr /R /C:":%PORT_NUMBER% .*LISTENING" >nul
if not errorlevel 1 exit /b 1
exit /b 0
