param(
    [switch]$EnableLiveModel,
    [switch]$Browser,
    [string]$EnvFile,
    [string]$DatabaseUrl = $env:AGENT_TEST_DB_URL,
    [string]$DatabaseUsername = $env:AGENT_TEST_DB_USERNAME,
    [string]$RedisExecutable = $env:AGENT_TEST_REDIS_BINARY
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $EnableLiveModel) { throw 'Explicit -EnableLiveModel is required for external model acceptance.' }
$repositoryPath = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $repositoryPath '.env' }
$names = @('AI_API_KEY', 'AI_BASE_URL', 'AI_MODEL', 'AGENT_TEST_DB_URL', 'AGENT_TEST_DB_USERNAME', 'AGENT_TEST_REDIS_BINARY')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$logPath = Join-Path ([IO.Path]::GetTempPath()) ('ai-workmate-live-model-' + [guid]::NewGuid().ToString('N') + '.log')

try {
    # Read only these server model settings as data, never execute or echo dotenv contents.
    if (Test-Path -LiteralPath $EnvFile -PathType Leaf) {
        foreach ($line in [IO.File]::ReadAllLines((Resolve-Path -LiteralPath $EnvFile).Path)) {
            $setting = [regex]::Match($line, '^\s*(AI_API_KEY|AI_BASE_URL|AI_MODEL)\s*=(.*)$')
            if (-not $setting.Success) { continue }
            $name = $setting.Groups[1].Value
            if ([Environment]::GetEnvironmentVariable($name, 'Process')) { continue }
            $value = $setting.Groups[2].Value.Trim()
            if ($value.Length -ge 2 -and (($value[0] -eq [char]34 -and $value[-1] -eq [char]34) -or
                    ($value[0] -eq [char]39 -and $value[-1] -eq [char]39))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
    foreach ($name in @('AI_API_KEY', 'AI_BASE_URL', 'AI_MODEL')) {
        if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) { throw "Required server setting is absent: $name" }
    }
    if (-not $DatabaseUrl -or -not $DatabaseUsername -or -not $RedisExecutable) {
        throw 'Explicit local test database and Redis executable settings are required.'
    }
    [Environment]::SetEnvironmentVariable('AGENT_TEST_DB_URL', $DatabaseUrl, 'Process')
    [Environment]::SetEnvironmentVariable('AGENT_TEST_DB_USERNAME', $DatabaseUsername, 'Process')
    [Environment]::SetEnvironmentVariable('AGENT_TEST_REDIS_BINARY', $RedisExecutable, 'Process')
    Push-Location (Join-Path $repositoryPath 'backend')
    try {
        # Credentials remain environment variables, never JVM command-line properties or output.
        $testClass = if ($Browser) { 'AgentLiveBrowserIT' } else { 'AgentLiveModelHttpIT' }
        & mvn "-Dtest=$testClass" '-Dagent.test.live-model=true' '-Dlogging.level.root=OFF' `
            '-Dlogging.level.com.aiworkmate=OFF' '-Dlogging.level.com.aiworkmate.agent=OFF' `
            '-Dlogging.level.org.springframework.ai=OFF' test *> $logPath
        $verificationExit = $LASTEXITCODE
    } finally { Pop-Location }
    Select-String -LiteralPath $logPath -Pattern '^Browser acceptance:|^Live model metadata:|^\[INFO\] Tests run:|^\[ERROR\] Tests run:|^\[INFO\] BUILD |^\[INFO\] Total time:' |
        ForEach-Object { Write-Output $_.Line }
    Write-Output "Private verification log: $logPath"
    if ($verificationExit -ne 0) { throw 'Live model acceptance failed; inspect the private local test report without sharing credentials.' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
