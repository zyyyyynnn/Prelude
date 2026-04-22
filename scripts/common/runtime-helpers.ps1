Set-StrictMode -Version Latest

function Ensure-Directory {
  param([string]$Path)

  if (-not (Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
  }
}

function Ensure-Command {
  param([string]$Name)

  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command not found: $Name"
  }
}

function Ensure-PathExists {
  param(
    [string]$Path,
    [string]$Label
  )

  if (-not (Test-Path -LiteralPath $Path)) {
    throw "$Label not found: $Path"
  }
}

function Reset-LogFile {
  param([string]$Path)

  if (Test-Path -LiteralPath $Path) {
    Remove-Item -LiteralPath $Path -Force
  }
}

function Ensure-FrontendDependencies {
  param([string]$FrontendDir)

  $nodeModulesDir = Join-Path $FrontendDir 'node_modules'
  if (Test-Path -LiteralPath $nodeModulesDir) {
    return
  }

  Write-Host '[INFO] Frontend dependencies missing, running npm install ...'
  Push-Location $FrontendDir
  try {
    & npm install
    if ($LASTEXITCODE -ne 0) {
      throw "Frontend dependency installation failed in $FrontendDir."
    }
  } finally {
    Pop-Location
  }
}

function Test-PortListening {
  param([int]$Port)

  return @(
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  ).Count -gt 0
}

function Wait-PortListening {
  param(
    [int]$Port,
    [int]$TimeoutSeconds
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-PortListening -Port $Port) {
      return $true
    }

    Start-Sleep -Seconds 2
  }

  return $false
}

function Read-LogTail {
  param(
    [string]$Path,
    [int]$Lines = 20
  )

  if (-not (Test-Path -LiteralPath $Path)) {
    return $null
  }

  $tail = Get-Content -LiteralPath $Path -Tail $Lines -ErrorAction SilentlyContinue
  if (-not $tail) {
    return $null
  }

  return ($tail -join [Environment]::NewLine)
}

function Wait-HttpReady {
  param(
    [string]$Url,
    [int]$TimeoutSeconds,
    [System.Diagnostics.Process]$Process,
    [string]$StdOutPath,
    [string]$StdErrPath
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if ($Process -and $Process.HasExited) {
      $tail = Read-LogTail -Path $StdErrPath
      if (-not $tail) {
        $tail = Read-LogTail -Path $StdOutPath
      }

      if ($tail) {
        throw "Process exited early while waiting for $Url.`n$tail"
      }

      throw "Process exited early while waiting for $Url. Check logs: $StdOutPath ; $StdErrPath"
    }

    try {
      Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5 | Out-Null
      return
    } catch {
      $hasResponse = $_.Exception -and $_.Exception.PSObject.Properties.Match('Response').Count -gt 0 -and $null -ne $_.Exception.Response
      if ($hasResponse) {
        return
      }

      Start-Sleep -Seconds 2
    }
  }

  throw "Timed out waiting for $Url"
}

function Start-BackgroundCommand {
  param(
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$WorkingDirectory,
    [string]$StdOutPath,
    [string]$StdErrPath
  )

  return Start-Process `
    -FilePath $FilePath `
    -ArgumentList $Arguments `
    -WorkingDirectory $WorkingDirectory `
    -RedirectStandardOutput $StdOutPath `
    -RedirectStandardError $StdErrPath `
    -PassThru
}

