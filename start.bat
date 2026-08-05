@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul

REM ============================================================
REM  AI WorkMate 一键启动脚本（Windows Terminal 标签页模式）
REM  - 后端 Spring Boot : http://localhost:8080
REM  - 营销官网          : http://localhost:3000
REM  - OA 工作台         : http://localhost:3001
REM  - OCR 识别服务      : http://localhost:8686（可选，首次运行自动安装）
REM  - 所有服务在同一个窗口的独立标签页中运行
REM  - 需要 Windows Terminal（wt.exe）。Win11 自带；
REM    Win10 请从 Microsoft Store 安装：https://aka.ms/terminal
REM ============================================================

title AI WorkMate 一键启动

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTED_MAIN=%ROOT%fronted-main"
set "FONTED_OA=%ROOT%fonted-oa"
set "OCR_SERVICE=%ROOT%deploy\ocr-service"

echo.
echo ============================================================
echo   AI WorkMate 一键启动（Windows Terminal 标签页）
echo ============================================================
echo   后端 : %BACKEND%
echo   官网 : %FRONTED_MAIN%
echo   OA   : %FONTED_OA%
echo   OCR  : %OCR_SERVICE%
echo ============================================================
echo.

REM ---------- 检查 Windows Terminal ----------
echo [检查] Windows Terminal...
where wt.exe >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Windows Terminal ^(wt.exe^)。
    echo        Win11 自带；Win10 请从 Microsoft Store 安装：
    echo        https://aka.ms/terminal
    echo.
    pause
    exit /b 1
)
echo [检查] 已检测到 Windows Terminal。
echo.

REM ---------- 检查端口占用并释放端口 ----------
echo [检查] 正在扫描服务端口...
call :release_port 8080 "后端"
if errorlevel 1 goto port_release_failed
call :release_port 3000 "营销官网"
if errorlevel 1 goto port_release_failed
call :release_port 3001 "OA 工作台"
if errorlevel 1 goto port_release_failed
echo [检查] 所有服务端口就绪。
echo.

REM ---------- 检查 Java 17 ----------
echo [检查] Java 环境...
java -version 2>nul | findstr /R "version \"17" >nul
if errorlevel 1 (
    echo [警告] 未检测到 Java 17，后端可能无法启动。
    echo        请确保 JAVA_HOME 指向 JDK 17 且 java 已在 PATH 中。
    java -version 2>&1
    echo.
)

REM ---------- 检查 Maven ----------
where mvn >nul 2>nul
if errorlevel 1 (
    echo [警告] 未找到 mvn，改用 mvnw.cmd。
    set "MVN_CMD=mvnw.cmd"
) else (
    set "MVN_CMD=mvn"
)

REM ---------- 检查 Node ----------
where npm >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 npm，请先安装 Node.js。
    pause
    exit /b 1
)

echo.
echo [启动] 正在打开 Windows Terminal 标签页...
echo.

REM ---------- 准备 OCR 服务（可选） ----------
set "OCR_STARTED=0"
where python >nul 2>nul
if errorlevel 1 goto ocr_wt_done
set "OCR_PY=%OCR_SERVICE%\.venv\Scripts\python.exe"
if not exist "%OCR_PY%" (
    echo [警告] 未找到 OCR 虚拟环境，首次运行需要安装依赖。
    choice /c YN /m "是否现在安装 OCR 服务（python venv + paddlepaddle，可能需要几分钟）?"
    if errorlevel 2 goto ocr_wt_done
    echo [安装] 正在创建 OCR 虚拟环境：%OCR_SERVICE%\.venv ...
    python -m venv "%OCR_SERVICE%\.venv"
    if errorlevel 1 goto ocr_wt_done
    echo [安装] 正在安装 OCR 依赖，请稍候 ...
    "%OCR_PY%" -m pip install -r "%OCR_SERVICE%\requirements.txt"
    if errorlevel 1 goto ocr_wt_done
)
call :release_port 8686 "OCR 服务"
set "OCR_STARTED=1"
:ocr_wt_done

REM ---------- 在同一个窗口中启动 wt 标签页 ----------
wt -d "%BACKEND%" --title "Backend (8080)" cmd /k "cd /d %BACKEND% && %MVN_CMD% spring-boot:run" ; new-tab -d "%FRONTED_MAIN%" --title "Main (3000)" cmd /k "cd /d %FRONTED_MAIN% && npm run dev" ; new-tab -d "%FONTED_OA%" --title "OA (3001)" cmd /k "cd /d %FONTED_OA% && npm run dev"

REM ---------- 将 OCR 标签页追加到同一个窗口 ----------
if "%OCR_STARTED%"=="1" (
    ping -n 2 127.0.0.1 >nul
    wt -w 0 new-tab -d "%OCR_SERVICE%" --title "OCR (8686)" cmd /k "cd /d %OCR_SERVICE% && .venv\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 8686"
)

echo ============================================================
echo   所有服务已在 Windows Terminal 标签页中启动
echo ============================================================
echo   后端 : http://localhost:8080  （标签页：Backend）
echo   官网 : http://localhost:3000  （标签页：Main）
echo   OA   : http://localhost:3001  （标签页：OA）
if "%OCR_STARTED%"=="1" echo   OCR  : http://localhost:8686  （标签页：OCR）
echo.
echo   关闭对应标签页即可停止服务。
echo   关闭整个窗口可停止全部服务。
echo ============================================================
echo.
echo 本窗口将在 5 秒后自动关闭...
timeout /t 5 /nobreak > nul
exit /b 0

REM ---------- 端口释放失败 ----------
:port_release_failed
echo.
echo [错误] 有端口无法释放，启动已取消。
echo        如进程无法被终止，请以管理员身份运行本脚本。
echo.
pause
exit /b 1

REM ---------- 函数：扫描并释放单个 TCP 端口 ----------
:release_port
REM  参数1：端口号  参数2：服务显示名称
set "PORT_NUMBER=%~1"
set "SERVICE_NAME=%~2"
set "PORT_OCCUPIED=0"

for /f "tokens=5" %%p in ('netstat -ano -p tcp ^| findstr /R /C:":%PORT_NUMBER% .*LISTENING"') do (
    set "PORT_OCCUPIED=1"
    set "PORT_PID=%%p"
    if not "!PORT_PID!"=="0" (
        echo [端口] %SERVICE_NAME% 端口 %PORT_NUMBER% 被 PID !PORT_PID! 占用，正在终止进程树...
        taskkill /PID !PORT_PID! /T /F >nul 2>&1
    )
)

if "%PORT_OCCUPIED%"=="0" (
    echo [端口] %SERVICE_NAME% 端口 %PORT_NUMBER% 可用。
    exit /b 0
)

timeout /t 1 /nobreak >nul
netstat -ano -p tcp | findstr /R /C:":%PORT_NUMBER% .*LISTENING" >nul
if not errorlevel 1 (
    echo [错误] %SERVICE_NAME% 端口 %PORT_NUMBER% 仍被占用。
    exit /b 1
)

echo [端口] %SERVICE_NAME% 端口 %PORT_NUMBER% 已释放。
exit /b 0
