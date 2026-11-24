$ErrorActionPreference='Stop'
$compiler=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$testBinary=Join-Path $env:TEMP ('VietFreshVmLifecycle-tests-' + [guid]::NewGuid().ToString('N') + '.exe')
try {
    & $compiler /nologo /target:exe /main:VmLifecycleTests ('/out:' + $testBinary) (Join-Path $PSScriptRoot 'VietFreshVmLifecycle.cs') (Join-Path $PSScriptRoot 'VietFreshVmLifecycle.Tests.cs')
    if ($LASTEXITCODE -ne 0) { throw 'Service test compilation failed.' }
    & $testBinary
    if ($LASTEXITCODE -ne 0) { throw 'Service startup retry tests failed.' }
} finally {
    if (Test-Path -LiteralPath $testBinary) { Remove-Item -LiteralPath $testBinary }
}
