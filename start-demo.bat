@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%backend"
set "FRONTEND_DIR=%ROOT%frontend"
set "BACKEND_READY_URL=http://127.0.0.1:8081/api/health"
set "FRONTEND_URL=http://127.0.0.1:5174"

cd /d "%ROOT%"

REM ---- 前置检查 ----
where docker >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Docker not found.
  pause
  exit /b 1
)
docker info >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Docker daemon not reachable.
  pause
  exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven ^(mvn^) not found.
  pause
  exit /b 1
)
where npm >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm not found.
  pause
  exit /b 1
)

if not exist "%BACKEND_DIR%" (
  echo [ERROR] backend directory not found.
  pause
  exit /b 1
)
if not exist "%FRONTEND_DIR%" (
  echo [ERROR] frontend directory not found.
  pause
  exit /b 1
)

REM ---- 确保 .env 存在 ----
if not exist "%ROOT%.env" (
  if exist "%ROOT%.env.example" (
    echo [INFO] .env not found, copying from .env.example
    copy /Y "%ROOT%.env.example" "%ROOT%.env" >nul
  )
)

REM ---- 启动 Docker 中间件 ----
echo [INFO] Starting Docker middleware...
docker compose up -d mysql redis rabbitmq
if errorlevel 1 (
  echo [ERROR] Failed to start Docker middleware.
  pause
  exit /b 1
)

REM 从 .env 尝试读取端口，如果没有则使用默认值
set "MYSQL_PORT=13306"
set "REDIS_PORT=16379"
set "RABBITMQ_PORT=5672"
if exist "%ROOT%.env" (
  for /f "tokens=1,2 delims==" %%A in ('findstr /b "MYSQL_HOST_PORT=" "%ROOT%.env"') do set "MYSQL_PORT=%%B"
  for /f "tokens=1,2 delims==" %%A in ('findstr /b "REDIS_HOST_PORT=" "%ROOT%.env"') do set "REDIS_PORT=%%B"
  for /f "tokens=1,2 delims==" %%A in ('findstr /b "RABBITMQ_HOST_PORT=" "%ROOT%.env"') do set "RABBITMQ_PORT=%%B"
)

echo [INFO] Waiting for middleware ports: MySQL %MYSQL_PORT%, Redis %REDIS_PORT%, RabbitMQ %RABBITMQ_PORT%
call :wait_for_port %MYSQL_PORT%
call :wait_for_port %REDIS_PORT%
call :wait_for_port %RABBITMQ_PORT%

REM ---- 确保后端本地配置 ----
if not exist "%BACKEND_DIR%\src\main\resources\application-local.yml" (
  if exist "%BACKEND_DIR%\src\main\resources\application-local.example.yml" (
    echo [INFO] application-local.yml not found, copying from example...
    copy /Y "%BACKEND_DIR%\src\main\resources\application-local.example.yml" "%BACKEND_DIR%\src\main\resources\application-local.yml" >nul
  )
)

if exist "%BACKEND_DIR%\src\main\resources\application-local.yml" (
  findstr /c:"replace-with-at-least-32-bytes-jwt-secret" "%BACKEND_DIR%\src\main\resources\application-local.yml" >nul 2>nul
  if not errorlevel 1 echo [WARN] application-local.yml uses placeholder JWT_SECRET.
  findstr /c:"replace-with-at-least-32-bytes-aes-secret" "%BACKEND_DIR%\src\main\resources\application-local.yml" >nul 2>nul
  if not errorlevel 1 echo [WARN] application-local.yml uses placeholder APP_CRYPTO_AES_SECRET.
)

REM ---- 确保前端依赖与 Demo 环境变量 ----
if not exist "%FRONTEND_DIR%\node_modules" (
  echo [INFO] Installing frontend dependencies...
  call npm --prefix "%FRONTEND_DIR%" install
)

if not exist "%FRONTEND_DIR%\.env.demo" (
  echo [WARN] frontend/.env.demo not found. Demo frontend may use wrong port or proxy target.
  echo [INFO] Ensure VITE_PORT=5174 and VITE_PROXY_TARGET=http://127.0.0.1:8081 are set in .env.demo.
)

REM ---- 启动后端窗口 ----
echo [INFO] Starting Backend...
start "Backend - Spring Boot" cmd /k "cd /d "%BACKEND_DIR%" && mvn -Dspring-boot.run.profiles=demo spring-boot:run"

echo [INFO] Waiting for backend readiness at %BACKEND_READY_URL% ...
call :wait_for_url "%BACKEND_READY_URL%" 120
if errorlevel 1 (
  echo [ERROR] Backend did not become reachable.
  pause
  exit /b 1
)

REM ---- 启动前端窗口 ----
echo [INFO] Starting Frontend...
start "Frontend - Vite" cmd /k "cd /d "%FRONTEND_DIR%" && npm run dev:demo"

echo [INFO] Waiting for frontend readiness at %FRONTEND_URL% ...
call :wait_for_url "%FRONTEND_URL%" 60
if errorlevel 1 (
  echo [ERROR] Frontend did not become reachable.
  pause
  exit /b 1
)

echo.
echo ============================================================
echo  Prelude Local App Demo Runtime is running.
echo  - Backend window: Backend - Spring Boot
echo  - Frontend window: Frontend - Vite
echo  - Frontend URL: %FRONTEND_URL%
echo  - Login: demo / 123456
echo  - Middleware: Docker mysql/redis/rabbitmq
echo.
echo  * Frontend uses Vite dev server, Vue/CSS modifications will apply instantly via HMR.
echo ============================================================
echo  Stop app: close Backend/Frontend windows or Ctrl+C in them
echo  Stop middleware: docker compose stop mysql redis rabbitmq
echo  Full Docker alternative: .\start-demo-docker.bat
echo ============================================================
echo.
pause
goto :eof

:wait_for_port
powershell -NoProfile -ExecutionPolicy Bypass -Command "$port = %~1; $deadline = (Get-Date).AddSeconds(30); while ((Get-Date) -lt $deadline) { if (@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { exit 0 }; Start-Sleep -Seconds 1 }; exit 1"
if errorlevel 1 (
  echo [ERROR] Port %1 is not listening.
  exit /b 1
)
exit /b 0

:wait_for_url
powershell -NoProfile -ExecutionPolicy Bypass -Command "$url = '%~1'; $deadline = (Get-Date).AddSeconds([int]'%~2'); while ((Get-Date) -lt $deadline) { try { $resp = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5; if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) { exit 0 } } catch { if ($_.Exception.Response) { $resp = $_.Exception.Response; if ([int]$resp.StatusCode -ge 200 -and [int]$resp.StatusCode -lt 400) { exit 0 } } }; Start-Sleep -Seconds 2 }; exit 1"
exit /b %errorlevel%
