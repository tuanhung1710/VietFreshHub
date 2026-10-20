param(
    [string]$SchemaPath='D:/Download/vietfresh_hub_script.sql',
    [string]$SeedPath='D:/Download/data.sql'
)
$ErrorActionPreference='Stop'
if([string]::IsNullOrWhiteSpace($env:TV3_TEST_SQL_USER) -or [string]::IsNullOrWhiteSpace($env:TV3_TEST_SQL_PASSWORD)) {
    throw 'Set TV3_TEST_SQL_USER and TV3_TEST_SQL_PASSWORD in this process; do not store credentials in this script.'
}
if(-not (Test-Path -LiteralPath "$env:JAVA_HOME/bin/java.exe")) { throw 'Set JAVA_HOME to the project Java 21 installation.' }
$taskWorkspace=Split-Path -Parent $PSScriptRoot
$taskOutput=Join-Path $taskWorkspace 'target/tv3-sqlserver'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskDatabase='VietFreshHub_TV3_Test_'+[guid]::NewGuid().ToString('N')
if((Get-FileHash -Algorithm SHA256 -LiteralPath $SchemaPath).Hash -ne 'C6EE34C228E714AD5F80992086B9BD13E6AA320E0EE727D0CDCBE1C2B8D3372D' -or
   (Get-FileHash -Algorithm SHA256 -LiteralPath $SeedPath).Hash -ne '0667C39824C613BA290F18B36AA06E7BEC14C64834FA2A15980F91A793652FE5') {
    throw 'SQL input differs from the reviewed version. Review the new scripts before updating these fingerprints.'
}
$taskSchema=Get-Content -LiteralPath $SchemaPath -Raw
$taskSeed=Get-Content -LiteralPath $SeedPath -Raw
foreach($taskSource in @($taskSchema,$taskSeed)) {
    if($taskSource -match '(?im)\b(DROP\s+DATABASE|ALTER\s+DATABASE|CREATE\s+LOGIN|ALTER\s+LOGIN|xp_cmdshell|sp_configure|BACKUP\s+DATABASE|RESTORE\s+DATABASE)\b') {
        throw 'Unexpected server-level command in input SQL; review the updated source before proceeding.'
    }
}
$taskSchema=$taskSchema.Replace('VietFresh_Hub',$taskDatabase)
$taskSeed=$taskSeed.Replace('VietFresh_Hub',$taskDatabase)
$taskSql="USE [master];`nGO`nIF DB_ID(N'$taskDatabase') IS NOT NULL THROW 59001,N'Test database already exists; refusing reuse.',1;`nGO`n"+
    $taskSchema+"`nUSE [$taskDatabase];`nGO`nEXEC sys.sp_addextendedproperty @name=N'TV3_TASK3_TEST',@value=N'$taskDatabase';`nGO`n"+$taskSeed
foreach($taskUse in [regex]::Matches($taskSql,'(?im)^\s*USE\s+\[([^\]]+)\]')) {
    if($taskUse.Groups[1].Value -notin @('master',$taskDatabase)) { throw 'Input SQL switches to an unexpected database.' }
}
$taskSqlPath=Join-Path $taskOutput 'create-test-database.sql'
[IO.File]::WriteAllText($taskSqlPath,$taskSql,[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $taskOutput 'db-name.txt'),$taskDatabase)
Get-FileHash -Algorithm SHA256 -LiteralPath $SchemaPath,$SeedPath | Select-Object Path,Hash |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'sources.sha256.json') -Encoding utf8
$taskPreviousPassword=$env:SQLCMDPASSWORD
$taskPreviousUrl=$env:TV3_TEST_JDBC_URL
Push-Location -LiteralPath $taskWorkspace
try {
    $env:SQLCMDPASSWORD=$env:TV3_TEST_SQL_PASSWORD
    & sqlcmd -S 'tcp:127.0.0.1,1433' -d master -U $env:TV3_TEST_SQL_USER -C -l 5 -t 60 -f 65001 -b -i $taskSqlPath
    if($LASTEXITCODE -ne 0) { throw "Could not initialize isolated test database $taskDatabase. Tests were not started." }
    $env:TV3_TEST_JDBC_URL="jdbc:sqlserver://127.0.0.1:1433;databaseName=$taskDatabase;encrypt=true;trustServerCertificate=true;loginTimeout=5;lockTimeout=60000"
    & .\mvnw.cmd -o '-Dtest=CartServiceImplTest,CustomerCartIdentityTest,CheckoutDraftStoreTest,CheckoutServiceImplTest,CheckoutShippingQuoteServiceTest,CheckoutFailureRecoveryTest,CheckoutSqlServerIntegrationTest,CustomerOrderCancellationTest,CustomerOrderTrackingTest,CustomerOrderHistoryTest,CustomerOrderReorderTest,CustomerOrderHistoryControllerTest,CustomerHttpSecurityTest,CustomerPaymentHistoryTest,CustomerPaymentHistoryControllerTest' test
    if($LASTEXITCODE -ne 0) { throw "TV3 tests failed; inspect target/surefire-reports. Isolated DB retained: $taskDatabase" }
    Write-Output "TV3 tests passed. Isolated DB retained for review: $taskDatabase"
} finally {
    $env:SQLCMDPASSWORD=$taskPreviousPassword
    $env:TV3_TEST_JDBC_URL=$taskPreviousUrl
    Pop-Location
}
