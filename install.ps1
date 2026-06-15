# ─────────────────────────────────────────────────────
# GPT CLI Tool — Windows Installer (PowerShell)
# ─────────────────────────────────────────────────────
# Usage:  .\install.ps1
#
# What it does:
#   1. Checks for Java 21+ and Maven
#   2. Builds the shaded JAR
#   3. Adds the project directory to your user PATH
#      so you can type "gpt" from any terminal.
# ─────────────────────────────────────────────────────

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "   G P T   C L I   Installer" -ForegroundColor Cyan
Write-Host "   ─────────────────────────" -ForegroundColor DarkGray
Write-Host ""

$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── 1. Check Java ──
Write-Host "ℹ  Checking for Java..." -ForegroundColor Cyan
try {
    $null = Get-Command java -ErrorAction Stop
    $javaVersion = (java -version 2>&1 | Select-Object -First 1)
    Write-Host "✔  Java found: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "✖  Java is not installed or not in PATH." -ForegroundColor Red
    Write-Host "   Please install Java 21+: https://adoptium.net" -ForegroundColor Yellow
    exit 1
}

# ── 2. Check Maven ──
Write-Host "ℹ  Checking for Maven..." -ForegroundColor Cyan
try {
    $null = Get-Command mvn -ErrorAction Stop
    $mvnVersion = (mvn --version 2>&1 | Select-Object -First 1)
    Write-Host "✔  Maven found: $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "✖  Maven is not installed or not in PATH." -ForegroundColor Red
    Write-Host "   Install from: https://maven.apache.org/install.html" -ForegroundColor Yellow
    exit 1
}

# ── 3. Build ──
Write-Host "ℹ  Building GPT CLI Tool..." -ForegroundColor Cyan
Push-Location $ProjectDir
try {
    mvn clean package -DskipTests -q
    Write-Host "✔  Build complete." -ForegroundColor Green
} catch {
    Write-Host "✖  Build failed: $_" -ForegroundColor Red
    Pop-Location
    exit 1
}
Pop-Location

# ── 4. Add to PATH ──
$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$ProjectDir*") {
    Write-Host "ℹ  Adding project directory to your user PATH..." -ForegroundColor Cyan
    $newPath = "$userPath;$ProjectDir"
    [System.Environment]::SetEnvironmentVariable("Path", $newPath, "User")
    # Also update the current session
    $env:Path = "$env:Path;$ProjectDir"
    Write-Host "✔  Added $ProjectDir to user PATH." -ForegroundColor Green
    Write-Host "   NOTE: Restart your terminal for the change to take full effect." -ForegroundColor Yellow
} else {
    Write-Host "✔  Project directory is already in PATH." -ForegroundColor Green
}

Write-Host ""
Write-Host "✔  Installation complete!" -ForegroundColor Green
Write-Host ""
Write-Host "   Usage:  gpt" -ForegroundColor White
Write-Host "   Config: Copy .env.example to .env and add your API keys." -ForegroundColor DarkGray
Write-Host ""
