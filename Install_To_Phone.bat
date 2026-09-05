@echo off
setlocal

echo ===================================================
echo        GTA - Gig Teleprompter Android (AG)        
echo               Phone / Tablet Installer             
echo ===================================================
echo.

set NO_PAUSE=0
if "%1"=="--no-pause" set NO_PAUSE=1
if "%2"=="--no-pause" set NO_PAUSE=1

set SKIP_BUILD=0
if "%1"=="--no-build" set SKIP_BUILD=1
if "%2"=="--no-build" set SKIP_BUILD=1

set ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB_PATH%" (
    where adb >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set ADB_PATH=adb
    ) else (
        echo [ERROR] adb not found in %LOCALAPPDATA%\Android\Sdk\platform-tools or PATH.
        echo Please ensure Android SDK platform-tools are installed.
        if "%NO_PAUSE%"=="0" pause
        exit /b 1
    )
)

set APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk

if "%SKIP_BUILD%"=="0" (
    echo [INFO] Building latest debug APK with gradlew assembleDebug...
    call "%~dp0gradlew.bat" assembleDebug
    if %ERRORLEVEL% NEQ 0 (
        echo.
        echo [ERROR] Gradle build failed. Tingnan ang errors sa taas.
        if "%NO_PAUSE%"=="0" pause
        exit /b %ERRORLEVEL%
    )
) else (
    if not exist "%APK_PATH%" (
        echo [WARN] APK not found. Building despite --no-build flag...
        call "%~dp0gradlew.bat" assembleDebug
    ) else (
        echo [INFO] Skipping build --no-build specified. Using existing APK.
    )
)

echo.
echo [INFO] Checking connected Android devices...
"%ADB_PATH%" devices

echo.
echo [INFO] Installing GTA to device / tablet...
"%ADB_PATH%" install -r -d "%APK_PATH%"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [WARN] Standard install failed. Retrying with signature refresh...
    "%ADB_PATH%" uninstall com.joel.gta
    "%ADB_PATH%" install -r "%APK_PATH%"
)

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================
    echo SUCCESS! GTA is now installed on your device.
    echo Opening GTA application on phone / tablet...
    echo ===================================================
    "%ADB_PATH%" shell am start -n com.joel.gta/.MainActivity
) else (
    echo.
    echo [ERROR] Hindi natapos ang pag-install. Siguraduhin na:
    echo 1. Naka-on ang 'USB Debugging' sa Developer Options ng device.
    echo 2. Naka-allow ang computer sa popup prompt sa screen ng device.
    echo 3. Naka-unlock ang screen ng phone habang nag-i-install.
)

if "%NO_PAUSE%"=="0" (
    echo.
    pause
)
