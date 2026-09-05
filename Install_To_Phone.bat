@echo off
setlocal enabledelayedexpansion

set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB_PATH%" (
    where adb >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set ADB_PATH=adb
    ) else (
        echo [ERROR] adb not found in %LOCALAPPDATA%\Android\Sdk\platform-tools or PATH.
        if "%1" neq "--no-pause" pause
        exit /b 1
    )
)

set APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK_PATH%" (
    echo [INFO] Debug APK not found. Building with gradlew assembleDebug...
    call "%~dp0gradlew.bat" assembleDebug
)

echo [INFO] Checking connected devices...
"%ADB_PATH%" devices

echo.
echo [INFO] Installing GTA to device / tablet...
"%ADB_PATH%" install -r "%APK_PATH%"
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] Standard install failed. Retrying with uninstall of old signature...
    "%ADB_PATH%" uninstall com.joel.gta
    "%ADB_PATH%" install -r "%APK_PATH%"
)

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===========================================
    echo SUCCESS! GTA is now installed on your device.
    echo Opening GTA application...
    echo ===========================================
    "%ADB_PATH%" shell am start -n com.joel.gta/.MainActivity
) else (
    echo.
    echo [ERROR] Hindi nag-install. Siguraduhin na:
    echo 1. Naka-on ang 'USB Debugging' sa Developer Options ng device.
    echo 2. Naka-allow ang computer sa popup prompt sa screen ng device.
)

if "%1" neq "--no-pause" (
    echo.
    pause
)
