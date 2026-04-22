@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%interview-backend"
set "FRONTEND_DIR=%ROOT%interview-frontend"
set "PREPARE_SCRIPT=%ROOT%scripts\demo\start-demo.ps1"
set "BACKEND_READY_URL=http://127.0.0.1:8081/api/health"
set "BACKEND_READY_TIMEOUT=120"
set "BACKEND_PORT=8081"
set "FRONTEND_PORT=5174"
set "BACKEND_TITLE=Backend - Spring Boot (Demo)"
set "FRONTEND_TITLE=Frontend - Vite (Demo)"

call :resolve_shell
if errorlevel 1 goto :fail

if not exist "%BACKEND_DIR%" (
  echo [ERROR] Backend directory not found: %BACKEND_DIR%
  goto :fail
)

if not exist "%FRONTEND_DIR%" (
  echo [ERROR] Frontend directory not found: %FRONTEND_DIR%
  goto :fail
)

if not exist "%PREPARE_SCRIPT%" (
  echo [ERROR] Prepare script not found: %PREPARE_SCRIPT%
  goto :fail
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven command not found ^(mvn^). Install Maven and add it to PATH.
  goto :fail
)

where npm >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm command not found. Install Node.js and add it to PATH.
  goto :fail
)

where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] node command not found. Install Node.js and add it to PATH.
  goto :fail
)

echo [INFO] Preparing demo runtime ^(MySQL/database/frontend deps^)...
call "%PWSH_CMD%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%PREPARE_SCRIPT%" -PrepareOnly
if errorlevel 1 (
  echo [ERROR] Demo runtime preparation failed.
  goto :fail
)

call :is_port_listening %BACKEND_PORT%
if errorlevel 1 (
  echo Starting backend ^(demo: mvn -Dspring-boot.run.profiles=demo spring-boot:run^)...
  start "%BACKEND_TITLE%" cmd /k "cd /d "%BACKEND_DIR%" && mvn -Dspring-boot.run.profiles=demo spring-boot:run"
) else (
  echo [INFO] Backend port %BACKEND_PORT% is already listening. Skip backend launch.
)

echo [INFO] Waiting for backend readiness at %BACKEND_READY_URL% ^(timeout: %BACKEND_READY_TIMEOUT%s^)...
call :wait_for_backend
if errorlevel 1 (
  echo [WARN] Demo backend did not become reachable within %BACKEND_READY_TIMEOUT%s.
  echo [WARN] Frontend will still start, but login may fail until backend is ready.
  echo [WARN] Please check MySQL and the "%BACKEND_TITLE%" window.
) else (
  echo [INFO] Demo backend is reachable. Starting frontend next.
)

call :is_port_listening %FRONTEND_PORT%
if errorlevel 1 (
  echo Starting frontend ^(demo: npm run dev:demo^)...
  start "%FRONTEND_TITLE%" cmd /k "cd /d "%FRONTEND_DIR%" && npm run dev:demo"
) else (
  echo [INFO] Frontend port %FRONTEND_PORT% is already listening. Skip frontend launch.
)

echo.
echo Demo services launched:
echo - Backend window: %BACKEND_TITLE%
echo - Frontend window: %FRONTEND_TITLE%
echo - URLs: http://127.0.0.1:8081  and  http://127.0.0.1:5174
echo.
echo To stop services, close each window or press Ctrl+C in each service window.
pause
exit /b 0

:resolve_shell
where pwsh >nul 2>nul
if errorlevel 1 (
  where powershell >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] Neither pwsh nor powershell was found in PATH.
    exit /b 1
  )
  set "PWSH_CMD=powershell"
  exit /b 0
)

set "PWSH_CMD=pwsh"
exit /b 0

:is_port_listening
call "%PWSH_CMD%" -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$port = [int]%~1; if (@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { exit 0 } else { exit 1 }"
exit /b %errorlevel%

:wait_for_backend
call "%PWSH_CMD%" -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
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

:fail
pause
exit /b 1
