@echo off
set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

if not exist "%ADB_PATH%" (
    echo [ERROR] adb.exe not found at: %ADB_PATH%
    pause
    exit /b 1
)

echo Checking for connected Android devices...
"%ADB_PATH%" devices

echo.
echo Installing GTA debug APK to device...
"%ADB_PATH%" install -r "%~dp0app\build\outputs\apk\debug\app-debug.apk"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===========================================
    echo SUCCESS! GTA is now installed on your phone.
    echo Opening GTA application...
    echo ===========================================
    "%ADB_PATH%" shell am start -n com.joel.gta/.MainActivity
) else (
    echo.
    echo [NOTE] Kung hindi nag-install, siguraduhin na:
    echo 1. Naka-on ang 'USB Debugging' sa Developer Options ng phone mo.
    echo 2. Naka-allow ang computer mo sa popup prompt sa screen ng phone.
)

echo.
pause
