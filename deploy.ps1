# TradeVision Android - Complete Build & Push Script
# Run this in PowerShell as Administrator

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TradeVision Android - Build & Push" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ─── CONFIG ───
$repoUrl = "https://github.com/mhmkhavari3-collab/tradevision-android.git"
$downloadUrl = "https://tmpfiles.org/dl/wlwsi9elaCp0/tradevision-v14.tar.gz"
$projectDir = "C:\tradevision-android"
$tempDir = "$env:TEMP\tradevision-download"
$tarFile = "$tempDir\tradevision-final.tar.gz"

# ─── CLEANUP ───
Write-Host "`n[1/7] Cleaning old project..." -ForegroundColor Yellow
if (Test-Path $projectDir) {
    Remove-Item -Recurse -Force $projectDir -ErrorAction SilentlyContinue
}
if (Test-Path $tempDir) {
    Remove-Item -Recurse -Force $tempDir -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Force -Path $projectDir | Out-Null
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

# ─── DOWNLOAD ───
Write-Host "[2/7] Downloading project..." -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile $tarFile -UseBasicParsing
    Write-Host "  ✓ Downloaded" -ForegroundColor Green
}
catch {
    Write-Host "  ✗ Download failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ─── EXTRACT ───
Write-Host "[3/7] Extracting..." -ForegroundColor Yellow
try {
    # Use tar.exe (built into Windows 10+)
    tar -xzf $tarFile -C $tempDir
    Write-Host "  ✓ Extracted" -ForegroundColor Green
}
catch {
    Write-Host "  ✗ Extract failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ─── COPY FILES ───
Write-Host "[4/7] Copying files..." -ForegroundColor Yellow
$sourceDir = "$tempDir\tradevision_project"
if (Test-Path $sourceDir) {
    Copy-Item -Path "$sourceDir\*" -Destination $projectDir -Recurse -Force
    Write-Host "  ✓ Files copied to $projectDir" -ForegroundColor Green
}
else {
    Write-Host "  ✗ Source directory not found: $sourceDir" -ForegroundColor Red
    exit 1
}

# ─── GIT INIT & CONFIG ───
Write-Host "[5/7] Initializing Git..." -ForegroundColor Yellow
Set-Location $projectDir
git init
git config user.email "mhmkhavari3@users.noreply.github.com"
git config user.name "mhmkhavari3-collab"

# ─── COMMIT ───
Write-Host "[6/7] Committing..." -ForegroundColor Yellow
git add .
git commit -m "TradeVision v3.0 - Complete Production Build"
git branch -M main

# ─── REMOTE & PUSH ───
Write-Host "[7/7] Pushing to GitHub..." -ForegroundColor Yellow
git remote remove origin 2>$null
git remote add origin $repoUrl

# Force push main
git push -u origin main --force

# Force push tag
git tag -d v3.0.0 2>$null
git push origin :refs/tags/v3.0.0 2>$null
git tag v3.0.0
git push origin v3.0.0

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  ✓ DONE! Build triggered on GitHub" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "`nCheck build status:" -ForegroundColor Yellow
Write-Host "  https://github.com/mhmkhavari3-collab/tradevision-android/actions" -ForegroundColor Gray
Write-Host "`nDownload APK (after build passes):" -ForegroundColor Yellow
Write-Host "  https://github.com/mhmkhavari3-collab/tradevision-android/releases" -ForegroundColor Gray
Write-Host "`nArtifacts:" -ForegroundColor Yellow
Write-Host "  https://github.com/mhmkhavari3-collab/tradevision-android/actions/runs/[run-id]" -ForegroundColor Gray