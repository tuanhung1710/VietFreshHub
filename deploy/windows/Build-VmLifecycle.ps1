$ErrorActionPreference='Stop'
$compiler=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) { throw '.NET Framework C# compiler was not found.' }
$source=Join-Path $PSScriptRoot 'VietFreshVmLifecycle.cs'
$binary=Join-Path $PSScriptRoot 'VietFreshVmLifecycle.exe'
& $compiler /nologo /target:exe ('/out:' + $binary) $source
if ($LASTEXITCODE -ne 0) { throw 'Windows service compilation failed.' }
Get-FileHash -LiteralPath $binary -Algorithm SHA256
