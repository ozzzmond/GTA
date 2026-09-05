@echo off
setlocal

:: Setup ANSI Colors for clean terminal output
for /f %%e in ('powershell -NoProfile -Command "[char]27"') do set "ESC=%%e"
set "GREEN=%ESC%[92m"
set "YELLOW=%ESC%[93m"
set "RED=%ESC%[91m"
set "CYAN=%ESC%[96m"
set "BOLD=%ESC%[1m"
set "RESET=%ESC%[0m"

echo %CYAN%%BOLD%===============================================================%RESET%
echo %CYAN%%BOLD%    GTA (Gig Teleprompter Android) - Auto Build ^& Deploy      %RESET%
echo %CYAN%%BOLD%===============================================================%RESET%
echo.

set "NO_PAUSE=0"
set "BUILD_VARIANT=debug"
set "BUILD_TASK=assembleDebug"
set "CUSTOM_MSG="

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="--no-pause" (
    set "NO_PAUSE=1"
    shift
    goto parse_args
)
if /i "%~1"=="--release" (
    set "BUILD_VARIANT=release"
    set "BUILD_TASK=assembleRelease"
    shift
    goto parse_args
)
if /i "%~1"=="-m" (
    set "CUSTOM_MSG=%~2"
    shift
    shift
    goto parse_args
)
shift
goto parse_args
:args_done

:: -------------------------------------------------------------
:: STEP 1: LOCAL BUILD & HARDWARE INSTALL
:: -------------------------------------------------------------
echo %BOLD%[STEP 1/2] LOCAL BUILD ^& DEVICE INSTALL%RESET%
echo [INFO] Running Gradle task: %CYAN%%BUILD_TASK%%RESET%...

call "%~dp0gradlew.bat" %BUILD_TASK%
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo %RED%%BOLD%[BUILD FAILED]%RESET% Gradle compilation error encountered.
    echo %YELLOW%[SAFETY LOCK]%RESET% Hindi magpu-push sa Git para protektahan ang repository laban sa sirang code.
    if "%NO_PAUSE%"=="0" pause
    exit /b %ERRORLEVEL%
)

echo %GREEN%%BOLD%[BUILD SUCCESS]%RESET% %BUILD_VARIANT% APK successfully built.
echo.

set "APK_PATH=%~dp0app\build\outputs\apk\%BUILD_VARIANT%\app-%BUILD_VARIANT%.apk"
if not exist "%APK_PATH%" (
    set "APK_PATH=%~dp0app\build\outputs\apk\%BUILD_VARIANT%\app-%BUILD_VARIANT%-unsigned.apk"
)

:: Locate adb
set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB_PATH%" (
    where adb >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        set "ADB_PATH=adb"
    ) else (
        set "ADB_PATH="
    )
)

if not defined ADB_PATH goto skip_device_install

echo [INFO] Checking connected Android devices via adb...
"%ADB_PATH%" devices

set "DEVICE_FOUND=0"
for /f "skip=1 tokens=1,2" %%a in ('"%ADB_PATH%" devices') do (
    if "%%b"=="device" set "DEVICE_FOUND=1"
)

if "%DEVICE_FOUND%"=="0" goto no_device_found

echo.
echo [INFO] Installing to connected device / tablet...
"%ADB_PATH%" install -r -d "%APK_PATH%"
if %ERRORLEVEL% NEQ 0 (
    echo %YELLOW%[WARN]%RESET% Standard update failed. Retrying with fresh signature...
    "%ADB_PATH%" uninstall com.joel.gta >nul 2>&1
    "%ADB_PATH%" install -r "%APK_PATH%"
)

if %ERRORLEVEL% EQU 0 (
    echo %GREEN%%BOLD%[INSTALLED TO DEVICE]%RESET% GTA updated on hardware device.
    echo [INFO] Launching MainActivity...
    "%ADB_PATH%" shell am start -n com.joel.gta/.MainActivity >nul 2>&1
) else (
    echo %RED%[INSTALL ERROR]%RESET% Failed to install APK to device.
)
goto step2_git

:no_device_found
echo %YELLOW%[DEVICE SKIPPED]%RESET% Walang nakitang aktibong device sa adb.
echo [INFO] Skipping device installation, tutuloy tayo sa Git sync...
goto step2_git

:skip_device_install
echo %YELLOW%[ADB NOT FOUND]%RESET% Android adb tool not found. Skipping device install...

:step2_git
echo.
echo ---------------------------------------------------------------
:: -------------------------------------------------------------
:: STEP 2: AUTO GIT COMMIT & PUSH
:: -------------------------------------------------------------
echo %BOLD%[STEP 2/2] GIT COMMIT ^& REPO SYNC%RESET%

set "GIT_BRANCH=main"
for /f %%b in ('git branch --show-current 2^>nul') do set "GIT_BRANCH=%%b"

set "DIRTY=0"
git status --porcelain | findstr . >nul 2>&1
if %ERRORLEVEL% EQU 0 set "DIRTY=1"

if "%DIRTY%"=="0" goto git_clean

echo [INFO] Changes detected in working tree. Staging all files...
git add -A

if defined CUSTOM_MSG goto do_commit
if "%NO_PAUSE%"=="1" goto default_commit_msg

echo.
set /p "USER_INPUT=Enter commit release note (Press Enter for auto-timestamp): "
if not "%USER_INPUT%"=="" (
    set "CUSTOM_MSG=%USER_INPUT%"
    goto do_commit
)

:default_commit_msg
set "CUSTOM_MSG=Auto-deploy and update: %DATE% %TIME%"

:do_commit
echo [INFO] Committing changes...
git commit -m "%CUSTOM_MSG%"

echo [INFO] Pushing to remote branch %CYAN%origin/%GIT_BRANCH%%RESET%...
git push origin %GIT_BRANCH%
if %ERRORLEVEL% EQU 0 (
    echo %GREEN%%BOLD%[SYNCED TO GITHUB]%RESET% Matagumpay na nai-push sa GitHub: %GIT_BRANCH%
) else (
    echo %RED%%BOLD%[GIT PUSH ERROR]%RESET% Hindi nai-push sa remote repository. Pakisuri ang network o credentials.
)
goto pipeline_finish

:git_clean
echo [INFO] Working directory is clean. Checking for unpushed commits...
git push origin %GIT_BRANCH%
if %ERRORLEVEL% EQU 0 (
    echo %CYAN%%BOLD%[GIT UP-TO-DATE]%RESET% Remote branch %GIT_BRANCH% is synchronized.
)

:pipeline_finish
echo.
echo %GREEN%%BOLD%===============================================================%RESET%
echo %GREEN%%BOLD% [PIPELINE SUCCESS] Local build, hardware deploy ^& sync done. %RESET%
echo %GREEN%%BOLD%===============================================================%RESET%
echo.

if "%NO_PAUSE%"=="0" pause
