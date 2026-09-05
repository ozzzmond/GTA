@echo off
set EMULATOR_PATH=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe

if not exist "%EMULATOR_PATH%" (
    echo [ERROR] emulator.exe not found.
    pause
    exit /b 1
)

echo Starting Pixel_8 emulator...
start "" "%EMULATOR_PATH%" -avd Pixel_8
