@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%interview-backend"
set "FRONTEND_DIR=%ROOT%interview-frontend"
set "BACKEND_READY_URL=http://127.0.0.1:8080/api/health"
set "FRONTEND_READY_URL=http://127.0.0.1:5173/login"
set "BACKEND_READY_TIMEOUT=90"
set "FRONTEND_READY_TIMEOUT=60"
set "MYSQL_EXE=E:\DevEnv\MySQL\bin\mysqld.exe"
set "MYSQL_CNF=E:\DevEnv\MySQL\conf\my.ini"
set "MYSQL_CLI=E:\DevEnv\MySQL\bin\mysql.exe"
set "MYSQL_USER=root"
set "MYSQL_PASSWORD=mysql123456"
set "DB_NAME=interview_system"
set "DEMO_USERNAME=demo"
set "DEMO_PASSWORD=123456"

echo ========================================
echo  Interview MVP Development Launcher
echo ========================================
echo.

if not exist "%BACKEND_DIR%\pom.xml" (
  echo [ERROR] Backend project not found: %BACKEND_DIR%
  pause
  exit /b 1
)

if not exist "%FRONTEND_DIR%\package.json" (
  echo [ERROR] Frontend project not found: %FRONTEND_DIR%
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

echo [INFO] Checking MySQL on port 3306...
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-NetTCPConnection -LocalPort 3306 -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  if exist "%MYSQL_EXE%" (
    echo [INFO] MySQL is not listening. Starting local MySQL...
    start "MySQL - Interview" cmd /k ""%MYSQL_EXE%" --defaults-file="%MYSQL_CNF%" --console"
    timeout /t 6 /nobreak >nul
  ) else (
    echo [WARN] MySQL is not listening and mysqld.exe was not found:
    echo        %MYSQL_EXE%
    echo [WARN] Start MySQL manually before using backend APIs.
  )
) else (
  echo [INFO] MySQL is already listening.
)

if exist "%MYSQL_CLI%" (
  echo [INFO] Ensuring database exists: %DB_NAME%
  "%MYSQL_CLI%" -u%MYSQL_USER% -p%MYSQL_PASSWORD% -e "CREATE DATABASE IF NOT EXISTS %DB_NAME% DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >nul 2>nul
  if errorlevel 1 (
    echo [WARN] Database initialization command failed. Check MySQL password or server status.
  )
) else (
  echo [WARN] mysql.exe not found. Skip database creation check.
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

echo.
echo [INFO] Starting backend ^(Spring Boot: mvn spring-boot:run^)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  start "Backend - Spring Boot" cmd /k "cd /d "%BACKEND_DIR%" && mvn spring-boot:run"
) else (
  echo [INFO] Backend port 8080 is already listening. Skip backend startup.
)

echo [INFO] Waiting for backend readiness at %BACKEND_READY_URL% ^(timeout: %BACKEND_READY_TIMEOUT%s^)...
call :wait_for_url "%BACKEND_READY_URL%" "%BACKEND_READY_TIMEOUT%"
if errorlevel 1 (
  echo [WARN] Backend did not become reachable within %BACKEND_READY_TIMEOUT%s.
  echo [WARN] Frontend will still start, but API calls may fail until backend is ready.
  echo [WARN] Check MySQL and the "Backend - Spring Boot" window.
) else (
  echo [INFO] Backend is reachable.
)

if exist "%MYSQL_CLI%" (
  echo [INFO] Ensuring demo login account exists...
  "%MYSQL_CLI%" -u%MYSQL_USER% -p%MYSQL_PASSWORD% %DB_NAME% -e "INSERT INTO user(username,password,email) SELECT 'demo', '$2a$10$cwL4a7RrPcB895DFoO2MyuhK6QGDWhU0fScSmKj/LuBDtIzmL2zL2', 'demo@example.com' WHERE NOT EXISTS (SELECT 1 FROM user WHERE username='demo');" >nul 2>nul
  if errorlevel 1 (
    echo [WARN] Demo account initialization failed. Backend data.sql may still create it on restart.
  )
)

echo.
echo [INFO] Starting frontend ^(Vite: npm run dev^)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  start "Frontend - Vite" cmd /k "cd /d "%FRONTEND_DIR%" && npm run dev -- --host 127.0.0.1"
) else (
  echo [INFO] Frontend port 5173 is already listening. Skip frontend startup.
)

echo [INFO] Waiting for frontend readiness at %FRONTEND_READY_URL% ^(timeout: %FRONTEND_READY_TIMEOUT%s^)...
call :wait_for_url "%FRONTEND_READY_URL%" "%FRONTEND_READY_TIMEOUT%"
if errorlevel 1 (
  echo [WARN] Frontend did not become reachable within %FRONTEND_READY_TIMEOUT%s.
) else (
  echo [INFO] Frontend is reachable.
  start "" "%FRONTEND_READY_URL%"
)

echo.
echo Services launched:
echo - MySQL window: MySQL - Interview ^(if it was not already running^)
echo - Backend window: Backend - Spring Boot
echo - Frontend window: Frontend - Vite
echo.
echo Initial login account:
echo - Username: %DEMO_USERNAME%
echo - Password: %DEMO_PASSWORD%
echo.
echo To stop services, close each window or press Ctrl+C in each service window.
pause
goto :eof

:wait_for_url
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
  "    if ($_.Exception.Response) {" ^
  "      $ready = $true;" ^
  "      break;" ^
  "    }" ^
  "  }" ^
  "  Start-Sleep -Seconds 2;" ^
  "}" ^
  "if ($ready) { exit 0 } else { exit 1 }"
exit /b %errorlevel%
