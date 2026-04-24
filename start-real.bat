@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%interview-backend"
set "FRONTEND_DIR=%ROOT%interview-frontend"
set "BACKEND_READY_URL=http://127.0.0.1:8080/api/health"
set "BACKEND_READY_TIMEOUT=60"
set "FRONTEND_URL=http://127.0.0.1:5173"

if not exist "%BACKEND_DIR%" (
  echo [ERROR] Backend directory not found: %BACKEND_DIR%
  pause
  exit /b 1
)

if not exist "%FRONTEND_DIR%" (
  echo [ERROR] Frontend directory not found: %FRONTEND_DIR%
  pause
  exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven command not found ^(mvn^). Install Maven and add it to PATH.
  pause
  exit /b 1
)

where npm >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm command not found. Install Node.js and add it to PATH.
  pause
  exit /b 1
)

echo.
echo [INFO] Please make sure MySQL is already running.
echo.
call :ensure_mysql
if errorlevel 1 (
  pause
  exit /b 1
)

if not exist "%FRONTEND_DIR%\node_modules" (
  echo [INFO] Frontend dependencies missing, running npm install ...
  call npm --prefix "%FRONTEND_DIR%" install
  if errorlevel 1 (
    echo [ERROR] Frontend dependency installation failed. Run manually:
    echo         cd /d "%FRONTEND_DIR%" ^&^& npm install
    pause
    exit /b 1
  )
)

echo Starting backend ^(backend: mvn spring-boot:run^)...
start "Backend - Spring Boot" cmd /k "cd /d "%BACKEND_DIR%" && mvn spring-boot:run"

echo [INFO] Waiting for backend readiness at %BACKEND_READY_URL% ^(timeout: %BACKEND_READY_TIMEOUT%s^)...
call :wait_for_backend
if errorlevel 1 (
  echo [ERROR] Backend did not become reachable within %BACKEND_READY_TIMEOUT%s.
  echo [ERROR] Frontend will not start because API login would fail.
  echo [ERROR] Please check MySQL, application-local.yml, and the "Backend - Spring Boot" window.
  pause
  exit /b 1
)
echo [INFO] Backend is reachable. Starting frontend next.

echo Starting frontend ^(frontend: npm run dev -- --host 127.0.0.1^)...
start "Frontend - Vite" cmd /k "cd /d "%FRONTEND_DIR%" && npm run dev -- --host 127.0.0.1"

echo.
echo Services launched:
echo - Backend window: Backend - Spring Boot
echo - Frontend window: Frontend - Vite
echo - Frontend URL: %FRONTEND_URL%
echo.
echo To stop services, close each window or press Ctrl+C.
pause
goto :eof

:wait_for_backend
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$url = '%BACKEND_READY_URL%';" ^
  "$deadline = (Get-Date).AddSeconds([int]%BACKEND_READY_TIMEOUT%);" ^
  "$ready = $false;" ^
  "while ((Get-Date) -lt $deadline) {" ^
  "  try {" ^
  "    Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5 | Out-Null;" ^
  "    $ready = $true;" ^
  "    break;" ^
  "  } catch {" ^
  "    if ($_.Exception.Response) {" ^
  "      $ready = $true;" ^
  "      break;" ^
  "    }" ^
  "  }" ^
  "  Start-Sleep -Seconds 2;" ^
  "}" ^
  "if ($ready) { exit 0 } else { exit 1 }"
exit /b %errorlevel%

:ensure_mysql
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$portOpen = $false;" ^
  "try {" ^
  "  $client = [Net.Sockets.TcpClient]::new();" ^
  "  $task = $client.ConnectAsync('127.0.0.1', 3306);" ^
  "  if ($task.Wait(1500) -and $client.Connected) { $portOpen = $true }" ^
  "  $client.Dispose();" ^
  "} catch { }" ^
  "if ($portOpen) { Write-Host '[INFO] MySQL is ready on 127.0.0.1:3306.'; exit 0 }" ^
  "$svc = Get-Service -Name 'MySQL80' -ErrorAction SilentlyContinue;" ^
  "Write-Host '[ERROR] MySQL is not reachable on 127.0.0.1:3306.';" ^
  "if ($svc) { Write-Host ('[ERROR] MySQL80 service status: ' + $svc.Status) }" ^
  "Write-Host '[ERROR] Start MySQL first, then run this script again.';" ^
  "Write-Host '[ERROR] Admin terminal: net start MySQL80';" ^
  "Write-Host '[ERROR] Manual foreground: E:\DevEnv\MySQL\bin\mysqld.exe --defaults-file=E:\DevEnv\MySQL\conf\my.ini --console';" ^
  "exit 1"
exit /b %errorlevel%
