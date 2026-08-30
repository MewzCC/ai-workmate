param(
    [Parameter(Mandatory = $true)]
    [string]$DatabaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$DatabaseUsername,
    [Parameter(Mandatory = $true)]
    [string]$DatabasePassword
)

$ErrorActionPreference = 'Stop'

if (-not $DatabaseUrl.StartsWith('jdbc:postgresql:')) {
    throw 'DatabaseUrl 必须是 jdbc:postgresql: 开头的真实 PostgreSQL 地址。'
}

$backendPath = Resolve-Path (Join-Path $PSScriptRoot '..\backend')
Push-Location $backendPath
try {
    & mvn '-Dtest=P1PostgresMigrationIT' `
        "-Dp1.test.db.url=$DatabaseUrl" `
        "-Dp1.test.db.username=$DatabaseUsername" `
        "-Dp1.test.db.password=$DatabasePassword" `
        test
    if ($LASTEXITCODE -ne 0) {
        throw "P1 PostgreSQL 回归失败，Maven 退出码：$LASTEXITCODE"
    }
} finally {
    Pop-Location
}
