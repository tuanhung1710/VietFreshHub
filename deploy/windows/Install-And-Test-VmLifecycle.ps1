#requires -RunAsAdministrator
param([string]$VmxPath = 'E:\VMs\VietFresh-Server\VietFresh-Server.vmx')
$ErrorActionPreference = 'Stop'
$serviceName = 'VietFreshVmLifecycle'
$vmrun = 'C:\Program Files\VMware\VMware Workstation\vmrun.exe'
$stateDirectory = Join-Path $env:ProgramData $serviceName
$sourceBinary = Join-Path $PSScriptRoot 'VietFreshVmLifecycle.exe'
if (-not (Test-Path -LiteralPath $sourceBinary)) { & (Join-Path $PSScriptRoot 'Build-VmLifecycle.ps1') }
if (-not (Get-Service -Name $serviceName -ErrorAction SilentlyContinue)) {
    & (Join-Path $PSScriptRoot 'Install-VmLifecycle.ps1') -VmxPath $VmxPath
}
$expectedBinaryPath = '"' + (Join-Path $stateDirectory 'VietFreshVmLifecycle.exe') + '"'
$installed = Get-CimInstance Win32_Service -Filter "Name='$serviceName'"
if ($installed.PathName -ne $expectedBinaryPath) { throw 'Existing service points to an unexpected binary; no VM action was taken.' }
$installedBinary = Join-Path $stateDirectory 'VietFreshVmLifecycle.exe'
if ((Get-FileHash -LiteralPath $sourceBinary).Hash -ne (Get-FileHash -LiteralPath $installedBinary).Hash) {
    if ((Get-Service -Name $serviceName).Status -ne 'Stopped') {
        throw 'The existing service must be stopped before replacing its binary.'
    }
    Write-Host 'Updating the stopped VietFresh VM lifecycle service...'
    Copy-Item -LiteralPath $sourceBinary -Destination $installedBinary -Force
    if ((Get-FileHash -LiteralPath $sourceBinary).Hash -ne (Get-FileHash -LiteralPath $installedBinary).Hash) {
        throw 'Service binary verification failed after the update.'
    }
}
$VmxPath = [System.IO.Path]::GetFullPath($VmxPath)
if ((Get-Content -LiteralPath (Join-Path $stateDirectory 'vmx-path.txt') -Raw).Trim() -ne $VmxPath) {
    throw 'The installed service targets a different VM; no VM action was taken.'
}
function Test-VMRunning {
    $running = @(& $vmrun -T ws list)
    if ($LASTEXITCODE -ne 0) { throw 'VMware could not list running VMs.' }
    return ($running -icontains $VmxPath)
}
function Wait-VMState([bool]$Running) {
    $deadline = (Get-Date).AddSeconds(300)
    do {
        if ((Test-VMRunning) -eq $Running) { return }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "VM did not reach running=$Running within five minutes."
}
function Wait-Application {
    $deadline = (Get-Date).AddSeconds(300)
    do {
        try {
            $ip = (& $vmrun -T ws getGuestIPAddress $VmxPath 2>$null | Out-String).Trim()
            $address = $null
            if ($LASTEXITCODE -eq 0 -and [System.Net.IPAddress]::TryParse($ip,[ref]$address) -and $address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork) {
                $url = 'http://' + $ip + ':8088'
                $response = Invoke-WebRequest -Uri ($url + '/healthz') -UseBasicParsing -TimeoutSec 5
                $content = $response.Content
                if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
                if (($content | ConvertFrom-Json).status -eq 'UP') { return $url }
            }
        } catch { }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw 'VietFreshHub did not become ready within five minutes.'
}
try {
    Write-Host 'Starting the VM lifecycle service and checking VietFreshHub...'
    Start-Service -Name $serviceName
    (Get-Service -Name $serviceName).WaitForStatus('Running',[TimeSpan]::FromMinutes(2))
    Wait-VMState $true
    $initialUrl = Wait-Application
    Write-Host ('Initial readiness passed: ' + $initialUrl)
    Write-Host 'Testing a safe VM shutdown. Windows will remain running.'
    (Get-Service -Name $serviceName).Stop()
    (Get-Service -Name $serviceName).WaitForStatus('Stopped',[TimeSpan]::FromMinutes(5))
    Wait-VMState $false
    Write-Host 'VM stopped. Starting it again and waiting for application recovery...'
    Start-Service -Name $serviceName
    (Get-Service -Name $serviceName).WaitForStatus('Running',[TimeSpan]::FromMinutes(2))
    Wait-VMState $true
    $applicationUrl = Wait-Application
    [pscustomobject]@{
        VerifiedAtUtc = [DateTime]::UtcNow.ToString('O')
        Service = $serviceName
        Vmx = $VmxPath
        ApplicationUrl = $applicationUrl
        VmStopStartPassed = $true
        Readiness = 'UP'
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stateDirectory 'verification.json') -Encoding UTF8
    Write-Host ('PASS: VM service stop/start and application recovery. ' + $applicationUrl)
} catch {
    $failure = $_
    Write-Warning ('Lifecycle verification failed: ' + $failure.Exception.Message)
    try {
        if (-not (Test-VMRunning)) {
            Write-Host 'Restoring the Ubuntu VM with VMware; no hard power-off is used.'
            & $vmrun -T ws start $VmxPath nogui
        }
    } catch { Write-Warning $_.Exception.Message }
    $log = Join-Path $stateDirectory 'lifecycle.log'
    if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 30 | Write-Host }
    throw $failure
}
