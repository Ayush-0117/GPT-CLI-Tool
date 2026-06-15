@echo off
REM ─────────────────────────────────────────────────────
REM GPT CLI Tool — Launcher Script (Windows)
REM ─────────────────────────────────────────────────────
REM Place this file (or the whole project folder) somewhere
REM and add it to your system PATH.
REM Then you can run: gpt
REM ─────────────────────────────────────────────────────

SET DIR=%~dp0
SET JAR=%DIR%target\gpt-cli-tool-1.0-SNAPSHOT.jar

REM ── Check for Java ──
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java is not installed or not in PATH.
    echo Please install Java 21+ from https://adoptium.net
    exit /b 1
)

REM ── Check for JAR, auto-build if missing ──
if not exist "%JAR%" (
    echo JAR not found. Building project...
    where mvn >nul 2>nul
    if %ERRORLEVEL% NEQ 0 (
        echo Error: Maven is not installed. Please build manually:
        echo   cd %DIR% ^&^& mvn clean package -DskipTests
        exit /b 1
    )
    pushd "%DIR%"
    call mvn clean package -DskipTests -q
    popd
)

REM ── Launch ──
java -jar "%JAR%" %*
