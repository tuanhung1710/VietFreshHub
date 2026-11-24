#requires -RunAsAdministrator
param([string]$VmxPath = 'E:\VMs\VietFresh-Server\VietFresh-Server.vmx')
$ErrorActionPreference = 'Stop'
$serviceName = 'VietFreshVmLifecycle'
$vmrun = 'C:\Program Files\VMware\VMware Workstation\vmrun.exe'
$stateDirectory = Join-Path $env:ProgramData $serviceName
$sourceBinary = Join-Path $PSScriptRoot 'VietFreshVmLifecycle.exe'
$taskName = 'VietFreshVmStart'
if (-not (Test-Path -LiteralPath $sourceBinary)) { & (Join-Path $PSScriptRoot 'Build-VmLifecycle.ps1') }
if (-not (Get-Service -Name $serviceName -ErrorAction SilentlyContinue)) {
    & (Join-Path $PSScriptRoot 'Install-VmLifecycle.ps1') -VmxPath $VmxPath
}
$expectedBinaryPath = '"' + (Join-Path $stateDirectory 'VietFreshVmLifecycle.exe') + '"'
$installed = Get-CimInstance Win32_Service -Filter "Name='$serviceName'"
if ($installed.PathName -ne $expectedBinaryPath) { throw 'Existing service points to an unexpected binary; no VM action was taken.' }
$installedBinary = Join-Path $stateDirectory 'VietFreshVmLifecycle.exe'
$updateRequired = (Get-FileHash -LiteralPath $sourceBinary).Hash -ne (Get-FileHash -LiteralPath $installedBinary).Hash
if ($updateRequired -and (Get-Service -Name $serviceName).Status -ne 'Stopped') { throw 'The existing service must be stopped before replacing its binary.' }
$VmxPath = [System.IO.Path]::GetFullPath($VmxPath)
if ((Get-Content -LiteralPath (Join-Path $stateDirectory 'vmx-path.txt') -Raw).Trim() -ne $VmxPath) {
    throw 'The installed service targets a different VM; no VM action was taken.'
}
foreach ($file in @($VmxPath, (Join-Path ([IO.Path]::GetDirectoryName($VmxPath)) 'VietFresh-Server.vmdk'))) {
    if ((Get-Item -LiteralPath $file).Attributes -band [IO.FileAttributes]::Encrypted) { throw 'S4U startup requires local files without EFS encryption.' }
}
$taskArguments = '-T ws start "' + $VmxPath + '" nogui'
$existingTask = Get-ScheduledTask -TaskName $taskName -TaskPath '\' -ErrorAction SilentlyContinue
if ($existingTask -and (@($existingTask.Actions).Count -ne 1 -or $existingTask.Actions[0].Execute -ne $vmrun -or $existingTask.Actions[0].Arguments -ne $taskArguments)) {
    throw 'The task name is already used by another action. Review it before proceeding.'
}
$windowsSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$action = New-ScheduledTaskAction -Execute $vmrun -Argument $taskArguments -WorkingDirectory ([IO.Path]::GetDirectoryName($VmxPath))
$principal = New-ScheduledTaskPrincipal -UserId $windowsSid -LogonType S4U -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -ExecutionTimeLimit ([TimeSpan]::FromMinutes(2))
$task = New-ScheduledTask -Action $action -Principal $principal -Settings $settings -Description 'Starts only the existing VietFresh Ubuntu VM in its Windows owner account without storing a password. Invoked by the lifecycle service.'
Register-ScheduledTask -TaskName $taskName -TaskPath '\' -InputObject $task -Force | Out-Null
Write-Host 'Verifying that the owner-account S4U task can run before attempting any VM shutdown...'
& (Join-Path $PSScriptRoot 'Invoke-OwnerTask.ps1')
Write-Host 'Owner-account task preflight passed.'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Invoke-OwnerTask.ps1') -Destination (Join-Path $stateDirectory 'Invoke-OwnerTask.ps1') -Force
if ($updateRequired) {
    Write-Host 'Updating the stopped VietFresh VM lifecycle service...'
    Copy-Item -LiteralPath $sourceBinary -Destination $installedBinary -Force
    if ((Get-FileHash -LiteralPath $sourceBinary).Hash -ne (Get-FileHash -LiteralPath $installedBinary).Hash) { throw 'Service binary verification failed after the update.' }
}
$dependencyChange = Invoke-CimMethod -InputObject $installed -MethodName Change -Arguments @{ServiceDependencies=[string[]]@('VMAuthdService','VMnetDHCP','VMware NAT Service','Schedule')}
if ($dependencyChange.ReturnValue -ne 0) { throw ('Could not configure Task Scheduler dependency. SCM result: ' + $dependencyChange.ReturnValue) }
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
        StartupTask = $taskName
        StartupTaskLogonType = 'S4U'
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
    Get-ScheduledTaskInfo -TaskName $taskName -TaskPath '\' | Select-Object LastRunTime,LastTaskResult | Format-List | Out-Host
    throw $failure
}
