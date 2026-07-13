param(
  [Parameter(Mandatory = $true)]
  [string]$SourceDir,

  [Parameter(Mandatory = $true)]
  [string]$DestinationZip
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$sourcePath = (Resolve-Path -LiteralPath $SourceDir).Path
$destinationPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($DestinationZip)
$destinationParent = Split-Path -Parent $destinationPath

if ($destinationParent -and -not (Test-Path -LiteralPath $destinationParent)) {
  New-Item -ItemType Directory -Force -Path $destinationParent | Out-Null
}

if (Test-Path -LiteralPath $destinationPath) {
  Remove-Item -LiteralPath $destinationPath -Force
}

$zip = [System.IO.Compression.ZipFile]::Open($destinationPath, [System.IO.Compression.ZipArchiveMode]::Create)

try {
  Get-ChildItem -LiteralPath $sourcePath -Recurse -File | ForEach-Object {
    $relativePath = $_.FullName.Substring($sourcePath.Length).TrimStart('\', '/')
    $entryName = $relativePath.Replace('\', '/')
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
      $zip,
      $_.FullName,
      $entryName,
      [System.IO.Compression.CompressionLevel]::Optimal
    ) | Out-Null
  }
}
finally {
  $zip.Dispose()
}
