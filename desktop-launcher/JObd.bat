@echo off
setlocal

REM Get the directory of this script
set SCRIPT_DIR=%~dp0
REM Get project root (one directory up)
set PROJECT_ROOT=%SCRIPT_DIR%..

REM Set JAR file path
set JARFILE=%PROJECT_ROOT%\library\build\libs\library.jar

REM Check if JAR exists
if not exist "%JARFILE%" (
    echo ERROR: Library JAR not found at %JARFILE%
    echo Please build it first with: gradlew.bat :library:jar
    exit /b 1
)

REM Configure serial port if provided
if not "%1"=="" (
    echo Configuring serial port: %1
    mode %1 38400,n,8,1 >nul 2>&1
    if errorlevel 1 (
        echo WARNING: Could not configure serial port %1
        echo Make sure the port exists and you have permission to access it
    )
) else (
    echo Starting in DEMO mode - no serial port specified
    echo Usage: %0 [serial_port]
    echo Example: %0 COM3
)

REM Start the desktop OBD application
echo Starting OBD Desktop Application...
echo Using JAR: %JARFILE%

REM Run with logging configuration
java -Djava.util.logging.config.file="%SCRIPT_DIR%logging.properties" ^
     -cp "%JARFILE%" ^
     com.obddroid.ecu.gui.application.ObdTestFrame %*

endlocal