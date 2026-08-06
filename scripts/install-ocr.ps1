[CmdletBinding()]
param(
    [string]$InstallDir,
    [switch]$Force,
    [switch]$StartAfterInstall
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $projectRoot "docker\ocr-service"
$defaultInstallDir = Join-Path $projectRoot "deploy\ocr-service"
$pathConfigFile = Join-Path $projectRoot "deploy\.ocr-install-path"

# 捕获安装阶段的所有终止错误，保留窗口供用户查看，不允许错误信息闪退。
trap {
    Write-Host ""
    Write-Host "[OCR 安装失败] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "请根据上方提示修复环境后重新执行 start.bat --install-ocr。" -ForegroundColor Yellow
    Read-Host "按回车键关闭当前安装窗口"
    exit 1
}

function Write-Step([string]$Message) {
    Write-Host "[OCR] $Message" -ForegroundColor Cyan
}

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "命令执行失败 ($LASTEXITCODE)：$Program $($Arguments -join ' ')"
    }
}

function Write-PythonInstallHelp {
    Write-Host ""
    Write-Host "[环境问题] OCR 本地安装需要 64 位 Python 3.10 或 3.11。" -ForegroundColor Yellow
    Write-Host "推荐在新的 PowerShell 窗口中执行：" -ForegroundColor Yellow
    Write-Host "  winget install -e --id Python.Python.3.11" -ForegroundColor White
    Write-Host "也可以自行下载：https://www.python.org/downloads/windows/" -ForegroundColor Yellow
    Write-Host "安装时请勾选 Add python.exe to PATH，完成后重新执行安装。" -ForegroundColor Yellow
    Write-Host ""
}

function Assert-PythonRuntime([string]$PythonPath) {
    & $PythonPath -c "import struct,sys; ok=(3,10)<=sys.version_info[:2]<=(3,11) and struct.calcsize('P')*8==64; print('Python', sys.version.split()[0], struct.calcsize('P')*8, 'bit'); raise SystemExit(0 if ok else 1)"
    if ($LASTEXITCODE -ne 0) {
        Write-PythonInstallHelp
        throw "Python 版本或系统架构不受支持。"
    }
}

if (-not (Test-Path (Join-Path $sourceDir "app.py")) -or
    -not (Test-Path (Join-Path $sourceDir "requirements.txt"))) {
    throw "OCR 安装源不完整：$sourceDir"
}

$systemPython = Get-Command python -ErrorAction SilentlyContinue
if (-not $systemPython) {
    Write-PythonInstallHelp
    throw "PATH 中未找到 Python。"
}
Assert-PythonRuntime $systemPython.Source

if ([string]::IsNullOrWhiteSpace($InstallDir)) {
    $answer = Read-Host "请输入 OCR 安装目录（直接回车使用 $defaultInstallDir）"
    $InstallDir = if ([string]::IsNullOrWhiteSpace($answer)) { $defaultInstallDir } else { $answer.Trim('"') }
}
if (-not [IO.Path]::IsPathRooted($InstallDir)) {
    $InstallDir = Join-Path $projectRoot $InstallDir
}
$InstallDir = [IO.Path]::GetFullPath($InstallDir)
if ($InstallDir.Contains("!")) {
    throw "安装目录不能包含感叹号 (!)。"
}

Write-Step "安装目录：$InstallDir"
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $InstallDir "models") | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pathConfigFile) | Out-Null

Copy-Item -LiteralPath (Join-Path $sourceDir "app.py") -Destination $InstallDir -Force
Copy-Item -LiteralPath (Join-Path $sourceDir "requirements.txt") -Destination $InstallDir -Force

$venvDir = Join-Path $InstallDir ".venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"
if ($Force -and (Test-Path $venvDir)) {
    Write-Step "正在移除旧虚拟环境"
    Remove-Item -LiteralPath $venvDir -Recurse -Force
}
if (-not (Test-Path $venvPython)) {
    Write-Step "正在创建 Python 虚拟环境"
    Invoke-Checked $systemPython.Source @("-m", "venv", $venvDir)
}

Assert-PythonRuntime $venvPython
Write-Step "正在安装 OCR 依赖，首次安装可能需要数分钟"
Invoke-Checked $venvPython @("-m", "pip", "install", "--disable-pip-version-check", "--upgrade", "pip")
Invoke-Checked $venvPython @("-m", "pip", "install", "--disable-pip-version-check", "-r", (Join-Path $InstallDir "requirements.txt"))
Invoke-Checked $venvPython @("-c", "import fastapi, fitz, paddle, paddleocr, PIL, uvicorn; print('OCR dependencies OK')")

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($pathConfigFile, $InstallDir, $utf8NoBom)
Write-Host "[OCR] 安装完成：$InstallDir" -ForegroundColor Green
Write-Host "[OCR] 首次识别时，模型将下载到 $InstallDir\models。" -ForegroundColor Green

if ($StartAfterInstall) {
    Write-Step "正在启动 OCR 服务：http://localhost:8686"
    $env:OCR_MODEL_DIR = Join-Path $InstallDir "models"
    Set-Location $InstallDir
    Invoke-Checked $venvPython @("-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8686")
}
