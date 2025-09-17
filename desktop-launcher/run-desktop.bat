@echo off
REM Desktop launcher script for OBDroid (Windows)

echo Starting OBDroid Desktop Application...

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Java is not installed. Please install Java 8 or higher.
    pause
    exit /b 1
)

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0

REM Find the desktop JAR file
set JAR_FILE=%SCRIPT_DIR%..\library\build\libs\library-desktop-all.jar

if not exist "%JAR_FILE%" (
    echo Error: Desktop JAR not found at %JAR_FILE%
    echo Please build the desktop JAR first using: gradlew.bat desktopJar
    pause
    exit /b 1
)

REM Run the desktop application
REM Pass serial port as first argument if provided
if "%~1"=="" (
    echo Usage: %0 ^<serial-port^>
    echo Example: %0 COM3
    echo Starting in simulation mode...
    java -jar "%JAR_FILE%"
) else (
    echo Using serial port: %1
    java -jar "%JAR_FILE%" %*
)

pause