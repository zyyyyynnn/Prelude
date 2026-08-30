@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "ROOT=%~dp0"
set "BACKEND_READY_URL=http://127.0.0.1:8080/actuator/health"
set "FRONTEND_URL=http://127.0.0.1:5173"

cd /d "%ROOT%"
where docker >nul 2>nul || goto :failed
docker info >nul 2>nul || goto :failed
if not exist "%ROOT%.env" if exist "%ROOT%.env.example" copy /Y "%ROOT%.env.example" "%ROOT%.env" >nul

docker compose --profile app config >nul || goto :failed
docker compose --profile app up -d --build || goto :failed
call :wait_for_url "%BACKEND_READY_URL%" 120 || goto :failed
call :wait_for_url "%FRONTEND_URL%" 90 || goto :failed

echo.
echo Prelude Docker stack is running:
echo   Frontend: %FRONTEND_URL%
echo   Backend health: %BACKEND_READY_URL%
echo.
pause
exit /b 0

:wait_for_url
powershell -NoProfile -ExecutionPolicy Bypass -Command "$url='%~1'; $deadline=(Get-Date).AddSeconds([int]'%~2'); while((Get-Date)-lt $deadline){try{$response=Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5; if($response.StatusCode -lt 400){exit 0}}catch{}; Start-Sleep 2}; exit 1"
exit /b %errorlevel%

:failed
echo [ERROR] Prelude Docker startup failed.
pause
exit /b 1
