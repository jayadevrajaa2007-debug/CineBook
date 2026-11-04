@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo Starting CineBook Application
echo ===================================================

cd /d "%~dp0"

if not exist ".env" (
    echo [ERROR] .env file not found!
    echo Please copy .env.example to .env and configure your MySQL credentials:
    echo   copy .env.example .env
    pause
    exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
    set "line_key=%%A"
    set "line_val=%%B"
    if not "!line_key!"=="" (
        set "first_char=!line_key:~0,1!"
        if not "!first_char!"=="#" (
            set "!line_key!=!line_val!"
        )
    )
)

if "%MYSQL_PASSWORD%"=="" (
    echo [ERROR] MYSQL_PASSWORD is empty or not set in .env!
    echo Please edit .env and set your MySQL root password.
    pause
    exit /b 1
)

if "%MYSQL_HOST%"=="" set MYSQL_HOST=localhost
if "%MYSQL_PORT%"=="" set MYSQL_PORT=3306
if "%MYSQL_DB%"=="" set MYSQL_DB=cinebook
if "%MYSQL_USER%"=="" set MYSQL_USER=root

echo Database target: %MYSQL_USER%@%MYSQL_HOST%:%MYSQL_PORT%/%MYSQL_DB%

REM Check if MySQL is already running on port 3306
netstat -ano | findstr ":3306 " >nul 2>&1
if errorlevel 1 (
    echo [INFO] MySQL is not running on port 3306. Starting local MySQL server...
    if exist "C:\Users\Admin\mysql-server\bin\mysqld.exe" (
        start "MySQL Server" /B "C:\Users\Admin\mysql-server\bin\mysqld.exe" --console --basedir="C:\Users\Admin\mysql-server" --datadir="C:\Users\Admin\mysql-server\data" --port=3306 --bind-address=127.0.0.1
        timeout /t 3 /nobreak >nul
    ) else (
        echo [WARNING] Could not locate C:\Users\Admin\mysql-server\bin\mysqld.exe.
        echo Please ensure your MySQL Server service is started.
    )
) else (
    echo [OK] MySQL Server is running on port 3306.
)

REM Locate Maven or Java
set "MVN_CMD=mvn"
where mvn >nul 2>&1
if errorlevel 1 (
    if exist "C:\Users\Admin\.m2\wrapper\dists\apache-maven-3.9.6\apache-maven-3.9.6\bin\mvn.cmd" (
        set "MVN_CMD=C:\Users\Admin\.m2\wrapper\dists\apache-maven-3.9.6\apache-maven-3.9.6\bin\mvn.cmd"
    )
)

echo Launching CineBook on http://localhost:8080 ...
if exist "target\cinebook-1.0.0.jar" (
    echo Using pre-built package target\cinebook-1.0.0.jar ...
    java -jar target\cinebook-1.0.0.jar
) else (
    echo Building and running via Maven...
    call %MVN_CMD% spring-boot:run
)
