@echo off
REM ==============================================================================
REM Restaurant Kitchen Display System (KDS) & Order Pipeline
REM Windows Batch Startup Script
REM ==============================================================================

setlocal enabledelayedexpansion
title Restaurant Kitchen Display System (KDS)

cd /d "%~dp0"

set JAR_PATH=target\restaurant-kds-1.0.0.jar
set PORT=8080
set PROFILE=default
set FORCE_BUILD=false
set RUN_TESTS=false
set SKIP_TESTS=

REM Parse Arguments
:parse_args
if "%~1"=="" goto after_args
if /i "%~1"=="--build" (set FORCE_BUILD=true& shift & goto parse_args)
if /i "%~1"=="-b" (set FORCE_BUILD=true& shift & goto parse_args)
if /i "%~1"=="--test" (set RUN_TESTS=true& shift & goto parse_args)
if /i "%~1"=="-t" (set RUN_TESTS=true& shift & goto parse_args)
if /i "%~1"=="--mysql" (set PROFILE=mysql& shift & goto parse_args)
if /i "%~1"=="--port" (set PORT=%~2& shift & shift & goto parse_args)
if /i "%~1"=="--skip-tests" (set SKIP_TESTS=-DskipTests& shift & goto parse_args)
if /i "%~1"=="--help" goto show_help
if /i "%~1"=="-h" goto show_help
echo Unknown argument: %~1
goto show_help

:show_help
echo ===================================================================
echo        RESTAURANT KITCHEN DISPLAY SYSTEM (KDS) ^& PIPELINE
echo ===================================================================
echo Usage: run.bat [OPTIONS]
echo.
echo Options:
echo   --build, -b         Force a clean rebuild with Maven before running
echo   --test, -t          Run unit ^& integration test suite (20 tests)
echo   --mysql             Run with native MySQL 8 profile
echo   --port ^<PORT^>       Specify custom server port (default: 8080)
echo   --skip-tests        Package without running tests
echo   --help, -h          Show this help screen
echo ===================================================================
exit /b 0

:after_args

REM Detect Java
set JAVA_CMD=java
where java >nul 2>nul
if %errorlevel% neq 0 (
    if defined JAVA_HOME (
        set JAVA_CMD="%JAVA_HOME%\bin\java.exe"
    ) else if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\jbr\bin\java.exe" (
        set JAVA_CMD="C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\jbr\bin\java.exe"
    ) else (
        echo [ERROR] Java runtime not found. Please install Java 21+ or set JAVA_HOME.
        pause
        exit /b 1
    )
)

REM Detect Maven
set MVN_CMD=mvn
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    if defined M2_HOME (
        set MVN_CMD="%M2_HOME%\bin\mvn.cmd"
    ) else if defined MAVEN_HOME (
        set MVN_CMD="%MAVEN_HOME%\bin\mvn.cmd"
    ) else if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    )
)

REM Test execution
if "%RUN_TESTS%"=="true" (
    echo [INFO] Running test suite...
    call %MVN_CMD% test
    exit /b %errorlevel%
)

REM Check if rebuild needed or JAR missing
if "%FORCE_BUILD%"=="true" goto do_build
if not exist "%JAR_PATH%" goto do_build
goto run_app

:do_build
echo [INFO] Packaging application into %JAR_PATH%...
call %MVN_CMD% clean package %SKIP_TESTS%
if not exist "%JAR_PATH%" (
    echo [ERROR] Build failed. %JAR_PATH% not found.
    pause
    exit /b 1
)

:run_app
cls
echo ===================================================================
echo     RESTAURANT KITCHEN DISPLAY SYSTEM ^& ORDER PIPELINE
echo ===================================================================
echo Profile:      %PROFILE%
echo Port:         %PORT%
echo Database:     MySQL-compatible persistent storage
echo Live Updates: Server-Sent Events (SSE) + Web Audio Chime
echo -------------------------------------------------------------------
echo Application URLs:
echo   - Login:            http://localhost:%PORT%/login
echo   - Kitchen Display:  http://localhost:%PORT%/kds
echo   - Dashboard:        http://localhost:%PORT%/dashboard
echo   - Order Entry:      http://localhost:%PORT%/orders/new
echo   - Billing ^& POS:    http://localhost:%PORT%/billing
echo   - Reports ^& CSV:    http://localhost:%PORT%/reports
echo -------------------------------------------------------------------
echo Seeded Credentials:
echo   - Admin:     admin / admin123
echo   - Kitchen:   kitchen / kitchen123
echo   - Cashier:   cashier / cashier123
echo   - Manager:   manager / manager123
echo ===================================================================
echo Starting application... Press Ctrl+C to stop.
echo.

set JAVA_OPTS=-Dserver.port=%PORT%
if "%PROFILE%"=="mysql" (
    set JAVA_OPTS=%JAVA_OPTS% -Dspring.profiles.active=mysql
)

%JAVA_CMD% %JAVA_OPTS% -jar "%JAR_PATH%"
