@echo off
REM Quick Start Script for gRPC Demo

echo ========================================
echo gRPC Demo Project - Quick Start
echo ========================================
echo.

REM Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Maven first: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

echo Step 1: Cleaning and compiling project...
echo.
call mvn clean compile

if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Compilation failed!
    echo Please check the error messages above.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Build successful!
echo ========================================
echo.
echo Next steps:
echo 1. Open a terminal and run: mvn exec:java -Dexec.mainClass="com.example.grpc.server.GrpcServer"
echo 2. Open another terminal and run: mvn exec:java -Dexec.mainClass="com.example.grpc.client.GrpcClient"
echo.
echo Or use the provided start-server.bat and start-client.bat scripts
echo ========================================
pause
