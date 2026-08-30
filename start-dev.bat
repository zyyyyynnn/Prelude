@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%backend"
set "FRONTEND_DIR=%ROOT%frontend"
set "BACKEND_READY_URL=http://127.0.0.1:8080/actuator/health"

cd /d "%ROOT%"

where docker >nul 2>nul || goto :missing_docker
docker info >nul 2>nul || goto :missing_daemon
where mvn >nul 2>nul || goto :missing_maven
where npm >nul 2>nul || goto :missing_npm

for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$port=5173; while(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){$port++}; $port"`) do set "FRONTEND_PORT=%%P"
set "FRONTEND_URL=http://127.0.0.1:%FRONTEND_PORT%"

if not exist "%ROOT%.env" if exist "%ROOT%.env.example" copy /Y "%ROOT%.env.example" "%ROOT%.env" >nul

echo [INFO] Starting MySQL, Redis, and RabbitMQ...
docker compose up -d mysql redis rabbitmq || goto :failed

set "MYSQL_PORT=13306"
set "REDIS_PORT=16379"
set "RABBITMQ_PORT=5672"
if exist "%ROOT%.env" for /f "tokens=1,2 delims==" %%A in ('findstr /b "MYSQL_HOST_PORT=" "%ROOT%.env"') do set "MYSQL_PORT=%%B"
if exist "%ROOT%.env" for /f "tokens=1,2 delims==" %%A in ('findstr /b "REDIS_HOST_PORT=" "%ROOT%.env"') do set "REDIS_PORT=%%B"
if exist "%ROOT%.env" for /f "tokens=1,2 delims==" %%A in ('findstr /b "RABBITMQ_HOST_PORT=" "%ROOT%.env"') do set "RABBITMQ_PORT=%%B"

call :wait_for_port %MYSQL_PORT% 60 || goto :failed
call :wait_for_port %REDIS_PORT% 60 || goto :failed
call :wait_for_port %RABBITMQ_PORT% 60 || goto :failed

if not exist "%FRONTEND_DIR%\node_modules" (
  echo [INFO] Installing frontend dependencies...
  call npm --prefix "%FRONTEND_DIR%" ci || goto :failed
)

echo [INFO] Starting Prelude backend...
start "Prelude Backend" /D "%BACKEND_DIR%" cmd /k "mvn spring-boot:run -Dspring-boot.run.profiles=dev"
call :wait_for_url "%BACKEND_READY_URL%" 120 || goto :failed

echo [INFO] Starting Prelude React frontend...
start "Prelude Frontend" /D "%FRONTEND_DIR%" cmd /k "npm run dev -- --host 127.0.0.1 --port %FRONTEND_PORT% --strictPort"
call :wait_for_url "%FRONTEND_URL%" 60 || goto :failed

echo.
echo Prelude is running:
echo   Frontend: %FRONTEND_URL%
echo   Backend health: %BACKEND_READY_URL%
echo.
pause
exit /b 0

:wait_for_port
powershell -NoProfile -ExecutionPolicy Bypass -Command "$port=%~1; $deadline=(Get-Date).AddSeconds([int]'%~2'); while((Get-Date)-lt $deadline){if(Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet){exit 0}; Start-Sleep 1}; exit 1"
exit /b %errorlevel%

:wait_for_url
powershell -NoProfile -ExecutionPolicy Bypass -Command "$url='%~1'; $deadline=(Get-Date).AddSeconds([int]'%~2'); while((Get-Date)-lt $deadline){try{$response=Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5; if($response.StatusCode -lt 400){exit 0}}catch{}; Start-Sleep 2}; exit 1"
exit /b %errorlevel%

:missing_docker
echo [ERROR] Docker was not found.
goto :failed
:missing_daemon
echo [ERROR] Docker Desktop is not running.
goto :failed
:missing_maven
echo [ERROR] Maven was not found.
goto :failed
:missing_npm
echo [ERROR] npm was not found.
goto :failed
:failed
echo [ERROR] Prelude startup failed.
pause
exit /b 1
