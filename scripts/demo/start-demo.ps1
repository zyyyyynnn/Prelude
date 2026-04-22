[CmdletBinding()]
param(
  [switch]$PrepareOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $PSCommandPath
$rootDir = [System.IO.Path]::GetFullPath((Join-Path $scriptDir '..\..'))
. (Join-Path $rootDir 'scripts\common\runtime-helpers.ps1')

$backendDir = Join-Path $rootDir 'interview-backend'
$frontendDir = Join-Path $rootDir 'interview-frontend'
$runtimeDir = Join-Path $rootDir 'output\runtime'
$mysqlLogDir = Join-Path $runtimeDir 'mysql'
$backendLogDir = Join-Path $runtimeDir 'backend-demo'
$frontendLogDir = Join-Path $runtimeDir 'frontend-demo'
$backendOutLog = Join-Path $backendLogDir 'backend-demo.out.log'
$backendErrLog = Join-Path $backendLogDir 'backend-demo.err.log'
$frontendOutLog = Join-Path $frontendLogDir 'frontend-demo.out.log'
$frontendErrLog = Join-Path $frontendLogDir 'frontend-demo.err.log'
$backendUrl = 'http://127.0.0.1:8081/api/health'
$frontendUrl = 'http://127.0.0.1:5174/login'
$databaseName = 'interview_demo'

Ensure-Command 'mvn'
Ensure-Command 'npm'
Ensure-Command 'node'

Ensure-PathExists -Path $backendDir -Label 'Backend directory'
Ensure-PathExists -Path $frontendDir -Label 'Frontend directory'

Ensure-Directory $runtimeDir
Ensure-Directory $mysqlLogDir
Ensure-Directory $backendLogDir
Ensure-Directory $frontendLogDir

Ensure-FrontendDependencies -FrontendDir $frontendDir

$mvnPath = (Get-Command mvn -ErrorAction Stop).Source
$nodePath = (Get-Command node -ErrorAction Stop).Source
$viteScript = Join-Path $frontendDir 'node_modules\vite\bin\vite.js'
$applicationLocalPath = Join-Path $backendDir 'src\main\resources\application-local.yml'
$datasourceConfig = Get-ApplicationLocalDatasourceConfig -ConfigPath $applicationLocalPath

if (-not (Ensure-MySqlReady -DatasourceConfig $datasourceConfig -MySqlLogDir $mysqlLogDir)) {
  throw "MySQL is not listening on $($datasourceConfig.Host):$($datasourceConfig.Port). Start MySQL first, or set MYSQLD_PATH / MYSQL_DEFAULTS_FILE so the launcher can start it automatically."
}

if (-not (Try-EnsureDatabase -DatabaseName $databaseName -DatasourceConfig $datasourceConfig)) {
  Write-Warning "Failed to ensure database $databaseName. Check MySQL credentials in application-local.yml or MYSQL_* environment variables."
}

if ($PrepareOnly) {
  Write-Host 'Demo runtime preparation complete.'
  return
}

$backendProcess = $null
if (-not (Test-PortListening -Port 8081)) {
  Reset-LogFile -Path $backendOutLog
  Reset-LogFile -Path $backendErrLog
  $backendProcess = Start-BackgroundCommand `
    -FilePath $mvnPath `
    -Arguments @('-Dspring-boot.run.profiles=demo', 'spring-boot:run') `
    -WorkingDirectory $backendDir `
    -StdOutPath $backendOutLog `
    -StdErrPath $backendErrLog
}

Wait-HttpReady -Url $backendUrl -TimeoutSeconds 120 -Process $backendProcess -StdOutPath $backendOutLog -StdErrPath $backendErrLog

$frontendProcess = $null
if (-not (Test-PortListening -Port 5174)) {
  Reset-LogFile -Path $frontendOutLog
  Reset-LogFile -Path $frontendErrLog
  $frontendProcess = Start-BackgroundCommand `
    -FilePath $nodePath `
    -Arguments @("`"$viteScript`"", '--mode', 'demo', '--host', '127.0.0.1') `
    -WorkingDirectory $frontendDir `
    -StdOutPath $frontendOutLog `
    -StdErrPath $frontendErrLog
}

Wait-HttpReady -Url $frontendUrl -TimeoutSeconds 90 -Process $frontendProcess -StdOutPath $frontendOutLog -StdErrPath $frontendErrLog

Write-Host "Demo backend:  http://127.0.0.1:8081"
Write-Host "Demo frontend: http://127.0.0.1:5174"
Write-Host "Backend logs:  $backendLogDir"
Write-Host "Frontend logs: $frontendLogDir"
