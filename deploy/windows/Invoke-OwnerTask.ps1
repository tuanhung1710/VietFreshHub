$ErrorActionPreference='Stop'
$taskName='VietFreshVmStart'
$taskPath='\'
$beforeRun=(Get-ScheduledTaskInfo -TaskName $taskName -TaskPath $taskPath).LastRunTime
$alreadyRunning=(Get-ScheduledTask -TaskName $taskName -TaskPath $taskPath).State.ToString() -in @('Running','Queued')
if (-not $alreadyRunning) { Start-ScheduledTask -TaskName $taskName -TaskPath $taskPath }
$deadline=(Get-Date).AddSeconds(130)
do {
    Start-Sleep -Seconds 1
    $state=(Get-ScheduledTask -TaskName $taskName -TaskPath $taskPath).State.ToString()
    $info=Get-ScheduledTaskInfo -TaskName $taskName -TaskPath $taskPath
    if (($alreadyRunning -or $info.LastRunTime -gt $beforeRun) -and $state -notin @('Running','Queued')) {
        if ($info.LastTaskResult -ne 0) { throw ('The owner-account VM startup task failed. Task Scheduler result: 0x{0:X8}' -f [uint32]$info.LastTaskResult) }
        Write-Output 'Owner-account VM startup task completed successfully.'
        return
    }
} while ((Get-Date) -lt $deadline)
throw 'The owner-account startup task did not finish within its allowed time. Inspect Task Scheduler history.'
