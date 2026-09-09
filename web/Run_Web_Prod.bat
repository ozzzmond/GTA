@echo off
title GTA WebApp [PROD PREVIEW]
color 0A
echo ===================================================
echo       GTA WEBAPP - PRODUCTION PREVIEW SERVER
echo ===================================================
echo.
cd /d "%~dp0web"

if not exist node_modules (
    echo [INFO] node_modules not found. Running npm install...
    call npm install
    if errorlevel 1 (
        echo [ERROR] npm install failed.
        pause
        exit /b 1
    )
)

echo [1/2] Building production bundle (npm run build)...
call npm run build
if errorlevel 1 (
    echo.
    echo [ERROR] Production build failed! Check errors above.
    pause
    exit /b 1
)

echo.
echo [2/2] Launching Production Preview Server on http://localhost:4173 ...
echo [INFO] Serving optimized production bundle (port 4173).
echo [INFO] Press Ctrl+C to stop the server.
echo.
call npm run preview -- --port 4173
pause