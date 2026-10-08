param([int]$Port = 8081)
$ErrorActionPreference = 'Stop'
$taskWorkspace = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $taskWorkspace
$taskLocalDirectory = Join-Path $taskWorkspace '.document-analysis'
New-Item -ItemType Directory -Path $taskLocalDirectory -Force | Out-Null
$taskCredentialsFile = Join-Path $taskLocalDirectory 'demo-credentials.json'
if (-not (Test-Path -LiteralPath $taskCredentialsFile)) {
    $taskCredentials = @{
        password = ('Vfh!' + [guid]::NewGuid().ToString('N'))
        url = "http://localhost:$Port"
        accounts = @('customer.demo@vietfreshhub.example', 'empty.demo@vietfreshhub.example',
            'blocked.demo@vietfreshhub.example', 'manager-a.demo@vietfreshhub.example',
            'manager-b.demo@vietfreshhub.example', 'manager-closed.demo@vietfreshhub.example',
            'applicant.demo@vietfreshhub.example', 'admin.demo@vietfreshhub.example', 'delivery.demo@vietfreshhub.example')
    }
    $taskCredentials | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $taskCredentialsFile
}
$taskCredentials = Get-Content -Raw -LiteralPath $taskCredentialsFile | ConvertFrom-Json
$taskCredentials.url = "http://localhost:$Port"
$taskCredentials | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $taskCredentialsFile
$env:APP_SEED_PASSWORD = $taskCredentials.password
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    $taskJdk = Get-ChildItem -LiteralPath 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'jdk-21*' } | Select-Object -First 1
    if (-not $taskJdk) { throw 'Cần cấu hình JAVA_HOME tới JDK 21 trước khi chạy demo.' }
    $env:JAVA_HOME = $taskJdk.FullName
}
$taskJavaVersion = & (Join-Path $env:JAVA_HOME 'bin/java.exe') --version | Out-String
if ($taskJavaVersion -notmatch '^(java|openjdk) 21') { throw 'JAVA_HOME hiện không phải JDK 21. Hãy chọn JDK 21 rồi chạy lại.' }
Write-Host "Demo: http://localhost:$Port/home"
Write-Host "Thông tin đăng nhập: $taskCredentialsFile"
Write-Host 'Seed chạy một lần; chạy lại không đặt lại giỏ hoặc tồn kho.'
& .\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false' "-Dspring-boot.run.arguments=--spring.profiles.active=dev --app.seed.enabled=true --server.port=$Port --spring.jpa.properties.hibernate.show_sql=false --spring.jpa.show-sql=false"
if ($LASTEXITCODE -ne 0) { throw "App demo không khởi động được. Maven exit code: $LASTEXITCODE" }
