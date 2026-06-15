@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "ROOT=%~dp0"
set "COMPOSE=docker compose"
set "PROFILE=real"
set "BACKEND_READY_URL=http://127.0.0.1:8080/api/health"
set "BACKEND_READY_TIMEOUT=120"
set "FRONTEND_URL=http://127.0.0.1:5173"
set "FRONTEND_READY_TIMEOUT=90"

cd /d "%ROOT%"

REM ---- 前置校验：Docker ----
where docker >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Docker not found ^(docker^). Install Docker Desktop and ensure it is running.
  pause
  exit /b 1
)
docker info >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Docker daemon not reachable. Start Docker Desktop first.
  pause
  exit /b 1
)
echo [INFO] Docker is available.

REM ---- 确保 .env 存在（缺则从模板复制，不阻塞启动） ----
if not exist "%ROOT%.env" (
  if exist "%ROOT%.env.example" (
    echo [INFO] .env not found, copying from .env.example ^(please edit real secrets later^).
    copy /Y "%ROOT%.env.example" "%ROOT%.env" >nul
  ) else (
    echo [WARN] .env and .env.example both missing; proceeding with compose defaults.
  )
)

REM ---- 校验 compose 配置 ----
echo [INFO] Validating docker compose config ^(profile: %PROFILE%^)...
%COMPOSE% --profile %PROFILE% config >nul 2>nul
if errorlevel 1 (
  echo [ERROR] docker compose config validation failed. Check docker-compose.yml and .env.
  %COMPOSE% --profile %PROFILE% config
  pause
  exit /b 1
)

REM ---- 构建 + 启动 ----
echo [INFO] Building and starting Docker stack ^(profile: %PROFILE%^)...
%COMPOSE% --profile %PROFILE% up -d --build
if errorlevel 1 (
  echo [ERROR] docker compose up failed. See output above.
  pause
  exit /b 1
)

REM ---- 等待后端就绪 ----
echo [INFO] Waiting for backend readiness at %BACKEND_READY_URL% ^(timeout: %BACKEND_READY_TIMEOUT%s^)...
call :wait_for_url "%BACKEND_READY_URL%" %BACKEND_READY_TIMEOUT%
if errorlevel 1 (
  echo [ERROR] Backend did not become reachable within %BACKEND_READY_TIMEOUT%s.
  echo [ERROR] Run: docker compose --profile %PROFILE% logs backend-real
  pause
  exit /b 1
)
echo [INFO] Backend is reachable.

REM ---- 等待前端就绪 ----
echo [INFO] Waiting for frontend readiness at %FRONTEND_URL% ^(timeout: %FRONTEND_READY_TIMEOUT%s^)...
call :wait_for_url "%FRONTEND_URL%" %FRONTEND_READY_TIMEOUT%
if errorlevel 1 (
  echo [ERROR] Frontend did not become reachable within %FRONTEND_READY_TIMEOUT%s.
  echo [ERROR] Run: docker compose --profile %PROFILE% logs frontend-real
  pause
  exit /b 1
)
echo [INFO] Frontend is reachable.

echo.
echo ============================================================
echo  Prelude real stack is running ^(Docker^).
echo  - Frontend : %FRONTEND_URL%
echo  - Backend  : http://127.0.0.1:8080
echo  - Health   : http://127.0.0.1:8080/api/health
echo  - MySQL    : 127.0.0.1:13306  ^(db: interview_system^)
echo  - RabbitMQ : 127.0.0.1:15672  ^(guest / guest^)
echo ============================================================
echo  Stop app layer : docker compose stop backend-real frontend-real
echo  Stop all + mw  : docker compose --profile real --profile demo down
echo ============================================================
echo.
pause
goto :eof

:wait_for_url
REM %1 = url, %2 = timeout seconds
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$url = '%~1';" ^
  "$deadline = (Get-Date).AddSeconds([int]'%~2');" ^
  "$ready = $false;" ^
  "while ((Get-Date) -lt $deadline) {" ^
  "  try {" ^
  "    Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5 | Out-Null;" ^
  "    $ready = $true;" ^
  "    break;" ^
  "  } catch {" ^
  "    if ($_.Exception.Response) { $ready = $true; break }" ^
  "  }" ^
  "  Start-Sleep -Seconds 2;" ^
  "}" ^
  "if ($ready) { exit 0 } else { exit 1 }"
exit /b %errorlevel%
