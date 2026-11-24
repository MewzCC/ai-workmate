param(
    [switch]$EnableLiveModel,
    [ValidatePattern('^[a-zA-Z0-9-]+$')][string]$Label = 'run',
    [ValidateRange(1, 10)][int]$Repeats = 3,
    [switch]$Selection,
    [switch]$EdgeCases,
    [string]$EnvFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $EnableLiveModel) { throw 'Pass -EnableLiveModel to authorize real model requests.' }
if ($EdgeCases -and -not $Selection) { throw '-EdgeCases requires -Selection.' }
$repositoryPath = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $repositoryPath '.env' }
$names = @('AI_API_KEY', 'AI_BASE_URL', 'AI_MODEL')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    # Configuration is data only. Do not print, execute, or copy credentials into reports.
    foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile).Path)) {
        if ($line -match '^\s*(AI_API_KEY|AI_BASE_URL|AI_MODEL)\s*=(.*)$') {
            if (-not [Environment]::GetEnvironmentVariable($matches[1], 'Process')) {
                [Environment]::SetEnvironmentVariable($matches[1], $matches[2].Trim().Trim([char]34).Trim([char]39), 'Process')
            }
        }
    }
    foreach ($name in $names) {
        if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) { throw "Missing server setting: $name" }
    }
    Push-Location (Join-Path $repositoryPath 'backend')
    try {
        New-Item -ItemType Directory -Path target -Force | Out-Null
        $testClass = if ($Selection) { 'ToolSelectionBenchmarkTest' } else { 'PlannerLatencyBenchmarkTest' }
        $prefix = if ($Selection) { 'tool-selection' } else { 'planner-benchmark' }
        $logPath = "target/$prefix-$Label.log"
        & mvn "-Dtest=$testClass" '-Dagent.test.live-model=true' "-Dbenchmark.label=$Label" "-Dbenchmark.repeats=$Repeats" "-Dbenchmark.edge-cases=$($EdgeCases.IsPresent.ToString().ToLowerInvariant())" test *> $logPath
        $result = $LASTEXITCODE
        Select-String -LiteralPath $logPath -Pattern '^Planner benchmark|^Selection benchmark|^\[INFO\] BUILD |^\[INFO\] Tests run:' |
            ForEach-Object { Write-Output $_.Line }
        if ($result -ne 0) { throw "Benchmark failed; inspect the local $logPath" }
    } finally { Pop-Location }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
