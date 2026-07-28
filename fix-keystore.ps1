# TradeVision - Fix Keystore Secret (Run in PowerShell)
# This script generates a NEW keystore and properly encodes it for GitHub

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Fix Keystore Secret for GitHub" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ─── CONFIG ───
$alias = "tradevision"
$password = "tradevision123"
$dname = "CN=TradeVision, OU=Dev, O=TradeVision, L=Tehran, ST=Tehran, C=IR"
$repo = "mhmkhavari3-collab/tradevision-android"
$projectDir = "C:\tradevision-android"
$keystorePath = "$projectDir\app\keystore.jks"
$base64File = "$projectDir\keystore-base64.txt"

# ─── STEP 1: FIND KEYTOOL ───
Write-Host "`n[1/5] Finding keytool..." -ForegroundColor Yellow
$keytoolPath = $null
$jdkPaths = @(
    "C:\Program Files\Eclipse Adoptium\jdk-17*\bin\keytool.exe",
    "C:\Program Files\Java\jdk-17*\bin\keytool.exe",
    "C:\Program Files\Java\jdk-11*\bin\keytool.exe",
    "C:\Program Files (x86)\Java\jdk*\bin\keytool.exe"
)
foreach ($pattern in $jdkPaths) {
    $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($found) { $keytoolPath = $found.FullName; break }
}
if (-not $keytoolPath) { $keytoolPath = (Get-Command keytool -ErrorAction SilentlyContinue).Source }
if (-not $keytoolPath) {
    Write-Host "  ✗ keytool not found! Install JDK 17 from https://adoptium.net/" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Found: $keytoolPath" -ForegroundColor Green

# ─── STEP 2: CREATE KEYSTORE ───
Write-Host "`n[2/5] Creating keystore..." -ForegroundColor Yellow
$keystoreDir = Split-Path -Parent $keystorePath
if (-not (Test-Path $keystoreDir)) { New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null }

& $keytoolPath -genkeypair -v `
    -keystore $keystorePath `
    -alias $alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -storepass $password `
    -keypass $password `
    -dname $dname

if ($LASTEXITCODE -ne 0) { Write-Host "  ✗ Failed!" -ForegroundColor Red; exit 1 }
Write-Host "  ✓ Created: $keystorePath" -ForegroundColor Green

# ─── STEP 3: VERIFY ───
Write-Host "`n[3/5] Verifying..." -ForegroundColor Yellow
& $keytoolPath -list -keystore $keystorePath -storepass $password
Write-Host "  ✓ Verified" -ForegroundColor Green

# ─── STEP 4: GENERATE CORRECT BASE64 ───
Write-Host "`n[4/5] Generating PROPER base64..." -ForegroundColor Yellow
$bytes = [IO.File]::ReadAllBytes($keystorePath)
$base64 = [Convert]::ToBase64String($bytes)

# CRITICAL: Write WITHOUT newlines, just raw base64
[IO.File]::WriteAllText($base64File, $base64, [System.Text.Encoding]::UTF8)

Write-Host "  ✓ Base64 length: $($base64.Length) chars" -ForegroundColor Green
Write-Host "  ✓ Saved to: $base64File" -ForegroundColor Green
Write-Host "  ✓ First 50 chars: $($base64.Substring(0,50))..." -ForegroundColor Gray

# ─── STEP 5: UPLOAD TO GITHUB ───
Write-Host "`n[5/5] Uploading to GitHub Secrets..." -ForegroundColor Yellow

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    Write-Host "  ⚠️ gh CLI not found. Manual steps:" -ForegroundColor Yellow
    Write-Host "  1. Open: https://github.com/$repo/settings/secrets/actions" -ForegroundColor Gray
    Write-Host "  2. Delete OLD secrets: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD" -ForegroundColor Gray
    Write-Host "  3. Add NEW secrets:" -ForegroundColor Gray
    Write-Host "     KEYSTORE_BASE64 = (copy ENTIRE content of $base64File)" -ForegroundColor Gray
    Write-Host "     KEYSTORE_PASSWORD = $password" -ForegroundColor Gray
    Write-Host "     KEY_ALIAS = $alias" -ForegroundColor Gray
    Write-Host "     KEY_PASSWORD = $password" -ForegroundColor Gray
}
else {
    $auth = gh auth status 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  ⚠️ Not logged in. Run: gh auth login" -ForegroundColor Yellow
    }
    else {
        # Delete old secrets first
        gh secret delete KEYSTORE_BASE64 --repo $repo 2>$null
        gh secret delete KEYSTORE_PASSWORD --repo $repo 2>$null
        gh secret delete KEY_ALIAS --repo $repo 2>$null
        gh secret delete KEY_PASSWORD --repo $repo 2>$null

        # Upload new ones
        Write-Host "  Uploading KEYSTORE_BASE64..."
        $base64 | gh secret set KEYSTORE_BASE64 --repo $repo
        
        Write-Host "  Uploading KEYSTORE_PASSWORD..."
        $password | gh secret set KEYSTORE_PASSWORD --repo $repo
        
        Write-Host "  Uploading KEY_ALIAS..."
        $alias | gh secret set KEY_ALIAS --repo $repo
        
        Write-Host "  Uploading KEY_PASSWORD..."
        $password | gh secret set KEY_PASSWORD --repo $repo

        Write-Host "  ✓ ALL SECRETS UPDATED!" -ForegroundColor Green
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  DONE! Now run deploy.ps1" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan