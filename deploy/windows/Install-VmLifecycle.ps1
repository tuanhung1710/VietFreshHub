param([string]$VmxPath = 'E:\VMs\VietFresh-Server\VietFresh-Server.vmx')
$ErrorActionPreference = 'Stop'
if (-not [System.IO.Path]::IsPathFullyQualified($VmxPath) -or -not (Test-Path -LiteralPath $VmxPath)) { throw 'An existing absolute VMX path is required.' }
$serviceName='VietFreshVmLifecycle'
$installDirectory=Join-Path $env:ProgramData $serviceName
$sourceBinary=Join-Path $PSScriptRoot 'VietFreshVmLifecycle.exe'
if (-not (Test-Path -LiteralPath $sourceBinary)) { throw 'Compile VietFreshVmLifecycle.cs before installing.' }
if (Get-Service -Name $serviceName -ErrorAction SilentlyContinue) { throw 'Service already exists; inspect its status before upgrading.' }
New-Item -ItemType Directory -Path $installDirectory -Force | Out-Null
Copy-Item -LiteralPath $sourceBinary -Destination (Join-Path $installDirectory 'VietFreshVmLifecycle.exe')
[System.IO.File]::WriteAllText((Join-Path $installDirectory 'vmx-path.txt'),[System.IO.Path]::GetFullPath($VmxPath))
$userSid=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $installDirectory /inheritance:r /grant:r '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' ($userSid + ':(OI)(CI)RX')
if ($LASTEXITCODE -ne 0) { throw 'Could not secure the service installation directory.' }
$binaryPath='"' + (Join-Path $installDirectory 'VietFreshVmLifecycle.exe') + '"'
New-Service -Name $serviceName -BinaryPathName $binaryPath -DisplayName 'VietFresh Ubuntu VM lifecycle' -StartupType Automatic -DependsOn @('VMAuthdService','VMnetDHCP','VMware NAT Service') -Description 'Starts the existing Ubuntu VM on Windows startup and gracefully shuts it down before Windows stops.'
& (Join-Path $installDirectory 'VietFreshVmLifecycle.exe') --configure-preshutdown
if ($LASTEXITCODE -ne 0) { throw 'Could not configure safe Windows preshutdown handling.' }
& sc.exe failure $serviceName reset= 86400 actions= restart/30000/restart/60000
Write-Output 'Installed. Start and stop this service to validate the VM lifecycle before rebooting Windows.'