function Start-HiddenMySqlProcess {
  param(
    [string]$FilePath,
    [string]$DefaultsFile,
    [string]$ErrorLogPath,
    [string]$WorkingDirectory
  )

  return Start-Process `
    -FilePath $FilePath `
    -ArgumentList @("--defaults-file=`"$DefaultsFile`"", "--log-error=`"$ErrorLogPath`"") `
    -WorkingDirectory $WorkingDirectory `
    -WindowStyle Hidden `
    -PassThru
}

function Get-ApplicationLocalDatasourceConfig {
  param([string]$ConfigPath)

  $defaults = [pscustomobject]@{
    Host = '127.0.0.1'
    Port = 3306
    Username = 'root'
    Password = $null
  }

  if (-not (Test-Path -LiteralPath $ConfigPath)) {
    return $defaults
  }

  $content = Get-Content -LiteralPath $ConfigPath -Raw
  $urlMatch = [regex]::Match($content, '(?m)^\s*url:\s*(?<value>jdbc:mysql://[^\r\n]+)')
  $userMatch = [regex]::Match($content, '(?m)^\s*username:\s*(?<value>[^\r\n#]+)')
  $passwordMatch = [regex]::Match($content, '(?m)^\s*password:\s*(?<value>[^\r\n#]+)')

  $dbHost = $defaults.Host
  $port = $defaults.Port

  if ($urlMatch.Success) {
    $jdbcMatch = [regex]::Match($urlMatch.Groups['value'].Value.Trim(), '^jdbc:mysql://(?<host>[^:/?]+)(:(?<port>\d+))?/')
    if ($jdbcMatch.Success) {
      $dbHost = $jdbcMatch.Groups['host'].Value
      if ($jdbcMatch.Groups['port'].Success) {
        $port = [int]$jdbcMatch.Groups['port'].Value
      }
    }
  }

  return [pscustomobject]@{
    Host = $dbHost
    Port = $port
    Username = if ($userMatch.Success) { $userMatch.Groups['value'].Value.Trim() } else { $defaults.Username }
    Password = if ($passwordMatch.Success) { $passwordMatch.Groups['value'].Value.Trim() } else { $defaults.Password }
  }
}

function Ensure-MySqlReady {
  param(
    [pscustomobject]$DatasourceConfig,
    [string]$MySqlLogDir
  )

  if (Test-PortListening -Port $DatasourceConfig.Port) {
    return $true
  }

  $serviceCandidates = @('MySQL80', 'MySQL', 'mysql')
  $serviceWarnings = New-Object System.Collections.Generic.List[string]
  foreach ($serviceName in $serviceCandidates) {
    $service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
    if (-not $service) {
      continue
    }

    if ($service.Status -ne 'Running') {
      try {
        Start-Service -Name $serviceName -ErrorAction Stop
      } catch {
        $serviceWarnings.Add("Failed to start MySQL service ${serviceName}: $($_.Exception.Message)")
      }
    }

    if (Wait-PortListening -Port $DatasourceConfig.Port -TimeoutSeconds 15) {
      return $true
    }
  }

  $mysqldCandidates = @()
  if ($env:MYSQLD_PATH) {
    $mysqldCandidates += $env:MYSQLD_PATH
  }
  $mysqldCandidates += @(
    'E:\DevEnv\MySQL\bin\mysqld.exe',
    'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe',
    'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe'
  )

  $defaultsCandidates = @()
  if ($env:MYSQL_DEFAULTS_FILE) {
    $defaultsCandidates += $env:MYSQL_DEFAULTS_FILE
  }
  $defaultsCandidates += @(
    'E:\DevEnv\MySQL\conf\my.ini',
    'C:\ProgramData\MySQL\MySQL Server 8.0\my.ini',
    'C:\ProgramData\MySQL\MySQL Server 8.4\my.ini'
  )

  $mysqldPath = $mysqldCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
  $defaultsFile = $defaultsCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1

  if ($mysqldPath -and $defaultsFile) {
    Ensure-Directory $MySqlLogDir
    $mysqlOutLog = Join-Path $MySqlLogDir 'mysql-auto.out.log'
    $mysqlErrLog = Join-Path $MySqlLogDir 'mysql-auto.err.log'

    if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
      Reset-LogFile -Path $mysqlOutLog
      Reset-LogFile -Path $mysqlErrLog
      Start-HiddenMySqlProcess `
        -FilePath $mysqldPath `
        -DefaultsFile $defaultsFile `
        -ErrorLogPath $mysqlErrLog `
        -WorkingDirectory (Split-Path -Parent $mysqldPath) `
        | Out-Null
    }

    if (Wait-PortListening -Port $DatasourceConfig.Port -TimeoutSeconds 15) {
      return $true
    }
  }

  foreach ($warningMessage in $serviceWarnings) {
    Write-Warning $warningMessage
  }

  return $false
}

function Try-EnsureDatabase {
  param(
    [string]$DatabaseName,
    [pscustomobject]$DatasourceConfig
  )

  $mysql = Get-Command mysql -ErrorAction SilentlyContinue
  if (-not $mysql) {
    Write-Warning "mysql command not found. Skip database creation for $DatabaseName."
    return $false
  }

  $hostName = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { $DatasourceConfig.Host }
  $port = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { [string]$DatasourceConfig.Port }
  $user = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { $DatasourceConfig.Username }
  $password = if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { $DatasourceConfig.Password }

  $arguments = @('-h', $hostName, '-P', $port, '-u', $user)
  if ($password) {
    $arguments += "--password=$password"
  }

  $arguments += '-e'
  $arguments += "CREATE DATABASE IF NOT EXISTS $DatabaseName DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

  & $mysql.Source @arguments 2>$null | Out-Null
  return $LASTEXITCODE -eq 0
}
